package org.icpc.tools.cds.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.icpc.tools.cds.CDSConfig;
import org.icpc.tools.cds.ConfiguredContest;
import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.IJudgement;
import org.icpc.tools.contest.model.IJudgementType;
import org.icpc.tools.contest.model.ISubmission;

/**
 * An /api/metrics service for Prometheus.
 */
public class MetricsService2 {
	protected enum MetricType {
		GUAGE
	}

	// helper class to store contest metrics
	static class Metrics {
		Map<String, Integer> jtCount = new HashMap<String, Integer>();
		int queued;
	}

	public static void write(HttpServletRequest request, HttpServletResponse response) throws IOException {
		PrintWriter pw = response.getWriter();

		ConfiguredContest[] ccs = CDSConfig.getContests();
		int numContests = ccs.length;
		// IContest[] contests = new IContest[numContests];
		Metrics[] metrics = new Metrics[numContests];
		for (int i = 0; i < numContests; i++) {
			IContest contest = ccs[i].getContestByRole(request);

			int numTeams = contest.getNumTeams();
			String contestDetail = "{contest=\"" + contest.getId() + "\"}";
			outputMetric(pw, "teams_total", "Total number of teams", MetricType.GUAGE, contestDetail, numTeams + "");

			int numProblems = contest.getNumProblems();
			outputMetric(pw, "problems_total", "Total number of problems", MetricType.GUAGE, contestDetail,
					numProblems + "");

			int numSubmissions = contest.getNumSubmissions();
			outputMetric(pw, "submissions_total", "Total number of submissions", MetricType.GUAGE, contestDetail,
					numSubmissions + "");
		}

		// TODO hidden contests

		pw.println("# HELP submissions Number of submissions per contest per judgement type");
		pw.println("# TYPE submissions gauge");
		// submissions{contest="nwerc18",judgement="AC"} 5

		for (IContest contest : contests) {
			Metrics m = new Metrics();
			metrics[0] = m;
			IJudgementType[] jts = contest.getJudgementTypes();
			for (IJudgementType jt : jts)
				m.jtCount.put(jt.getId(), 0);

			ISubmission[] subs = contest.getSubmissions();
			for (ISubmission s : subs) {
				IJudgement[] juds = contest.getJudgementsBySubmissionId(s.getId());
				if (juds != null && juds.length > 0) {
					IJudgement j = juds[juds.length - 1];
					IJudgementType jt = contest.getJudgementTypeById(j.getJudgementTypeId());
					Integer in = m.jtCount.get(jt.getId());
					m.jtCount.put(jt.getId(), in + 1);
				} else
					m.queued++;
			}

			for (String s : m.jtCount.keySet()) {
				Integer in = m.jtCount.get(s);
				pw.println("submissions{contest=\"" + contest.getId() + "\",judgement=\"" + s + "\"} " + in.intValue());
			}
		}

		pw.println("# HELP submissions_queued Number of queued submissions");
		pw.println("# TYPE submissions_queued gauge");
		// submissions_queued{contest="nwerc18"} 1

		pw.println("# HELP teams_with_queued_submission Number of teams that have a queued submission");
		pw.println("# TYPE teams_with_queued_submission gauge");
		// teams_with_queued_submission{contest="nwerc18"} 1

		pw.println("# HELP teams_total Total number of teams");
		pw.println("# TYPE teams_total gauge");
		// teams_total{contest="nwerc18"} 150

		for (IContest contest : contests)
			pw.println("teams_total{contest=\"" + contest.getId() + "\"} " + contest.getNumTeams());

		pw.println("# HELP teams_with_correct_submission Number of teams that have solved at least one problem");
		pw.println("# TYPE teams_with_correct_submission gauge");
		// teams_with_correct_submission{contest="nwerc18"} 137

		pw.println("# HELP teams_with_submission Number of teams that have submitted at least once");
		pw.println("# TYPE teams_with_submission gauge");
		// teams_with_submission{contest="nwerc18"} 138

		pw.println("# HELP submissions_per_problem Submissions per problem and per judgement type");
		pw.println("# TYPE submissions_per_problem gauge");
		// submissions_per_problem_total{contest="nwerc18",problem="A",judgement="AC"} 3

		outputMetric(pw, "team_rank", "Rank per team", MetricType.GUAGE, "", "7");
		pw.println("# TYPE team_rank gauge");
		// team_rank{contest="nwerc18",team="3"} 1

		pw.println("# HELP clarifications_total Number of clarifications");
		pw.println("# TYPE clarifications_total gauge");
		// clarifications_total{contest="nwerc18"} 10

		for (IContest contest : contests)
			pw.println("clarifications_total{contest=\"" + contest.getId() + "\"} " + contest.getNumClarifications());

		pw.println("# HELP clarifications_replied Number of replied clarifications");
		pw.println("# TYPE clarifications_replied gauge");
		// clarifications_replied{contest="nwerc18"} 5
	}

	private static void outputMetric(PrintWriter pw, String name, String title, MetricType type, String detail,
			String value) {
		pw.println("# HELP " + name + " " + title);
		pw.println("# TYPE " + name + " " + type.name().toLowerCase());
		pw.println(name + detail + " " + value);
	}
}