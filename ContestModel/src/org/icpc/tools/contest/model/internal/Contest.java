package org.icpc.tools.contest.model.internal;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.icpc.tools.contest.Trace;
import org.icpc.tools.contest.model.ContestUtil;
import org.icpc.tools.contest.model.IAward;
import org.icpc.tools.contest.model.IClarification;
import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.IContestListener;
import org.icpc.tools.contest.model.IContestListener.Delta;
import org.icpc.tools.contest.model.IContestObject;
import org.icpc.tools.contest.model.IContestObject.ContestType;
import org.icpc.tools.contest.model.IContestObjectFilter;
import org.icpc.tools.contest.model.ICountdown;
import org.icpc.tools.contest.model.IGroup;
import org.icpc.tools.contest.model.IJudgement;
import org.icpc.tools.contest.model.IJudgementType;
import org.icpc.tools.contest.model.ILanguage;
import org.icpc.tools.contest.model.IOrganization;
import org.icpc.tools.contest.model.IPause;
import org.icpc.tools.contest.model.IProblem;
import org.icpc.tools.contest.model.IProblemSummary;
import org.icpc.tools.contest.model.IResult;
import org.icpc.tools.contest.model.IRun;
import org.icpc.tools.contest.model.IStanding;
import org.icpc.tools.contest.model.IState;
import org.icpc.tools.contest.model.ISubmission;
import org.icpc.tools.contest.model.ITeam;
import org.icpc.tools.contest.model.ITeamMember;
import org.icpc.tools.contest.model.Status;
import org.icpc.tools.contest.model.util.AwardUtil;

public class Contest implements IContest {
	private static final Collator collator = Collator.getInstance(Locale.US);

	protected ContestData data;

	private List<IContestListener> listeners = new ArrayList<>();
	private List<IContestModifier> modifiers = new ArrayList<>();

	private Info info = new Info();
	private IState state = new State();
	private IProblem[] problems;
	private ILanguage[] languages;
	private IJudgementType[] judgementTypes;
	private IGroup[] groups;
	private IOrganization[] organizations;
	private ITeam[] teams;
	private ITeamMember[] members;
	private ISubmission[] submissions;
	private IJudgement[] judgements;
	private IClarification[] clars;
	private IRun[] runs;
	private ICountdown countdown;
	private IAward[] awards;
	private IPause[] pauses;

	private Result[][] results;
	private ProblemSummary[] resultSummary;
	private Standing[] standings;
	private int[] order;
	private ITeam[] orderedTeams;
	private String[] fts;
	private Recent[] recentActivity;
	private IJudgementType[] submissionStatus;
	private int lastEventTime;
	private IContestObject lastTimedEvent;
	private int lastTimedEventIndex;

	public Contest() {
		this(true);
	}

	public Contest(boolean keepHistory) {
		data = new ContestData(keepHistory);
	}

