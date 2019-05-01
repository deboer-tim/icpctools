package org.icpc.tools.cds.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.icpc.tools.cds.ConfiguredContest;
import org.icpc.tools.cds.util.Role;
import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.internal.Contest;
import org.icpc.tools.contest.model.util.AwardUtil;

public class FinalizeService {
	protected static void doPut(HttpServletRequest request, HttpServletResponse response, String command,
			ConfiguredContest cc) throws IOException {
		if (!Role.isAdmin(request)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		IContest contest = cc.getContest();
		if (contest.getState().isRunning()) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Contest still running");
			return;
		}

		// valid commands:
		// b# - set value of b and assign awards

		System.out.println("Finalize command: " + command);
		try {
			Contest c = (Contest) contest;
			if (command.startsWith("b:")) {
				int b = Integer.parseInt(command.substring(2));
				AwardUtil.createWorldFinalsAwards(c, b);
			}
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			return;
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			e.printStackTrace();
			return;
		}
	}
}