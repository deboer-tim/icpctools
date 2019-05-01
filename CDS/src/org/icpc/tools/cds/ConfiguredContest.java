package org.icpc.tools.cds;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.Session;

import org.icpc.tools.cds.util.PlaybackContest;
import org.icpc.tools.cds.util.Role;
import org.icpc.tools.cds.video.VideoAggregator;
import org.icpc.tools.cds.video.VideoAggregator.ConnectionMode;
import org.icpc.tools.cds.video.VideoMapper;
import org.icpc.tools.contest.Trace;
import org.icpc.tools.contest.model.ContestUtil;
import org.icpc.tools.contest.model.IAward;
import org.icpc.tools.contest.model.IClarification;
import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.IContestObject;
import org.icpc.tools.contest.model.IGroup;
import org.icpc.tools.contest.model.IJudgement;
import org.icpc.tools.contest.model.IProblem;
import org.icpc.tools.contest.model.IRun;
import org.icpc.tools.contest.model.ISubmission;
import org.icpc.tools.contest.model.ITeam;
import org.icpc.tools.contest.model.ITeamMember;
import org.icpc.tools.contest.model.feed.CCSContestSource;
import org.icpc.tools.contest.model.feed.ContestSource;
import org.icpc.tools.contest.model.feed.ContestSource.ConnectionState;
import org.icpc.tools.contest.model.feed.ContestSource.ContestSourceListener;
import org.icpc.tools.contest.model.feed.DiskContestSource;
import org.icpc.tools.contest.model.feed.RESTContestSource;
import org.icpc.tools.contest.model.feed.Timestamp;
import org.icpc.tools.contest.model.internal.Contest;
import org.icpc.tools.contest.model.internal.Info;
import org.icpc.tools.contest.model.internal.State;
import org.w3c.dom.Element;

public class ConfiguredContest {
	private static final int NUM_TEAMS = 0;

	public enum Mode {
		ARCHIVE, PLAYBACK, LIVE
	}

	private String id;
	private String location;
	private boolean recordReactions;
	private boolean hidden;
	private CCS ccs;
	private List<Video> videos = new ArrayList<>(3);
	private Test test;
	private Boolean isTesting;

	private DiskContestSource contestSource;

	private Contest contest;
	private Contest trustedContest;
	private Contest publicContest;
	private Contest balloonContest;
	private Contest[] teamContests;

	private Map<Object, String> clients = new HashMap<>();
	private long[] metrics = new long[10]; // REST, feed, ws, web, download, scoreboard, XML,
														// desktop, webcam, total

	public static class Video {
		private Element video;

		protected Video(Element e) {
			video = e;
		}

		public String getId() {
			return CDSConfig.getString(video, "id");
		}

		public String getWebcam() {
			return CDSConfig.getString(video, "webcam");
		}

		public String getWebcamMode() {
			return CDSConfig.getString(video, "webcamMode");
		}

		public String getDesktop() {
			return CDSConfig.getString(video, "desktop");
		}

		public String getDesktopMode() {
			return CDSConfig.getString(video, "desktopMode");
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("Video [");
			if (getId() != null)
				sb.append(getId() + "/");
			sb.append(getWebcam());
			if (getWebcamMode() != null)
				sb.append(":" + getWebcamMode());
			sb.append("/");
			sb.append(getDesktop());
			if (getDesktopMode() != null)
				sb.append(":" + getDesktopMode());
			sb.append("]");
			return sb.toString();
		}
	}

	public static class Test {
		private int countdown = -1;
		private long startTime = -1;
		private double multiplier = 1.0;

		protected Test(Element e) {
			Integer in = CDSConfig.getInteger(e, "countdown");
			if (in != null)
				countdown = in.intValue();

			Double d = CDSConfig.getDouble(e, "multiplier");
			if (d != null)
				multiplier = d.doubleValue();

			String st = CDSConfig.getString(e, "startTime");
			if (st != null)
				try {
					startTime = Timestamp.parse(st);
				} catch (Exception ex) {
					Trace.trace(Trace.USER, "Could not parse start time");
				}
		}

