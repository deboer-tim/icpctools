package org.icpc.tools.contest.model.feed;

import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelativeTime {
	// pattern for relative times like: 1:22:05.034
	private static final Pattern TIME_PATTERN = Pattern.compile("-?([0-9]+):([0-9]{2}):([0-9]{2})(\\.[0-9]{3})?");

	public static long parse(String relTime) throws ParseException {
		Matcher match = TIME_PATTERN.matcher(relTime);
		if (!match.matches())
			throw new ParseException("Invalid relative time string: " + relTime, 0);

		long h = Integer.parseInt(match.group(1));
		long m = Integer.parseInt(match.group(2));
		long s = Integer.parseInt(match.group(3));
		if (m > 59 || s > 59)
			throw new ParseException("Invalid relative time string: " + relTime, 0);
		long ms = 0;
		if (match.group(4) != null) {
			ms = Integer.parseInt(match.group(4).substring(1));
		}

		long val = h * 60 * 60 * 1000 + m * 60 * 1000 + s * 1000 + ms;
		if (relTime.startsWith("-"))
			return -val;

		return val;
	}

	public static String format(Long relTimeMs) {
		if (relTimeMs == null)
			return "null";
		return format(relTimeMs.longValue());
	}

	public static String format(long relTimeMs) {
		long ms = relTimeMs;
		StringBuilder sb = new StringBuilder();
		if (relTimeMs < 0) {
			ms = -relTimeMs;
			sb.append("-");
		}

		long s = ms / 1000;
		if (s > 0)
			ms -= s * 1000;
		long m = s / 60;
		if (m > 0)
			s -= m * 60;
		long h = m / 60;
		if (h > 0)
			m -= h * 60;

		sb.append(h + ":");
		if (m < 10)
			sb.append("0");
		sb.append(m + ":");
		if (s < 10)
			sb.append("0");
		sb.append(s);
		sb.append(".");
		if (ms < 10)
			sb.append("00");
		else if (ms < 100)
			sb.append("0");
		sb.append(ms);
		return sb.toString();
	}

	public static void main(String[] args) throws Exception {
		String o = "1:22:05.034";
		// String o = "2:09:05.134";
		// String o = "2:09:05";
		long num = parse(o);
		System.out.println(o + " -> " + num + " -> " + format(num));
	}
}