	@Override
	public void addListener(IContestListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener(IContestListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	public void addModifier(IContestModifier listener) {
		synchronized (modifiers) {
			modifiers.add(listener);
		}
	}

	public void removeModifier(IContestModifier listener) {
		synchronized (modifiers) {
			modifiers.remove(listener);
		}
	}

	public void add(IContestObject obj) {
		if (obj == null)
			return;

		notifyModifiers(obj);

		Delta delta = null;
		synchronized (data) {
			delta = data.add(obj);
			if (delta == Delta.NOOP)
				return;

			clearCaches(obj, delta);
		}

		notifyListeners(obj, delta);

		if (obj instanceof ISubmission || obj instanceof IRun || obj instanceof IJudgement
				|| obj instanceof IClarification) {
			updateTime(ContestObject.getContestTime(obj));
			lastTimedEvent = obj;
			lastTimedEventIndex = data.size();
		}
	}

	private void updateTime(int time) {
		lastEventTime = Math.max(lastEventTime, time);
	}

	@Override
	public IContestObject getLastTimedObject() {
		return lastTimedEvent;
	}

	@Override
	public int getLastTimedObjectEventIndex() {
		return lastTimedEventIndex;
	}

	private void clearCaches(IContestObject e, Delta delta) {
		if (delta == Delta.NOOP)
			return;

		if (e == null) {
			Info[] infos = data.getByType(Info.class, ContestType.CONTEST);
			if (infos.length > 0)
				info = infos[infos.length - 1];
			else
				info = null;
			State[] states = data.getByType(State.class, ContestType.STATE);
			if (states.length > 0)
				state = states[states.length - 1];
			else
				state = null;
			problems = null;
			languages = null;
			groups = null;
			teams = null;
			organizations = null;
			judgementTypes = null;
			submissions = null;
			awards = null;
			pauses = null;
			countdown = null;
			members = null;
			runs = null;
			clars = null;

			order = null;
			orderedTeams = null;
			results = null;
			resultSummary = null;
			standings = null;
			submissionStatus = null;
			recentActivity = null;
			fts = null;
			recentActivity = null;
		} else if (e instanceof Info) {
			info = (Info) e;
		} else if (e instanceof State) {
			state = (State) e;
		} else if (e instanceof IProblem) {
			problems = null;

			order = null;
			orderedTeams = null;
			results = null;
			resultSummary = null;
			standings = null;
			submissionStatus = null;
			recentActivity = null;
			fts = null;
		} else if (e instanceof ILanguage) {
			languages = null;
		} else if (e instanceof IGroup) {
			groups = null;
		} else if (e instanceof IOrganization) {
			organizations = null;
		} else if (e instanceof IJudgementType) {
			judgementTypes = null;
		} else if (e instanceof ISubmission) {
			submissions = null;

			order = null;
			orderedTeams = null;
			results = null;
			resultSummary = null;
			standings = null;
			if (submissionStatus != null) {
				if (delta == Delta.ADD) {
					ISubmission s = (ISubmission) e;
					int sInd = getSubmissionIndex(s.getId());
					if (sInd >= submissionStatus.length)
						submissionStatus = null;
				} else
					submissionStatus = null;
			}
			recentActivity = null;
			fts = null;
		} else if (e instanceof IJudgement) {
			judgements = null;

			order = null;
			orderedTeams = null;
			results = null;
			resultSummary = null;
			standings = null;
			if (submissionStatus != null) {
				if (delta == Delta.ADD) {
					IJudgement sj = (IJudgement) e;
					IJudgementType jt = getJudgementTypeById(sj.getJudgementTypeId());
					if (jt != null) {
						int sInd2 = getSubmissionIndex(sj.getSubmissionId());
						if (sInd2 >= 0)
							submissionStatus[sInd2] = jt;
					}
				} else
					submissionStatus = null;
			}
			recentActivity = null;
			fts = null;
		} else if (e instanceof ITeam) {
			teams = null;

			order = null;
			orderedTeams = null;
			results = null;
			resultSummary = null;
			standings = null;
			submissionStatus = null;
			recentActivity = null;
			fts = null;
		} else if (e instanceof ITeamMember) {
			members = null;
		} else if (e instanceof ICountdown) {
			countdown = null;
		} else if (e instanceof IAward) {
			awards = null;
		} else if (e instanceof IPause) {
			pauses = null;
		} else if (e instanceof IRun) {
			runs = null;
		} else if (e instanceof IClarification) {
			clars = null;
		}
	}

	/**
	 * Removes an object from the contest. This method can be dangerous - listeners are not
	 * notified.
	 *
	 * @param obj
	 */
	public void remove(IContestObject obj) {
		if (obj == null)
			return;

		synchronized (data) {
			data.remove(obj);

			clearCaches(obj, Delta.DELETE);
		}
	}

	/**
	 * Removes an object from the contest. This method can be dangerous - listeners are not
	 * notified.
	 *
	 * @param obj
	 */
	public void removeSince(int num) {
		synchronized (data) {
			data.removeSince(num);

			clearCaches(null, Delta.DELETE);
		}
	}

	/**
	 * Removes an object from the contest. This method can be dangerous - listeners are not
	 * notified.
	 *
	 * @param obj
	 */
	public void removeFromHistory(IContestObject obj) {
		if (obj == null)
			return;

		synchronized (data) {
			data.removeFromHistory(obj);

			clearCaches(obj, Delta.DELETE);
		}
	}

	public Contest clone(boolean deep) {
		Contest c = new Contest();

		synchronized (data) {
			if (deep) {
				for (IContestObject o : data) {
					if (o instanceof ContestObject)
						c.add(((ContestObject) o).clone());
					else
						c.add(((Deletion) o).clone());
				}
			} else {
				for (IContestObject co : data)
					c.add(co);
			}
		}

		return c;
	}

	public Contest clone(IContestObjectFilter filter) {
		return clone(true, filter);
	}

	public Contest clone(boolean keepHistory, IContestObjectFilter filter) {
		Contest c = new Contest(keepHistory);

		synchronized (data) {
			for (IContestObject o : data) {
				IContestObject co = filter.filter(o);
				if (co != null) {
					c.add(co);
				}
			}
		}

		return c;
	}

	private void notifyListeners(IContestObject co, Delta delta) {
		IContestListener[] list = null;
		synchronized (listeners) {
			list = listeners.toArray(new IContestListener[listeners.size()]);
		}

		for (IContestListener listener : list) {
			try {
				listener.contestChanged(this, co, delta);
			} catch (Throwable t) {
				Trace.trace(Trace.ERROR, "Error notifying listener", t);
			}
		}
	}

	private void notifyModifiers(IContestObject co) {
		IContestModifier[] list = null;
		synchronized (modifiers) {
			list = modifiers.toArray(new IContestModifier[modifiers.size()]);
		}

		for (IContestModifier modifier : list) {
			try {
				modifier.notify(this, co);
			} catch (Throwable t) {
				Trace.trace(Trace.ERROR, "Error notifying modifier", t);
			}
		}
	}

	public IContestObject[] getObjects() {
		synchronized (data) {
			return data.toArray();
		}
	}

	public IContestObject[] getObjects(ContestType type) {
		synchronized (data) {
			return data.toArray(type);
		}
	}

	public IContestObject[] getObjectsAndListen(IContestListener listener) {
		synchronized (data) {
			IContestObject[] objs = data.toArray();
			addListener(listener);
			return objs;
		}
	}

	public void addListenerFromStart(IContestListener listener) {
		synchronized (data) {
			data.iterate(this, listener);
			addListener(listener);
		}
	}

	@Override
	public int getNumObjects() {
		return data.size();
	}

	public IContestObject getObjectByTypeAndId(ContestType type, String id) {
		return data.getById(id, type);
	}

	public Info getInfo() {
		return info;
	}

	/**
	 * Returns the name of the contest.
	 *
	 * @return the name
	 */
	@Override
	public String getId() {
		return info.getId();
	}

	/**
	 * Returns the name of the contest.
	 *
	 * @return the name
	 */
	@Override
	public String getName() {
		return info.getName();
	}

	/**
	 * Returns the formal name of the contest.
	 *
	 * @return the formal name
	 */
	@Override
	public String getFormalName() {
		return info.getFormalName();
	}

	@Override
	public Long getStartTime() {
		return info.getStartTime();
	}

	@Override
	public Integer getCountdownPauseTime() {
		return info.getCountdownPauseTime();
	}

	/**
	 * Returns the duration of the contest, in ms.
	 *
	 * @return the duration
	 */
	@Override
	public int getDuration() {
		return info.getDuration();
	}

	/**
	 * Returns the duration of the end of contest freeze, in ms.
	 *
	 * @return the freeze duration
	 */
	@Override
	public int getFreezeDuration() {
		return info.getFreezeDuration();
	}

	/**
	 * Returns the penalty time. -1 means there is no concept of penalty time; 0 indicates there is
	 * no penalty.
	 *
	 * @return the penalty time
	 */
	@Override
	public int getPenaltyTime() {
		return info.getPenaltyTime();
	}

	/**
	 * Returns the time multiplier, if the contest is in test/playback mode. Otherwise, returns 1.
	 *
	 * @return the time multiplier
	 */
	@Override
	public double getTimeMultiplier() {
		return info.getTimeMultiplier();
	}

	/**
	 * Returns the logo file.
	 *
	 * @return
	 */
	@Override
	public File getLogo(int width, int height, boolean force) {
		return info.getLogo(width, height, force);
	}

	/**
	 * Returns the logo image.
	 *
	 * @return
	 */
	@Override
	public BufferedImage getLogoImage(int width, int height, boolean forceLoad, boolean resizeToFit) {
		return info.getLogoImage(width, height, forceLoad, resizeToFit);
	}

	/**
	 * Returns the banner file.
	 *
	 * @return
	 */
	@Override
	public File getBanner(int width, int height, boolean force) {
		return info.getBanner(width, height, force);
	}

	/**
	 * Returns the contest banner image.
	 *
	 * @return
	 */
	@Override
	public BufferedImage getBannerImage(int width, int height, boolean forceLoad, boolean resizeToFit) {
		return info.getBannerImage(width, height, forceLoad, resizeToFit);
	}

	@Override
	public IGroup[] getGroups() {
		IGroup[] temp = groups;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (groups != null)
				return groups;

			groups = data.getByType(IGroup.class, ContestType.GROUP);
			return groups;
		}
	}

	@Override
	public IGroup getGroupById(String id) {
		return (IGroup) data.getById(id, ContestType.GROUP);
	}

	@Override
	public IGroup[] getGroupsByIds(String[] ids) {
		if (ids == null || ids.length == 0)
			return null;

		IGroup[] g = new IGroup[ids.length];
		for (int i = 0; i < ids.length; i++)
			g[i] = (IGroup) data.getById(ids[i], ContestType.GROUP);
		return g;
	}

	public IGroup getGroupByExternalId(String id) {
		if (id == null)
			return null;

		IGroup[] temp = getGroups();
		for (IGroup g : temp) {
			if (id.equals(g.getICPCId()))
				return g;
		}
		return null;
	}

	@Override
	public IState getState() {
		return state;
	}

	@Override
	public ICountdown getCountdown() {
		if (countdown != null)
			return countdown;

		synchronized (data) {
			if (countdown != null)
				return countdown;

			ICountdown[] counts = data.getByType(ICountdown.class, ContestType.COUNTDOWN);
			if (counts != null && counts.length > 0)
				countdown = counts[0];
			return countdown;
		}
	}

	@Override
	public Long getStartStatus() {
		Long startTime = info.getStartTime();
		if (startTime != null)
			return startTime;

		Integer pause = info.getCountdownPauseTime();
		if (pause == null)
			return null;

		return new Long(-pause);
	}

	public Info setStartStatus(Long start) {
		if (info == null)
			throw new IllegalArgumentException("Contest isn't initialized yet");

		Info info2 = (Info) info.clone();
		info2.setStartStatus(start);
		add(info2);
		return info2;
	}

	@Override
	public IPause[] getPauses() {
		IPause[] temp = pauses;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (pauses != null)
				return pauses;

			pauses = data.getByType(IPause.class, ContestType.PAUSE);
			return pauses;
		}
	}

