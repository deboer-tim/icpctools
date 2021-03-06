package org.icpc.tools.presentation.contest.internal.tile;

import java.awt.geom.Point2D;

import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.IOrganization;
import org.icpc.tools.contest.model.ITeam;
import org.icpc.tools.presentation.contest.internal.TeamUtil.Style;

public class TileListScoreboardPresentation extends ScrollingTileScoreboardPresentation {
	@Override
	protected String getTitle() {
		return "Alphabetic list of teams";
	}

	@Override
	protected void updateTeamTargets(ITeam[] teams, Point2D[] targets) {
		if (teams == null || teams.length == 0)
			return;

		IContest contest = getContest();
		Style style = tileHelper.getStyle();
		int size = teams.length;
		int[] sort = new int[size];
		String[] names = new String[size];
		for (int i = 0; i < size; i++) {
			sort[i] = i;
			names[i] = teams[i].getName();
			IOrganization org = contest.getOrganizationById(teams[i].getOrganizationId());
			if (org != null) {
				if (style == Style.ORGANIZATION_NAME)
					names[i] = org.getName();
				else if (style == Style.ORGANIZATION_FORMAL_NAME)
					names[i] = org.getActualFormalName();
			}
		}

		for (int i = 0; i < size - 1; i++) {
			for (int j = i + 1; j < size; j++) {
				if (names[sort[i]].compareTo(names[sort[j]]) > 0) {
					int t = sort[i];
					sort[i] = sort[j];
					sort[j] = t;
				}
			}
		}

		for (int i = 0; i < size; i++)
			targets[sort[i]].setLocation(0, i);
	}
}