		public int getCountdown() {
			return countdown;
		}

		public long getStartTime() {
			return startTime;
		}

		public double getMultiplier() {
			return multiplier;
		}

		@Override
		public String toString() {
			if (startTime > 0)
				return "Playback at " + ContestUtil.formatStartTime(startTime) + " at " + getMultiplier() + "x speed";
			return "Playback in " + getCountdown() + "s at " + getMultiplier() + "x speed";
		}
	}

	public static class CCS {
		private String url;
		private String user;
		private String password;
		private String eventFeed;
		private String startTime;
		private String submissionFiles;

		protected CCS(Element e) {
			url = CDSConfig.getString(e, "url");
			user = CDSConfig.getString(e, "user");
			password = CDSConfig.getString(e, "password");
			eventFeed = CDSConfig.getString(e, "eventFeed");
			startTime = CDSConfig.getString(e, "startTime");
			submissionFiles = CDSConfig.getString(e, "submissionFiles");
		}

		public String getURL() {
			return url;
		}

		public String getUser() {
			return user;
		}

		public String getPassword() {
			return password;
		}

		public String getEventFeed() {
			return eventFeed;
		}

		public String getStartTime() {
			return startTime;
		}

		public String getSubmissionFiles() {
			return submissionFiles;
		}

		@Override
		public String toString() {
			if (getURL() != null)
				return getUser() + "@" + getURL();

			StringBuilder sb = new StringBuilder();
			sb.append(getUser() + "@" + getEventFeed());
			if (startTime != null)
				sb.append(", start " + startTime);
			if (submissionFiles != null)
				sb.append(", submissionFiles " + submissionFiles);
			return sb.toString();
		}
	}

	protected ConfiguredContest(Element e) {
		id = CDSConfig.getString(e, "id");
		location = CDSConfig.getString(e, "location");

		recordReactions = CDSConfig.getBoolean(e, "recordReactions");
		hidden = CDSConfig.getBoolean(e, "hidden");

		Element ee = CDSConfig.getChild(e, "ccs");
		if (ee != null) {
			ccs = new CCS(ee);
			// if no id, default to last segment of url
			if (id == null && ccs.getURL() != null) {
				String url = ccs.getURL();
				int ind = url.lastIndexOf("/");
				if (ind >= 0)
					id = url.substring(ind + 1);
			}
		}
		// if no id, default to last folder in location
		if (id == null && location != null) {
			int ind = Math.max(location.lastIndexOf("/"), location.lastIndexOf("\\"));
			if (ind >= 0)
				id = location.substring(ind + 1);
		}

		Element[] vee = CDSConfig.getChildren(e, "video");
		if (vee != null) {
			for (Element ve : vee)
				videos.add(new Video(ve));
		}

		ee = CDSConfig.getChild(e, "test");
		if (ee != null)
			test = new Test(ee);
	}

	public void init() {
		if (videos == null)
			return;

		for (Video video : videos) {
			String teamId = video.getId();
			String urlPattern = video.getDesktop();
			ConnectionMode mode = VideoAggregator.getConnectionMode(video.getDesktopMode());

			if (urlPattern != null) {
				if (teamId != null)
					VideoMapper.DESKTOP.mapTeam(teamId, urlPattern, mode);
				else
					VideoMapper.DESKTOP.mapAllTeams(this, urlPattern, mode);
			}

			urlPattern = video.getWebcam();
			mode = VideoAggregator.getConnectionMode(video.getWebcamMode());

			if (urlPattern != null) {
				if (teamId != null)
					VideoMapper.WEBCAM.mapTeam(teamId, urlPattern, mode);
				else
					VideoMapper.WEBCAM.mapAllTeams(this, urlPattern, mode);
			}
		}
	}

