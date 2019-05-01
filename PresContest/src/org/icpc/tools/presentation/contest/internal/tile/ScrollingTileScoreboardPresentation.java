package org.icpc.tools.presentation.contest.internal.tile;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

import org.icpc.tools.contest.model.IContest;
import org.icpc.tools.contest.model.ITeam;
import org.icpc.tools.presentation.contest.internal.Animator;
import org.icpc.tools.presentation.contest.internal.Animator.Movement;
import org.icpc.tools.presentation.contest.internal.ICPCFont;
import org.icpc.tools.presentation.contest.internal.ScrollAnimator;

/**
 * Column-based scrolling.
 */
public abstract class ScrollingTileScoreboardPresentation extends AbstractTileScoreboardPresentation {
	private static final int MS_PER_COLUMN = 8000; // scroll a column every 8 seconds
	private static final int FADE_IN_MS = 1000; // initial fade in, 1 second
	private static final int INITIAL_DELAY_MS = 5000; // extra time on initial screen, 5 seconds
	private static final int MS_PER_SCREEN = 10000; // scroll a full screen every 10 seconds
	private static final int FADE_OUT_MS = 1000; // fade out at end, 1 second

	protected final ScrollAnimator scroll = new ScrollAnimator(INITIAL_DELAY_MS, MS_PER_COLUMN, MS_PER_COLUMN / 5);
	protected final Animator vScroll = new Animator(0, new Movement(3, 8));

	enum Direction {
		HORIZONTAL, VERTICAL
	}

	enum Header {
		TOP, LEFT, NONE
	}

	protected Direction dir = Direction.HORIZONTAL;
	protected Header header = Header.LEFT;

	protected int margin = 50;
	private Font titleFont;
	private Font clockFont;

	public ScrollingTileScoreboardPresentation() {
		// defaults
	}

	public ScrollingTileScoreboardPresentation(Direction d, Header h) {
		dir = d;
		header = h;
	}

	@Override
	public void setSize(Dimension d) {
		super.setSize(d);

		float size = tileDim.height * 36f * 0.95f / 96;
		titleFont = ICPCFont.getMasterFont().deriveFont(Font.BOLD, size * 1.9f);
		clockFont = ICPCFont.getMasterFont().deriveFont(Font.BOLD, size * 0.8f);
	}

	@Override
	protected TeamTileHelper createTileHelper() {
		if (header == Header.NONE)
			return super.createTileHelper();

		if (header == Header.TOP) {
			margin = (int) ((height - (rows - 1) * TILE_V_GAP) * 1.2 / rows);
			tileDim = new Dimension((width - (columns - 1) * TILE_H_GAP) / columns,
					(height - (rows - 1) * TILE_H_GAP - margin) / rows);
		} else if (header == Header.LEFT) {
			margin = (int) ((height - (rows - 1) * TILE_V_GAP) * 1.3 / rows);
			tileDim = new Dimension((width - (columns - 1) * TILE_H_GAP - margin) / columns,
					(height - (rows - 1) * TILE_H_GAP) / rows);
		}
		return new TeamTileHelper(tileDim, getContest(), overrideStyle);
	}

	private int getNumColumns() {
		if (dir == Direction.VERTICAL)
			return 1;
		IContest contest = getContest();
		if (contest == null)
			return 1;

		return (contest.getOrderedTeams().length + rows) / rows;
	}

	protected int getNumRows() {
		if (dir == Direction.HORIZONTAL)
			return 1;
		IContest contest = getContest();
		if (contest == null)
			return 1;

		return (contest.getOrderedTeams().length + columns - 1) / columns;
	}

	@Override
	public long getRepeat() {
		IContest contest = getContest();
		if (contest == null)
			return 1;

		if (dir == Direction.HORIZONTAL)
			return INITIAL_DELAY_MS + MS_PER_COLUMN * getNumColumns() + FADE_OUT_MS;

		return INITIAL_DELAY_MS + (int) (MS_PER_SCREEN * (getNumRows() / (double) rows));
	}

	protected void updateScrollTarget() {
		long time = getRepeatTimeMs();
		if (time <= INITIAL_DELAY_MS)
			vScroll.setTarget(0, 0);
		else
			vScroll.setTarget((time - INITIAL_DELAY_MS) / (double) MS_PER_SCREEN, 1000.0 / MS_PER_SCREEN);
	}

	@Override
	public void aboutToShow() {
		super.aboutToShow();

		if (dir == Direction.HORIZONTAL)
			return;

		updateScrollTarget();
		vScroll.resetToTarget();
	}

	@Override
	public void incrementTimeMs(long dt) {
		if (dir == Direction.VERTICAL) {
			updateScrollTarget();
			vScroll.incrementTimeMs(dt);
		}

		super.incrementTimeMs(dt);
	}

	protected abstract String getTitle();

