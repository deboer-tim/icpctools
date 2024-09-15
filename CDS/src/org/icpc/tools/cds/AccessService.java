package org.icpc.tools.cds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.icpc.tools.cds.CDSConfig.Auth;
import org.icpc.tools.contest.model.IAccount;
import org.icpc.tools.contest.model.IContestObject;
import org.icpc.tools.contest.model.feed.JSONEncoder;

/**
 * Very first attempt at an /access service, only provides a couple capabilities for now, no
 * endpoints.
 */
public class AccessService {

	public static void write(HttpServletRequest request, HttpServletResponse response, ConfiguredContest cc)
			throws IOException {
		JSONEncoder je = new JSONEncoder(response.getWriter());
		je.open();
		List<String> caps = new ArrayList<String>();

		IAccount account = cc.getAccount(request.getRemoteUser());

		// write capabilities
		if (account != null) {
			String type = account.getAccountType();
			if ("team".equals(type)) {
				caps.add("team_submit");
				caps.add("team_clar");
			}
			if ("judge".equals(type)) {
				caps.add("judge_clar");
				caps.add("judge_comment");
			}
			if ("admin".equals(type)) {
				caps.add("contest_start");
				caps.add("contest_thaw");
				caps.add("admin_submit");
				caps.add("admin_clar");
				caps.add("judge_clar");
				caps.add("judge_comment");
			}
		}
		String user = request.getRemoteUser();
		if (user != null)
			for (Auth a : CDSConfig.getInstance().getAuths()) {
				if (user.equals(a.getUsername())) {
					if (a.getContestId() == null || cc.getContest().getId().equals(a.getContestId()))
						caps.add(a.getType() + "/" + a.getId());
				}
			}

		if (caps.isEmpty())
			je.encodePrimitive("capabilities", "[]");
		else
			je.encodePrimitive("capabilities", "[\"" + String.join("\",\"", caps) + "\"]");

		// write endpoints
		// TODO - verify that public can see problems and submissions type before the start of the
		// contest
		// but not
		je.writeSeparator();
		je.openChildArray("endpoints");
		Set<String>[] allKnownProperties = cc.getContestByRole(request).getKnownProperties();
		for (IContestObject.ContestType ct : IContestObject.ContestType.values()) {
			Set<String> properties = allKnownProperties[ct.ordinal()];
			if (properties != null) {
				je.writeSeparator();
				je.open();
				if (ct == IContestObject.ContestType.CONTEST)
					je.encode("type", "contest");
				else
					je.encode("type", IContestObject.getTypeName(ct));

				String s = String.join("\",\"", properties);
				je.encodePrimitive("properties", "[\"" + s + "\"]");
				je.close();
			}
		}
		je.closeArray();

		je.close();
	}
}