	public String getLocation() {
		return location;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public boolean isRecordingReactions() {
		return recordReactions;
	}

	public boolean isHidden() {
		return hidden;
	}

	public CCS getCCS() {
		return ccs;
	}

	public String getVideo() {
		if (videos.isEmpty())
			return "Not configured";
		if (videos.size() == 1)
			return videos.get(0).toString();
		return videos.size() + " configured";
	}

	public boolean isWebcamEnabled(String teamId) {
		if (videos.isEmpty() || teamId == null || teamId.isEmpty())
			return false;

		for (Video v : videos) {
			if (v.getWebcam() == null || v.getWebcam().isEmpty())
				continue;
			if (v.getId() == null)
				return true;
			if (teamId.equals(v.getId()))
				return true;
		}
		return false;
	}

	public boolean isDesktopEnabled(String teamId) {
		if (videos.isEmpty() || teamId == null || teamId.isEmpty())
			return false;

		for (Video v : videos) {
			if (v.getDesktop() == null || v.getDesktop().isEmpty())
				continue;
			if (v.getId() == null)
				return true;
			if (teamId.equals(v.getId()))
				return true;
		}
		return false;
	}

	public Test getTest() {
		return test;
	}

	public boolean isTesting() {
		if (isTesting == null) {
			isTesting = (test != null);
			if (isTesting)
				System.out.println("----- Test mode enabled -----");
		}

		return isTesting;
	}

	public Contest getContest() {
		if (contest == null)
			loadContest();

		return contest;
	}

	public Contest getContestByRole(HttpServletRequest request) {
		if (contest == null)
			loadContest();

		if (hidden && !Role.isAdmin(request) && !Role.isBlue(request))
			return null;
		if (Role.isBalloon(request))
			return balloonContest;
		if (Role.isBlue(request))
			return contest;
		if (Role.isTrusted(request))
			return trustedContest;
		return publicContest;
	}

	public Contest getContestByRole(boolean isBlue, boolean isBalloon) {
		if (contest == null)
			loadContest();

		if (hidden && !isBlue)
			return null;
		if (isBalloon)
			return balloonContest;
		if (isBlue)
			return contest;
		return publicContest;
	}

	public DiskContestSource getContestSource() {
		if (contestSource != null)
			return contestSource;

		if (location == null)
			return null;

		File folder = new File(location);

		if (ccs == null)
			contestSource = new DiskContestSource(folder);
		else {
			try {
				if (ccs.getURL() != null)
					contestSource = new RESTContestSource(ccs.getURL(), ccs.getUser(), ccs.getPassword(), folder);
				else
					contestSource = new CCSContestSource(ccs.getEventFeed(), ccs.getUser(), ccs.getPassword(),
							ccs.getSubmissionFiles(), ccs.getStartTime(), folder);
			} catch (Exception e) {
				Trace.trace(Trace.ERROR, "Could not configure contest source", e);
				contestSource = new DiskContestSource(folder);
			}
		}

		contestSource.setContestId(id);
		return contestSource;
	}

	private synchronized void loadContest() {
		if (contest != null)
			return;

		try {
			ContestSource source = getContestSource();
			ConnectionState[] state = new ConnectionState[1];
			PlaybackContest pc = new PlaybackContest(this);
			pc.addModifier((cont, obj) -> {
				if (obj instanceof Info) {
					Info info2 = (Info) obj;
					info2.setId(id);
				}
			});
			if (isTesting())
				pc.setTestMode();

			source.addListener(new ContestSourceListener() {
				@Override
				public void stateChanged(ConnectionState state2) {
					state[0] = state2;
					if (ContestSource.ConnectionState.CONNECTED.equals(state2))
						pc.setConfigurationLoaded();
					if (ContestSource.ConnectionState.CONNECTING.equals(state2))
						Trace.trace(Trace.USER, "Reading contest: " + id);
					else if (ContestSource.ConnectionState.COMPLETE.equals(state2))
						Trace.trace(Trace.USER, "Done reading contest: " + id);
				}
			});

			contestSource.setInitialContest(pc);
			contest = contestSource.getContest();

			publicContest = new Contest();
			balloonContest = new Contest();
			trustedContest = new Contest();
			teamContests = new Contest[NUM_TEAMS];
			for (int i = 0; i < NUM_TEAMS; i++)
				teamContests[i] = new Contest();
			State currentState = new State();
			contest.addListenerFromStart((contest2, obj, d) -> {
				// don't show problems until contest starts
				if (obj instanceof IProblem && currentState.getStarted() == null)
					return;

				// filter awards from a role below blue
				if (obj instanceof IAward)
					return;

				// filter out hidden groups and dependencies
				IContestObject obj2 = filterHidden(obj);
				if (obj2 == null)
					return;

				// all - don't show any submissions or judgements from outside the contest
				// public - show judgments until the freeze, only public clars, no runs
				// balloon - show judgments until the freeze and then any after if the
				// team has less than 3, only public clars, no runs
				// trusted - show runs & judgments until the freeze, show all clars
				if (obj instanceof ISubmission) {
					ISubmission s = (ISubmission) obj;
					int time = s.getContestTime();
					if (time >= 0 && time < contest.getDuration()) {
						trustedContest.add(obj2);
						balloonContest.add(obj2);
						publicContest.add(obj2);
						for (int i = 0; i < NUM_TEAMS; i++)
							teamContests[i].add(obj2);
					}
				} else if (obj instanceof IJudgement) {
					IJudgement j = (IJudgement) obj;
					int freezeTime = contest.getDuration() - contest.getFreezeDuration();
					ISubmission s = contest.getSubmissionById(j.getSubmissionId());
					if (s == null || s.getContestTime() > freezeTime) {
						if (s != null && s.getContestTime() < freezeTime && contest.isSolved(s)
								&& getNumSolved(balloonContest, s.getTeamId()) < 3)
							balloonContest.add(obj);
						return;
					}
					trustedContest.add(obj2);
					publicContest.add(obj2);
					balloonContest.add(obj2);
					for (int i = 0; i < NUM_TEAMS; i++)
						teamContests[i].add(obj2);
				} else if (obj instanceof IRun) {
					IRun r = (IRun) obj;
					int freezeTime = contest.getDuration() - contest.getFreezeDuration();
					if (r.getContestTime() >= freezeTime)
						return;
					trustedContest.add(obj2);
				} else if (obj instanceof IClarification) {
					trustedContest.add(obj2);
					IClarification clar = (IClarification) obj;
					if (clar.getFromTeamId() == null && clar.getToTeamId() == null) {
						publicContest.add(obj2);
						balloonContest.add(obj2);
						for (int i = 0; i < NUM_TEAMS; i++)
							teamContests[i].add(obj2);
					}
				} else {
					trustedContest.add(obj2);
					balloonContest.add(obj2);
					publicContest.add(obj2);
					for (int i = 0; i < NUM_TEAMS; i++)
						teamContests[i].add(obj2);
				}

				if (obj instanceof State) {
					State state2 = (State) obj;
					if (currentState.getStarted() == null && state2.getStarted() != null) {
						// send problems at start
						IProblem[] probs = contest.getProblems();
						for (IProblem p : probs) {
							trustedContest.add(p);
							publicContest.add(p);
							balloonContest.add(p);
							for (int i = 0; i < NUM_TEAMS; i++)
								teamContests[i].add(obj2);
						}
					}
					if (currentState.isRunning() != state2.isRunning()) {
						if (state2.isRunning())
							Trace.trace(Trace.USER, "Contest started: " + id);
						else if (state2.isRunning())
							Trace.trace(Trace.USER, "Contest over: " + id);
						currentState.setStarted(state2.getStarted());
					}
					if (currentState.isFrozen() != state2.isFrozen()) {
						if (state2.isFrozen())
							Trace.trace(Trace.USER, "Contest frozen: " + id);
						else
							Trace.trace(Trace.USER, "Contest unfrozen: " + id);
						currentState.setFrozen(state2.getFrozen());
					}
					if (currentState.isFinal() != state2.isFinal()) {
						Trace.trace(Trace.USER, "Contest finalized: " + id);
						currentState.setFinalized(state2.getFinalized());
					}
					if (currentState.getEndOfUpdates() != state2.getEndOfUpdates()) {
						Trace.trace(Trace.USER, "Contest end of updates: " + id);
						currentState.setEndOfUpdates(state2.getEndOfUpdates());
					}
				}
				/*if (obj instanceof ISubmission) {
					ISubmission s = (ISubmission) obj;
					File f = new File(getLocation(), "submissions");
					f = new File(f, s.getId() + ".zip");
					if (f.exists())
						try {
							FileReference ref = contestSource.getMetadata("submissions/" + s.getId() + "/files", f);
							if (ref != null)
								((Submission) s).setFiles(new FileReferenceList(ref));
						} catch (Exception e) {
							e.printStackTrace();
						}
				}*/
			});

			// wait up to 2s to connect
			int count = 0;
			while ((state[0] == null || state[0].ordinal() < ConnectionState.CONNECTED.ordinal()) && count < 20) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
					// ignore
				}
				count++;
			}
		} catch (Exception e) {
			Trace.trace(Trace.ERROR, "Error reading event feed: " + e.getMessage());
		}
	}

	protected boolean isJudgementHidden(IJudgement j) {
		if (j == null)
			return false;

		ISubmission sub = contest.getSubmissionById(j.getSubmissionId());
		if (sub == null)
			return false;

		ITeam team = contest.getTeamById(sub.getTeamId());
		return contest.isTeamHidden(team);
	}

	protected IContestObject filterHidden(IContestObject obj) {
		if (obj instanceof IGroup) {
			IGroup group = (IGroup) obj;
			if (group.isHidden())
				return null;
		} else if (obj instanceof ITeam) {
			ITeam team = (ITeam) obj;
			if (contest.isTeamHidden(team))
				return null;
		} else if (obj instanceof ITeamMember) {
			ITeamMember member = (ITeamMember) obj;
			ITeam team = contest.getTeamById(member.getTeamId());
			if (contest.isTeamHidden(team))
				return null;
		} else if (obj instanceof ISubmission) {
			ISubmission sub = (ISubmission) obj;
			ITeam team = contest.getTeamById(sub.getTeamId());
			if (contest.isTeamHidden(team))
				return null;
		} else if (obj instanceof IRun) {
			IRun run = (IRun) obj;
			IJudgement j = contest.getJudgementById(run.getJudgementId());
			if (isJudgementHidden(j))
				return null;
		} else if (obj instanceof IJudgement) {
			IJudgement j = (IJudgement) obj;
			if (isJudgementHidden(j))
				return null;
		} else if (obj instanceof IClarification) {
			IClarification clar = (IClarification) obj;
			ITeam team = contest.getTeamById(clar.getFromTeamId());
			if (contest.isTeamHidden(team))
				return null;
			team = contest.getTeamById(clar.getToTeamId());
			if (contest.isTeamHidden(team))
				return null;
		}

		return obj;
	}

	private static int getNumSolved(IContest contest2, String teamId) {
		if (teamId == null)
			return 0;

		List<String> solved = new ArrayList<>();
		ISubmission[] submissions = contest2.getSubmissions();
		for (ISubmission s : submissions) {
			if (teamId.equals(s.getTeamId()) && contest2.isSolved(s)) {
				if (!solved.contains(s.getProblemId()))
					solved.add(s.getProblemId());
			}
		}

		return solved.size();
	}

	public void incrementRest() {
		metrics[0]++;
		incrementTotal();
	}

	public void incrementFeed() {
		metrics[1]++;
		incrementTotal();
	}

	public void incrementWS() {
		metrics[2]++;
		incrementTotal();
	}

	public void incrementWeb() {
		metrics[3]++;
		incrementTotal();
	}

	public void incrementDownload() {
		metrics[4]++;
		incrementTotal();
	}

	public void incrementScoreboard() {
		metrics[5]++;
		incrementTotal();
	}

	public void incrementXMLFeed() {
		metrics[6]++;
		incrementTotal();
	}

	public void incrementDesktop() {
		metrics[7]++;
		incrementTotal();
	}

	public void incrementWebcam() {
		metrics[8]++;
		incrementTotal();
	}

	private void incrementTotal() {
		metrics[9]++;
		if (metrics[9] > 500) {
			metrics[9] = 0;
			logMetrics();
		}
	}

	public long[] getMetrics() {
		return metrics;
	}

	public void logMetrics() {
		StringBuilder sb = new StringBuilder();
		sb.append("Metrics for contest " + getId() + " [");
		sb.append("REST:" + metrics[0] + ",");
		sb.append("Feed:" + metrics[1] + ",");
		sb.append("WS:" + metrics[2] + ",");
		sb.append("Web:" + metrics[3] + ",");
		sb.append("Dwnld:" + metrics[4] + ",");
		sb.append("Scr:" + metrics[5] + ",");
		sb.append("XML:" + metrics[6] + ",");
		sb.append("Desktop:" + metrics[7] + ",");
		sb.append("Webcam:" + metrics[8] + "]");
		Trace.trace(Trace.INFO, sb.toString());

		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileWriter("stats.txt", true));
			pw.println(sb.toString());
		} catch (Exception e) {
			// ignore
		} finally {
			try {
				pw.close();
			} catch (Exception ex) {
				// ignore
			}
		}
	}

	public static String getUser(HttpServletRequest request) {
		String role = "public";
		if (Role.isAdmin(request))
			role = "admin";
		else if (Role.isBlue(request))
			role = "blue";
		else if (Role.isTrusted(request))
			role = "trusted";
		else if (Role.isBalloon(request))
			role = "balloon";
		return request.getRemoteUser() + " / " + role;
	}

	public void add(Session session) {
		synchronized (clients) {
			try {
				String user = session.getUserPrincipal() + " @ " + session.getId();
				clients.put(session, user);
			} catch (Exception e) {
				// ignore
			}
		}
	}

	public void add(AsyncContext asyncCtx) {
		synchronized (clients) {
			try {
				HttpServletRequest request = (HttpServletRequest) asyncCtx.getRequest();
				String user = ConfiguredContest.getUser(request) + " @ " + request.getRemoteHost() + " / "
						+ request.getRemoteAddr();
				clients.put(asyncCtx, user);
			} catch (Exception e) {
				// ignore
			}
		}
	}

	public void remove(Object obj) {
		synchronized (clients) {
			clients.remove(obj);
		}
	}

	public List<String> getClients() {
		List<String> list = new ArrayList<>();
		List<Object> remove = new ArrayList<>();
		synchronized (clients) {
			for (Object obj : clients.keySet()) {
				try {
					list.add(clients.get(obj));
				} catch (Exception e) {
					e.printStackTrace();
					remove(obj);
				}
			}
			for (Object obj : remove) {
				remove(obj);
			}
		}
		return list;
	}

	public Mode getMode() {
		if (test != null)
			return Mode.PLAYBACK;
		else if (ccs != null)
			return Mode.LIVE;
		return Mode.ARCHIVE;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(id);
		sb.append(" on disk at " + location + " - ");
		if (ccs != null)
			sb.append("CCS configured (" + ccs + "), ");
		else
			sb.append("No CCS configured. ");
		if (test != null)
			sb.append(test + ". ");
		if (recordReactions)
			sb.append("Recording reaction videos");
		else
			sb.append("Not recording reactions");
		return sb.toString();
	}
}