package org.icpc.tools.contest.model.feed;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.Locale;

import org.icpc.tools.contest.model.internal.FileReferenceList;

public class JSONEncoder {
	private static final NumberFormat nf = NumberFormat.getInstance(Locale.US);
	private static final NumberFormat df = NumberFormat.getInstance(Locale.US);

	static {
		nf.setGroupingUsed(false);
		nf.setMaximumFractionDigits(0);
		df.setGroupingUsed(false);
	}

	private static final ThreadLocal<String> local = new ThreadLocal<>();
	private static String DEFAULT_HOST = "cds";
	static {
		try {
			DEFAULT_HOST = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			// ignore
		}
	}

	public static String HOST = null;

	private boolean first = true;
	private PrintWriter pw;

	public JSONEncoder(PrintWriter pw) {
		this.pw = pw;
	}

	public void reset() {
		first = true;
	}

	public void unreset() {
		first = false;
	}

	public void open() {
		if (!first)
			pw.write(",");
		pw.write("{");
		first = true;
	}

	public void openChild(String name) {
		if (!first)
			pw.write(",");
		pw.write("\"" + name + "\":{");
		first = true;
	}

	public void openArray() {
		if (!first)
			pw.write(",");
		pw.write("[");
		first = true;
	}

	public void openChildArray(String name) {
		if (!first)
			pw.write(",");
		pw.write("\"" + name + "\":[");
		first = true;
	}

	public void encode(String name) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":null");
	}

	public void encode(String name, Integer value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + nf.format(value.intValue()));
	}

	public void encode(String name, int value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + nf.format(value));
	}

	public void encode(String name, long value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + nf.format(value));
	}

	public void encode(String name, Double value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + df.format(value.doubleValue()));
	}

	public void encode(String name, double value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + df.format(value));
	}

	public void encode(String name, Boolean value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + value.booleanValue());
	}

	public void encode(String name, boolean value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + value);
	}

	public void encode(String name, String value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		if (value == null)
			pw.write("\"" + name + "\":null");
		else
			pw.write("\"" + name + "\":\"" + JSONWriter.escape(value) + "\"");
	}

	public void encodeString(String name, String value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":\"" + value + "\"");
	}

	public void encodePrimitive(String name, String value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + name + "\":" + value);
	}

	public void encode(String name, FileReferenceList refList, boolean force) {
		if (!force && (refList == null || refList.isEmpty()))
			return;

		if (!first)
			pw.write(",");
		else
			first = false;
		if (refList == null)
			pw.write("\"" + name + "\":[]");
		else
			pw.write("\"" + name + "\":" + refList.getJSON());
	}

	public static void setThreadHost(String host) {
		local.set(host);
	}

	public void encodeSubs(String name, FileReferenceList refList, boolean force) {
		if (!force && (refList == null || refList.isEmpty()))
			return;

		if (!first)
			pw.write(",");
		else
			first = false;
		if (refList == null)
			pw.write("\"" + name + "\":[]");
		else {
			String s = refList.getJSON();
			String host = local.get();
			if (host == null)
				host = DEFAULT_HOST;
			s = s.replace("<host>", host);
			pw.write("\"" + name + "\":" + s);
		}
	}

	public void encode3(String name) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write("\"" + JSONWriter.escape(name) + "\":");
		first = true;
	}

	public void encodeValue(int value) {
		if (!first)
			pw.write(",");
		else
			first = false;
		pw.write(value + "");
		first = true;
	}

	public void close() {
		pw.write("}");
		first = true;
	}

	public void closeArray() {
		pw.write("]");
		first = false;
	}
}