	protected void paintHeader(Graphics2D g) {
		if (header == Header.LEFT) {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// int arc = tileDim.width / 40;
			int h = rows * (tileDim.height + TILE_V_GAP) - TILE_V_GAP;
			g.setColor(Color.DARK_GRAY);
			// g.fillRoundRect(0, TILE_GAP, margin - TILE_GAP, height - TILE_GAP, arc, arc);
			// g.fillRoundRect(0, 0, margin - TILE_GAP, rows * (tileDim.height + TILE_GAP) - TILE_GAP,
			// arc, arc);
			g.fillRect(0, 0, margin - TILE_H_GAP, h);

			g.setColor(Color.BLACK);
			g.drawLine(margin - TILE_H_GAP, 0, margin - TILE_V_GAP, h);

			String title = getTitle();
			if (title != null) {
				Graphics2D gg = (Graphics2D) g.create();
				gg.setFont(titleFont);
				FontMetrics fm = gg.getFontMetrics();
				gg.setColor(Color.WHITE);
				gg.translate((margin - TILE_H_GAP + fm.getAscent()) / 2, (h + fm.stringWidth(title)) / 2);
				gg.rotate(-Math.PI / 2);
				gg.drawString(title, 0, 0);
				gg.dispose();
			}

			// if (showClock) {
			g.setColor(Color.WHITE);
			g.setFont(clockFont);
			FontMetrics fm = g.getFontMetrics();
			String s = getContestTime();
			if (s != null) {
				String[] ss = splitString(g, s, margin);
				for (int i = 0; i < ss.length; i++)
					g.drawString(ss[i], (margin - fm.stringWidth(ss[i])) / 2, fm.getHeight() * (i + 1) + TILE_V_GAP);
			}

			s = getRemainingTime();
			// TODO - if 10 min left, go red?
			if (s != null) {
				String[] ss = splitString(g, s, margin);
				for (int i = 0; i < ss.length; i++)
					g.drawString(ss[i], (margin - fm.stringWidth(ss[i])) / 2,
							h - TILE_V_GAP - (ss.length - i - 1) * fm.getHeight());
			}
		}
	}

	protected void paintBackground(Graphics2D g) {
		int arc = tileDim.width / 70;
		g.setColor(TeamTileHelper.TILE_BG);
		for (int i = 0; i < rows; i += 2) {
			for (int j = 0; j < columns; j++) {
				g.fillRoundRect((tileDim.width + TILE_H_GAP) * j, (tileDim.height + TILE_V_GAP) * i,
						tileDim.width + TILE_H_GAP / 2, tileDim.height + TILE_V_GAP / 2, arc, arc);
			}
		}
	}

	@Override
	protected void paintImpl(Graphics2D g) {
		Graphics2D gg = (Graphics2D) g.create();
		if (header == Header.LEFT)
			gg.translate(margin, 0);
		else if (header == Header.TOP)
			gg.translate(0, margin);

		if (dir == Direction.HORIZONTAL) {
			long time = getRepeatTimeMs();
			int hScroll = (int) (scroll.getScroll(time) * (tileDim.width + TILE_H_GAP));

			paintBackground(gg);

			if (time < FADE_IN_MS)
				gg.setComposite(AlphaComposite.SrcOver.derive(time / (float) FADE_IN_MS));

			long endTime = INITIAL_DELAY_MS + MS_PER_COLUMN * getNumColumns();
			if (time > endTime)
				gg.setComposite(AlphaComposite.SrcOver.derive(1.0f - (time - endTime) / (float) FADE_OUT_MS));

			gg.translate(-hScroll, 0);
			paintTiles(gg, hScroll);
		} else {
			int vScrollv = (int) (vScroll.getValue() * height);
			gg.translate(0, -vScrollv);

			paintBackground(gg);

			long time = getRepeatTimeMs();
			if (time < FADE_IN_MS)
				gg.setComposite(AlphaComposite.SrcOver.derive(time / (float) FADE_IN_MS));

			paintContent(gg);
		}
		gg.dispose();
		if (header != Header.NONE)
			paintHeader(g);
	}

	protected void paintTiles(Graphics2D g, int hScroll) {
		IContest contest = getContest();
		if (contest == null)
			return;

		ITeam[] teams = contest.getOrderedTeams();
		long timeMs = getTimeMs();
		for (int i = teams.length - 1; i >= 0; i--) {
			ITeam team = teams[i];

			TileAnimator anim = teamMap.get(team.getId());
			if (anim != null) {
				double yy = anim.getValue().getY();
				int col = (int) (yy / rows);
				int x = col * (tileDim.width + TILE_H_GAP);
				int y = (int) ((yy - col * rows) * (tileDim.getHeight() + TILE_V_GAP));

				if ((x - hScroll + tileDim.width > 0 && x - hScroll < width - margin))
					tileHelper.paintTile(g, x, y, anim.getZoom(), team, timeMs);

				// paint tiles that are half off the bottom twice (second time at the top of the next
				// column)
				int colHeight = (int) ((tileDim.getHeight() + TILE_V_GAP) * rows);
				if (y > colHeight - tileDim.height) {
					y -= colHeight;
					x += tileDim.width + TILE_H_GAP;
					if ((x - hScroll + tileDim.width > 0 && x - hScroll < width - margin))
						tileHelper.paintTile(g, x, y, anim.getZoom(), team, timeMs);
				}
			}
		}
	}

	protected void paintContent(Graphics2D g) {
		IContest contest = getContest();
		if (contest == null)
			return;

		int vScrollv = (int) (vScroll.getValue() * height);
		long timeMs = getTimeMs();
		ITeam[] teams = contest.getOrderedTeams();
		for (int i = teams.length - 1; i >= 0; i--) {
			TileAnimator anim = teamMap.get(teams[i].getId());
			if (anim != null) {
				Point2D p = anim.getValue();
				int x = (int) (p.getX() * (tileDim.width + TILE_H_GAP));
				int y = (int) (p.getY() * (tileDim.height + TILE_V_GAP));

				if (y - vScrollv + tileDim.height > 0 && y - vScrollv < height)
					tileHelper.paintTile(g, x, y, anim.getZoom(), teams[i], timeMs);
			}
		}
	}
}