	public boolean isDoneUpdating() {
		IState state2 = getState();
		if (state2 != null)
			return state2.isDoneUpdating();

		return false;
	}

	@Override
	public int getNumProblems() {
		return getProblems().length;
	}

	@Override
	public IProblem[] getProblems() {
		IProblem[] temp = problems;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (problems != null)
				return problems;

			temp = data.getByType(IProblem.class, ContestType.PROBLEM);

			// sort by ordinal (if it exists)
			Arrays.sort(temp, new Comparator<IProblem>() {
				@Override
				public int compare(IProblem a, IProblem b) {
					if (a.getOrdinal() < b.getOrdinal())
						return -1;
					if (a.getOrdinal() > b.getOrdinal())
						return 1;
					return 0;
				}
			});

			problems = temp;
			return temp;
		}
	}

	@Override
	public ITeam[] getTeams() {
		ITeam[] temp = teams;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (teams != null)
				return teams;

			teams = data.getByType(ITeam.class, ContestType.TEAM);
			return teams;
		}
	}

	@Override
	public ITeamMember[] getTeamMembers() {
		ITeamMember[] temp = members;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (members != null)
				return members;

			members = data.getByType(ITeamMember.class, ContestType.TEAM_MEMBER);
			return members;
		}
	}

	@Override
	public ITeamMember[] getTeamMembersByTeamId(String id) {
		ITeamMember[] temp = getTeamMembers();
		List<ITeamMember> list = new ArrayList<>();
		for (ITeamMember m : temp) {
			if (id.equals(m.getTeamId()))
				list.add(m);
		}
		if (list.isEmpty())
			return null;

		ITeamMember[] tempMembers = list.toArray(new ITeamMember[list.size()]);

		// default sort: by last name with coaches to bottom
		Arrays.sort(tempMembers, new Comparator<ITeamMember>() {
			@Override
			public int compare(ITeamMember o1, ITeamMember o2) {
				if (o1.getRole() != null && o2.getRole() != null && !o1.getRole().equals(o2.getRole()))
					return -o1.getRole().compareTo(o2.getRole());
				if (o1.getLastName() != null && o2.getLastName() != null)
					return collator.compare(o1.getLastName(), o2.getLastName());
				return 0;
			}
		});
		return tempMembers;
	}

	@Override
	public int getNumOrganizations() {
		return getOrganizations().length;
	}

	@Override
	public IOrganization getOrganizationById(String id) {
		return (IOrganization) data.getById(id, ContestType.ORGANIZATION);
	}

	@Override
	public IOrganization[] getOrganizations() {
		IOrganization[] temp = organizations;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (organizations != null)
				return organizations;

			organizations = data.getByType(IOrganization.class, ContestType.ORGANIZATION);
			return organizations;
		}
	}

	@Override
	public IAward[] getAwards() {
		IAward[] temp = awards;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (awards != null)
				return awards;

			awards = data.getByType(IAward.class, ContestType.AWARD);
			return awards;
		}
	}

	protected IResult getResult(ITeam team, IProblem problem) {
		int teamIndex = getTeamIndex(team.getId());
		int problemIndex = getProblemIndex(problem.getId());

		return getResult(teamIndex, problemIndex);
	}

	@Override
	public IResult getResult(ITeam team, int problemIndex) {
		int teamIndex = getTeamIndex(team.getId());
		return getResult(teamIndex, problemIndex);
	}

	@Override
	public IResult getResult(int teamIndex, int problemIndex) {
		Result[][] tempResults = results;
		if (tempResults != null)
			return tempResults[teamIndex][problemIndex];

		synchronized (data) {
			if (results != null)
				return results[teamIndex][problemIndex];

			calculateResultsAndStandings();

			return results[teamIndex][problemIndex];
		}
	}

	@Override
	public IProblemSummary getProblemSummary(int problemIndex) {
		ProblemSummary[] tempResults = resultSummary;
		if (tempResults != null)
			return tempResults[problemIndex];

		synchronized (data) {
			if (resultSummary != null)
				return resultSummary[problemIndex];

			calculateResultsAndStandings();

			return resultSummary[problemIndex];
		}
	}

	private void calculateResultsAndStandings() {
		synchronized (data) {
			if (results != null)
				return;

			// System.out.print("Results: ");
			// StringBuilder sb = new StringBuilder();
			// long scoreTime = System.currentTimeMillis();
			int numTeams = getNumTeams();
			int numProblems = getNumProblems();

			Result[][] tempResults = new Result[numTeams][numProblems];
			ProblemSummary[] tempResultSummary = new ProblemSummary[numProblems];
			Standing[] tempStandings = new Standing[numTeams];
			for (int i = 0; i < numTeams; i++) {
				tempStandings[i] = new Standing();
				for (int j = 0; j < numProblems; j++)
					tempResults[i][j] = new Result();
			}
			for (int j = 0; j < numProblems; j++)
				tempResultSummary[j] = new ProblemSummary();

			getSubmissions();

			// sort submissions by time (just in case they aren't)
			ISubmission[] sortedSubs = new ISubmission[submissions.length];
			System.arraycopy(submissions, 0, sortedSubs, 0, submissions.length);
			Arrays.sort(sortedSubs, (s1, s2) -> Integer.compare(s1.getContestTime(), s2.getContestTime()));

			// sb.append((System.currentTimeMillis() - scoreTime) + "ms ");

			String[] tempFTS = new String[numProblems];
			int duration = getDuration();
			for (ISubmission s : sortedSubs) {
				int time = s.getContestTime();
				if (time >= 0 && time < duration) {
					int teamIndex = getTeamIndex(s.getTeamId());
					int problemIndex = getProblemIndex(s.getProblemId());

					if (problemIndex >= 0 && teamIndex >= 0) {
						IJudgementType jt = getJudgementType(s);
						tempResults[teamIndex][problemIndex].addSubmission(this, s.getContestTime(), jt);

						// calculate FTS
						if (tempFTS[problemIndex] == null && !isTeamHidden(teams[teamIndex])) {
							if (isSolved(s)) {
								tempFTS[problemIndex] = s.getId();
								tempResults[teamIndex][problemIndex].setFTS();
							} else if (!isJudged(s))
								tempFTS[problemIndex] = "waiting for judgement";
						}
					} else
						Trace.trace(Trace.WARNING, "Invalid submission: " + s);
				}
			}
			// sb.append((System.currentTimeMillis() - scoreTime) + "ms ");

			for (int i = 0; i < numTeams; i++) {
				int numSolved = 0;
				int penalty = 0;
				int lastSolution = -1;
				for (int j = 0; j < numProblems; j++) {
					penalty += tempResults[i][j].getPenaltyTime();
					if (tempResults[i][j].getStatus() == Status.SOLVED) {
						int time = ContestUtil.getTimeInMin(tempResults[i][j].getContestTime());
						penalty += time;
						numSolved++;
						if (time > lastSolution)
							lastSolution = time;
					}
				}

				tempStandings[i].init(numSolved, penalty, lastSolution);
			}

			for (int i = 0; i < numTeams; i++) {
				for (int j = 0; j < numProblems; j++)
					if (tempResults[i][j] != null)
						tempResultSummary[j].addResult(tempResults[i][j]);
			}

			int[] tempOrder = new int[numTeams];
			int visibleTeams = 0;
			for (int i = 0; i < numTeams; i++) {
				if (!isTeamHidden(teams[i]))
					tempOrder[visibleTeams++] = i;
			}

			// remove hidden teams
			if (visibleTeams != numTeams) {
				int[] tempOrder2 = new int[visibleTeams];
				System.arraycopy(tempOrder, 0, tempOrder2, 0, visibleTeams);
				tempOrder = tempOrder2;
			}

			Ranking.rankIt(this, teams, tempStandings, tempOrder);

			results = tempResults;
			resultSummary = tempResultSummary;
			standings = tempStandings;
			order = tempOrder;
			fts = tempFTS;

			// sb.append((System.currentTimeMillis() - scoreTime) + "ms ");
			// System.out.println(sb.toString() + objects.size() + " objects ");
		}
	}

	@Override
	public boolean isTeamHidden(ITeam team) {
		if (team == null)
			return false;

		String[] groupIds = team.getGroupIds();
		if (groupIds == null || groupIds.length == 0)
			return false;

		int hidden = 0;
		for (String groupId : groupIds) {
			IGroup group = getGroupById(groupId);
			if (group != null && group.isHidden())
				hidden++;
		}
		if (hidden == 0)
			return false;
		if (hidden == groupIds.length)
			return true;

		// TODO some hidden and some non-hidden groups - hope this never happens!
		// we should probably clone the team and strip the hidden groups
		// System.err.println("Warning: team belongs to both hidden and non-hidden groups!");
		return true;
	}

	public int[] getOrder() {
		int[] tempOrder = order;
		if (tempOrder != null)
			return tempOrder;

		synchronized (data) {
			if (order != null)
				return order;

			calculateResultsAndStandings();

			return order;
		}
	}

	public Recent getRecent(ITeam team) {
		int teamIndex = getTeamIndex(team.getId());

		Recent[] tempRecent = recentActivity;
		if (tempRecent != null)
			return tempRecent[teamIndex];

		synchronized (data) {
			tempRecent = recentActivity;
			if (tempRecent != null)
				return tempRecent[teamIndex];

			int numTeams = getNumTeams();
			getSubmissions();
			tempRecent = new Recent[numTeams];
			for (int i = submissions.length - 1; i >= 0; i--) {
				ISubmission s = submissions[i];
				int teamInd = getTeamIndex(s.getId());
				if (teamInd >= 0) {
					if (tempRecent[teamInd] == null) {
						tempRecent[teamInd] = new Recent(s.getTime(), getStatus(s));
					}
				}
			}

			recentActivity = tempRecent;
			return recentActivity[teamIndex];
		}
	}

	/**
	 * Returns the rank order of the team.
	 */
	@Override
	public int getOrderOf(ITeam team) {
		if (team == null)
			return -1;

		int[] tempOrder = getOrder();
		int index = getTeamIndex(team.getId());
		for (int i = 0; i < tempOrder.length; i++) {
			if (tempOrder[i] == index) {
				return i;
			}
		}
		return -1;
	}

	private int getTeamIndex(String teamId) {
		return data.getIndexById(teamId, ContestType.TEAM);
	}

	@Override
	public int getProblemIndex(String problemId) {
		if (problemId == null)
			return -1;

		IProblem[] tempProbs = getProblems();
		for (int i = 0; i < tempProbs.length; i++) {
			if (problemId.equals(tempProbs[i].getId()))
				return i;
		}
		return -1;
	}

	public int getProblemIndexByLabel(String problemLabel) {
		if (problemLabel == null)
			return -1;

		IProblem[] tempProbs = getProblems();
		for (int i = 0; i < tempProbs.length; i++) {
			if (problemLabel.equals(tempProbs[i].getLabel()))
				return i;
		}
		return -1;
	}

	@Override
	public IStanding getStanding(ITeam team) {
		if (team == null)
			return null;

		return getStanding(getTeamIndex(team.getId()));
	}

	public IStanding getStanding(int teamIndex) {
		Standing[] tempStandings = standings;
		if (tempStandings != null)
			return tempStandings[teamIndex];

		synchronized (data) {
			if (standings != null)
				return standings[teamIndex];

			calculateResultsAndStandings();

			return standings[teamIndex];
		}
	}

	/**
	 * Finalize the results of the contest. Ranking will no longer be interim.
	 */
	public void finalizeResults() {
		synchronized (data) {
			order = null;
			orderedTeams = null;
			results = null;
			standings = null;
			calculateResultsAndStandings();
			Ranking.rankIt(this, teams, standings, Ranking.Scoring.UNOFFICIAL, order, AwardUtil.getLastBronze(this));
		}
	}

	/**
	 * Finalize the results of the contest and apply official scoring rules.
	 */
	public void officialResults() {
		synchronized (data) {
			order = null;
			orderedTeams = null;
			results = null;
			standings = null;
			calculateResultsAndStandings();
			Ranking.rankIt(this, teams, standings, Ranking.Scoring.OFFICIAL, order, AwardUtil.getLastBronze(this));
		}
	}

	/**
	 * Forces the state of the given submission to SOLVED or FAILED.
	 *
	 * @param submission
	 * @param b
	 */
	public void setSubmissionIsSolved(ISubmission submission, boolean b) {
		if (submission == null)
			return;

		String typeId = null;
		if (b)
			typeId = "AC";
		else
			typeId = "WA";

		IJudgement[] sjs = getJudgementsBySubmissionId(submission.getId());
		if (sjs != null) {
			for (IJudgement sj : sjs) {
				((Judgement) sj).setJudgementTypeId(typeId);
			}
			return;
		}

		add(new Judgement(submission.getId(), submission, typeId));
	}

	/**
	 * Updates the state of the submission to match the given submission.
	 *
	 * @param submission
	 * @param newSubmission
	 */
	public void updateSubmissionTo(ISubmission submission, IContest contest) {
		if (submission == null)
			return;

		IJudgement[] sjs = contest.getJudgementsBySubmissionId(submission.getId());
		if (sjs != null) {
			for (IJudgement sj : sjs)
				add(sj);
		}
	}

	@Override
	public int getContestTimeOfLastEvent() {
		return lastEventTime;
	}

	@Override
	public boolean isBeforeFreeze(ISubmission s) {
		if (s == null)
			return false;

		return s.getContestTime() < (info.getDuration() - info.getFreezeDuration());
	}

	@Override
	public int getNumTeams() {
		return getTeams().length;
	}

	@Override
	public ITeam getTeamById(String id) {
		return (ITeam) data.getById(id, ContestType.TEAM);
	}

	@Override
	public ITeam[] getOrderedTeams() {
		ITeam[] tempOrdTeams = orderedTeams;
		if (tempOrdTeams != null)
			return tempOrdTeams;

		synchronized (data) {
			if (orderedTeams != null)
				return orderedTeams;

			ITeam[] tempTeams = getTeams();
			int[] tempOrder = getOrder();

			int size = tempOrder.length;
			ITeam[] tempTeams2 = new ITeam[size];
			for (int i = 0; i < size; i++) {
				tempTeams2[i] = tempTeams[tempOrder[i]];
			}

			orderedTeams = tempTeams2;
			return orderedTeams;
		}
	}

	@Override
	public int getNumTeamMembers() {
		return getTeams().length;
	}

	@Override
	public IProblem getProblemById(String id) {
		return (IProblem) data.getById(id, ContestType.PROBLEM);
	}

	@Override
	public int getNumSubmissions() {
		return getSubmissions().length;
	}

	@Override
	public ISubmission[] getSubmissions() {
		ISubmission[] temp = submissions;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (submissions != null)
				return submissions;

			submissions = data.getByType(ISubmission.class, ContestType.SUBMISSION);
			return submissions;
		}
	}

	@Override
	public ISubmission getSubmissionById(String id) {
		return (ISubmission) data.getById(id, ContestType.SUBMISSION);
	}

	@Override
	public IJudgement[] getJudgements() {
		IJudgement[] temp = judgements;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (judgements != null)
				return judgements;

			judgements = data.getByType(IJudgement.class, ContestType.JUDGEMENT);
			return judgements;
		}
	}

	@Override
	public int getNumJudgements() {
		return getJudgements().length;
	}

	@Override
	public IJudgement getJudgementById(String id) {
		return (IJudgement) data.getById(id, ContestType.JUDGEMENT);
	}

	@Override
	public IJudgement[] getJudgementsBySubmissionId(String id) {
		if (id == null)
			return null;

		IJudgement[] temp = getJudgements();
		List<IJudgement> list = new ArrayList<>(2);
		for (IJudgement sj : temp) {
			if (id.equals(sj.getSubmissionId()))
				list.add(sj);
		}
		if (list.isEmpty())
			return null;

		return list.toArray(new IJudgement[list.size()]);
	}

	@Override
	public boolean isSolved(ISubmission submission) {
		if (submission == null)
			return false;

		return getStatus(submission) == Status.SOLVED;
	}

	public int getSubmissionIndex(String submissionId) {
		return data.getIndexById(submissionId, ContestType.SUBMISSION);
	}

	@Override
	public Status getStatus(ISubmission submission) {
		if (submission == null)
			return null;

		IJudgementType jt = getJudgementType(submission);
		if (jt == null)
			return Status.SUBMITTED;

		if (jt.isSolved())
			return Status.SOLVED;

		return Status.FAILED;
	}

	@Override
	public IJudgementType getJudgementType(ISubmission submission) {
		if (submission == null)
			return null;

		int sInd = getSubmissionIndex(submission.getId());
		IJudgementType[] tempStatus = submissionStatus;
		if (tempStatus != null && sInd >= 0)
			return tempStatus[sInd];

		synchronized (data) {
			sInd = getSubmissionIndex(submission.getId());
			if (sInd == -1)
				return null;

			if (submissionStatus != null)
				return submissionStatus[sInd];

			tempStatus = new IJudgementType[getNumSubmissions() + 100];

			getJudgements();
			for (IJudgement sj : judgements) {
				IJudgementType jt = getJudgementTypeById(sj.getJudgementTypeId());
				if (jt != null) {
					int sInd2 = getSubmissionIndex(sj.getSubmissionId());
					if (sInd2 >= 0)
						tempStatus[sInd2] = jt;
				}
			}

			submissionStatus = tempStatus;
			return submissionStatus[sInd];
		}
	}

	@Override
	public boolean isJudged(ISubmission submission) {
		if (submission == null)
			return false;

		return getJudgementType(submission) != null;
	}

	@Override
	public boolean isFirstToSolve(ISubmission submission) {
		if (submission == null)
			return false;

		String id = submission.getId();
		int problemIndex = getProblemIndex(submission.getProblemId());

		String[] tempFTS = fts;
		if (tempFTS != null)
			return id.equals(tempFTS[problemIndex]);

		synchronized (data) {
			if (fts != null)
				return id.equals(fts[problemIndex]);

			calculateResultsAndStandings();

			return id.equals(fts[problemIndex]);
		}
	}

	@Override
	public ILanguage[] getLanguages() {
		ILanguage[] temp = languages;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (languages != null)
				return languages;

			languages = data.getByType(ILanguage.class, ContestType.LANGUAGE);
			return languages;
		}
	}

	@Override
	public ILanguage getLanguageById(String id) {
		return (ILanguage) data.getById(id, ContestType.LANGUAGE);
	}

	@Override
	public IJudgementType[] getJudgementTypes() {
		IJudgementType[] tempTypes = judgementTypes;
		if (tempTypes != null)
			return tempTypes;

		synchronized (data) {
			if (judgementTypes != null)
				return judgementTypes;

			judgementTypes = data.getByType(IJudgementType.class, ContestType.JUDGEMENT_TYPE);
			return judgementTypes;
		}
	}

	@Override
	public IJudgementType getJudgementTypeById(String id) {
		return (IJudgementType) data.getById(id, ContestType.JUDGEMENT_TYPE);
	}

	@Override
	public int getNumRuns() {
		return getRuns().length;
	}

	@Override
	public IRun[] getRuns() {
		IRun[] temp = runs;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (runs != null)
				return runs;

			runs = data.getByType(IRun.class, ContestType.RUN);
			return runs;
		}
	}

	@Override
	public IRun getRunById(String id) {
		return (IRun) data.getById(id, ContestType.RUN);
	}

	@Override
	public IRun[] getRunsByJudgementId(String id) {
		if (id == null)
			return null;

		IRun[] temp = getRuns();
		List<IRun> list = new ArrayList<>();
		for (IRun r : temp) {
			if (id.equals(r.getJudgementId()))
				list.add(r);
		}
		if (list.isEmpty())
			return null;

		return list.toArray(new IRun[list.size()]);
	}

	@Override
	public int getNumClarifications() {
		return getClarifications().length;
	}

	@Override
	public IClarification[] getClarifications() {
		IClarification[] temp = clars;
		if (temp != null)
			return temp;

		synchronized (data) {
			if (clars != null)
				return clars;

			clars = data.getByType(IClarification.class, ContestType.CLARIFICATION);
			return clars;
		}
	}

	@Override
	public IClarification getClarificationById(String id) {
		if (id == null)
			return null;

		IClarification[] temp = getClarifications();
		for (IClarification c : temp) {
			if (c.getId().equals(id))
				return c;
		}
		return null;
	}

	@Override
	public List<String> validate() {
		List<String> errors = new ArrayList<>();

		synchronized (data) {
			for (IContestObject co : data) {
				List<String> list = co.validate(this);

				if (list != null) {
					IContestObject cur = data.getById(co.getId(), co.getType());
					String ss = "";
					if (cur != co)
						ss = "[Old instance] ";

					for (String s : list)
						errors.add(
								ss + "Invalid " + IContestObject.getTypeName(co.getType()) + " (" + co.getId() + "): " + s);
				}
			}
		}

		if (errors.isEmpty())
			return null;
		return errors;
	}

	public void list() {
		data.listByType();
	}

	private void removeSubmission(List<IContestObject> remove, ISubmission s) {
		if (s == null)
			return;

		IJudgement[] judgs = getJudgementsBySubmissionId(s.getId());
		if (judgs != null) {
			for (IJudgement j : judgs) {
				IRun[] runs2 = getRunsByJudgementId(j.getId());
				if (runs2 != null)
					remove.addAll(Arrays.asList(runs2));
				remove.add(j);
			}
		}

		remove.add(s);
	}

	public void removeHiddenTeams() {
		ITeam[] teams2 = getTeams();

		List<IContestObject> remove = new ArrayList<>();
		List<String> teamIds = new ArrayList<>();

		for (ITeam t : teams2) {
			if (isTeamHidden(t)) {
				remove.add(t);
				teamIds.add(t.getId());
			}
		}

		for (ISubmission s : getSubmissions()) {
			String teamId = s.getTeamId();
			ITeam t = getTeamById(teamId);
			if (t != null && isTeamHidden(t))
				removeSubmission(remove, s);
		}

		for (IClarification clar : getClarifications()) {
			boolean foundClar = false;
			String teamId = clar.getFromTeamId();
			if (teamId != null && teamIds.contains(teamId)) {
				remove.remove(clar);
				foundClar = true;
			}
			teamId = clar.getToTeamId();
			if (!foundClar && teamId != null && teamIds.contains(teamId)) {
				remove.remove(clar);
			}
		}

		Trace.trace(Trace.INFO, "Removing " + teamIds.size() + " teams and " + remove.size() + " total objects");
		for (IContestObject obj : remove)
			removeFromHistory(obj);
	}

	public int removeSubmissionsOutsideOfContestTime() {
		List<IContestObject> remove = new ArrayList<>();

		for (ISubmission s : getSubmissions()) {
			int time = s.getContestTime();
			if (time < 0 || time >= getDuration())
				removeSubmission(remove, s);
		}

		Trace.trace(Trace.INFO, "Removing " + remove.size() + " invalid submissions");
		for (IContestObject obj : remove)
			removeFromHistory(obj);

		return remove.size();
	}

	public int removeUnjudgedSubmissions() {
		List<IContestObject> remove = new ArrayList<>();

		for (ISubmission s : getSubmissions()) {
			Status st = getStatus(s);
			if (st == Status.SUBMITTED)
				removeSubmission(remove, s);
		}

		Trace.trace(Trace.INFO, "Removing " + remove.size() + " invalid submissions");
		for (IContestObject obj : remove)
			removeFromHistory(obj);

		return remove.size();
	}

	public void setHashCode(int hash) {
		this.hash = hash;
	}

	protected int hash;

	@Override
	public int hashCode() {
		if (hash != 0)
			return hash;

		int hash2 = data.size();
		if (info != null)
			hash2 += info.deepHash();
		return hash2;
	}

	@Override
	public String toString() {
		return "Contest " + getId();
	}
}