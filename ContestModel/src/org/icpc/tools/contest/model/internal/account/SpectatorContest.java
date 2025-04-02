package org.icpc.tools.contest.model.internal.account;

import org.icpc.tools.contest.model.IAccount;
import org.icpc.tools.contest.model.IContestObject;
import org.icpc.tools.contest.model.IDelete;
import org.icpc.tools.contest.model.ISubmission;

/**
 * Filter that adds things spectators can see compared to public/team area:
 * <ul>
 * <li>Team desktop, webcams (until the freeze)</li>
 * <li>Team tool data, key log</li>
 * <li>Submission language</li>
 * <li>Submission reaction videos (until the freeze)</li>
 * <li>Commentary</li>
 * </ul>
 */
public class SpectatorContest extends PublicContest {
	public SpectatorContest(IAccount account) {
		super();
		username = account.getUsername();
	}

	@Override
	public void add(IContestObject obj) {
		IContestObject.ContestType cType = obj.getType();
		if (obj instanceof IDelete) {
			addIt(obj);
			return;
		}

		switch (cType) {
			case COMMENTARY: {
				addIt(obj);
				return;
			}
			default: {
				super.add(obj);
			}
		}
	}

	@Override
	public boolean allowProperty(IContestObject obj, String property) {
		switch (obj.getType()) {
			case TEAM: {
				if (property.startsWith("desktop") || property.startsWith("webcam") || property.startsWith("audio")
						|| property.startsWith("tool_data") || property.startsWith("key_log")) {
					return this.getState().getStarted() != null && !this.getState().isFrozen();
				}
				return super.allowProperty(obj, property);
			}
			case SUBMISSION: {
				ISubmission s = (ISubmission) obj;
				if (property.startsWith("reaction")) {
					return this.isBeforeFreeze(s);
				}
				return super.allowProperty(obj, property);
			}
			default:
				return super.allowProperty(obj, property);
		}
	}

	@Override
	public boolean canAccessProperty(IContestObject.ContestType type, String property) {
		switch (type) {
			case TEAM: {
				if (property.startsWith("desktop") || property.startsWith("webcam") || property.startsWith("audio")
						|| property.startsWith("tool_data") || property.startsWith("key_log"))
					return true;

				return super.canAccessProperty(type, property);
			}
			case SUBMISSION: {
				if (property.startsWith("language_id") || property.startsWith("reaction")) {
					return true;
				}
				return super.canAccessProperty(type, property);
			}
			default:
				return super.canAccessProperty(type, property);
		}
	}
}
