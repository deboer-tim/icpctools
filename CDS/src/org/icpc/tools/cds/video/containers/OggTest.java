package org.icpc.tools.cds.video.containers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Ogg container handler.
 */
public class OggTest {

	public static void main(String[] args) throws Exception {
		byte[] b = new byte[27]; // a little over 90K
		// header - bitstream - page - granule - size - crc

		InputStream in = new FileInputStream(new File("/Users/deboer/Downloads/pat2.ogg"));
		OutputStream out = new FileOutputStream(new File("/Users/deboer/Downloads/test2-out.ogg"));

		Map<Integer, byte[]> streams = new HashMap<Integer, byte[]>();

		long[] crc_lookup = new long[256];
		for (int i = 0; i < 256; i++)
			crc_lookup[i] = _ogg_crc_entry(i);

		int count = 0;
		CRC32 crc = new CRC32();
		while (true) {
			crc.reset();
			boolean doOut = false;
			// read the first 27 bytes
			int n = 0;
			while (n < b.length) {
				n += in.read(b, n, b.length - n);
				if (n == -1) { // TODO
					out.close();
					// System.out.println("Packets: " + count);
					return;
				}
			}
			count++;

			// confirm ogg packet 79.103.103.83 = OggS
			if (b[0] != 79 || b[1] != 103 || b[2] != 103 || b[3] != 83)
				throw new IOException("Failed checksum");

			// if (b[18] == 0)
			// doOut = false;
			// if (b[18] == 1)
			// b[5] = 2;
			// b[18]--;

			// read segment lengths
			int numSegments = Byte.toUnsignedInt(b[26]);
			// int header = Byte.toUnsignedInt(b[5]); // 2 0 0 0 0 0 0 4
			String bsc = Byte.toUnsignedInt(b[14]) + "." + Byte.toUnsignedInt(b[15]) + "." + Byte.toUnsignedInt(b[16])
					+ "." + Byte.toUnsignedInt(b[17]);
			String psc = Byte.toUnsignedInt(b[18]) + "." + Byte.toUnsignedInt(b[19]) + "." + Byte.toUnsignedInt(b[20])
					+ "." + Byte.toUnsignedInt(b[21]); // 0.0.0.0 -> 1.0.0.0 -> 2.0.0.0
			String gp = Byte.toUnsignedInt(b[6]) + "-" + Byte.toUnsignedInt(b[7]) + "-" + Byte.toUnsignedInt(b[8]) + "-"
					+ Byte.toUnsignedInt(b[9]) + "-" + Byte.toUnsignedInt(b[12]) + "-" + Byte.toUnsignedInt(b[13]);
			// String chk = Byte.toUnsignedInt(b[22]) + "_" + Byte.toUnsignedInt(b[23]) + "_" +
			// Byte.toUnsignedInt(b[24])
			// + "_" + Byte.toUnsignedInt(b[25]);
			ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
			/*buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.put(b, 22, 4);
			buffer.flip();// need flip
			int chk = buffer.getInt();*/

			buffer = ByteBuffer.allocate(Integer.BYTES);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.put(b, 18, 4);
			buffer.flip();// need flip
			// int psc2 = buffer.getInt();
			// System.out.print(header + " " + b[5]);
			System.out.print(b[5] + "\t" + bsc + "\t" + psc + "\t" + gp);
			// System.out.print(" " + psc2);
			// System.out.print(" " + gp);

			if ("0-0-0-0-0-0".equals(gp) || count > 100) {
				// if (b[6] == 0 || count > 100) {// || Byte.toUnsignedInt(b[14]) == 64) {
				doOut = true;
				// System.out.print("x");
				int str = b[14];
				byte[] ob = null;
				if (!streams.containsKey(str)) {
					byte[] bb = new byte[b.length];
					System.arraycopy(b, 0, bb, 0, b.length);
					streams.put(str, bb);
					ob = bb;
				} else {
					ob = streams.get(str);
					ob[18]++;
					b[18] = ob[18];
				}

				/*if (!streams.containsKey(str)) {
					byte[] bb = new byte[b.length];
					System.arraycopy(b, 0, bb, 0, b.length);
					streams.put(str, bb);
					b[5] = 2;
				}
				byte[] ob = streams.get(str);
				int x = ob[18];
				b[18] -= x;*/

				buffer = ByteBuffer.allocate(Long.BYTES);
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.put(b, 6, 8);
				buffer.flip();// need flip
				long cur_gp = buffer.getLong();

				buffer = ByteBuffer.allocate(Long.BYTES);
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.put(ob, 6, 8);
				buffer.flip();// need flip
				long or_gp = buffer.getLong();
				// System.out.print(" " + cur_gp + ", " + or_gp + " -> ");
				// System.out.print("\t" + or_gp);

				cur_gp -= or_gp;
				// System.out.print(cur_gp + " ");

				buffer.putLong(0, cur_gp);
				byte[] byteb = buffer.array();
				System.arraycopy(byteb, 0, b, 6, 4);

				// int cur_gp = Byte.toUnsignedInt(b[6]) + Byte.toUnsignedInt(b[7]) * 255
				// + Byte.toUnsignedInt(b[8]) * 255 * 255;
				// int or_gp = Byte.toUnsignedInt(ob[6]) + Byte.toUnsignedInt(ob[7]) * 255
				// + Byte.toUnsignedInt(ob[8]) * 255 * 255;
				// // System.out.print(" " + cur_gp + ", " + or_gp + " -> ");
				// cur_gp -= or_gp;
				// b[6] = (byte) (cur_gp % 255);
				// b[7] = (byte) (cur_gp / 255);
				// b[8] = (byte) (cur_gp / 255 / 255);
				// // System.out.print(cur_gp + " ");

				psc = Byte.toUnsignedInt(b[18]) + "." + Byte.toUnsignedInt(b[19]) + "." + Byte.toUnsignedInt(b[20]) + "."
						+ Byte.toUnsignedInt(b[21]);
				// gp = Byte.toUnsignedInt(b[6]) + "-" + Byte.toUnsignedInt(b[7]) + "-" +
				// Byte.toUnsignedInt(b[8]) + "-"
				// + Byte.toUnsignedInt(b[9]) + "-" + Byte.toUnsignedInt(b[12]) + "-" +
				// Byte.toUnsignedInt(b[13]);

				/*buffer = ByteBuffer.allocate(Integer.BYTES);
				// buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.put(b, 22, 4);
				buffer.flip();// need flip
				chk = buffer.getInt();*/

				// System.out.print(" " + b[5]);
				// System.out.print(" " + psc);
				// System.out.print(" " + gp);

			}

			// ---- CRC
			byte[] bbb = new byte[b.length];
			System.arraycopy(b, 0, bbb, 0, b.length);
			bbb[22] = 0;
			bbb[23] = 0;
			bbb[24] = 0;
			bbb[25] = 0;
			crc.update(bbb); // TODO set CRC to null

			long crc_reg = 0;

			// TODO temp removal
			/*for (int i = 0; i < bbb.length; i++) {
				int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(bbb[i]));
				crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
				crc_reg &= 0xffffffff;
			}*/

			// read segment sizes
			byte[] bb = new byte[numSegments];
			n = 0;
			while (n < numSegments)
				n += in.read(bb, n, bb.length - n);

			// ---- CRC
			// TODO temp removal
			/*crc.update(bb);
			for (int i = 0; i < bb.length; i++) {
				int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(bb[i]));
				crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
				crc_reg &= 0xffffffff;
			}*/

			// total segments
			int segmentLength = 0;
			for (int i = 0; i < bb.length; i++)
				segmentLength += Byte.toUnsignedInt(bb[i]);

			// read all segments
			byte[] d = new byte[segmentLength];
			n = 0;
			while (n < segmentLength)
				n += in.read(d, n, d.length - n);

			// --- CRC ---
			crc.update(d);
			for (int i = 0; i < d.length; i++) {
				int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(d[i]));
				crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
				crc_reg &= 0xffffffff;
			}

			System.out.print("\t" + segmentLength + "\t" + crc_reg);

			// read segments
			// long segmentLength = 0;
			/*for (byte sl : bb) {
				int len = Byte.toUnsignedInt(sl);
				// System.out.print(len + " ");
				byte[] d = new byte[len];
				n = 0;
				while (n < len)
					n += in.read(d, n, d.length - n);
				// segmentLength += len;
			
				if (doOut)
					out.write(d);
			
				// --- CRC ---
				crc.update(d);
				for (int i = 0; i < d.length; i++) {
					int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(d[i]));
					crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
					crc_reg &= 0xffffffff;
				}
			}*/

			// --- CRC
			byte[] sum = new byte[4];
			// crc_reg = crc.getValue();
			sum[0] = (byte) (crc_reg & 0xffL);
			sum[1] = (byte) ((crc_reg >>> 8) & 0xffL);
			sum[2] = (byte) ((crc_reg >>> 16) & 0xffL);
			sum[3] = (byte) ((crc_reg >>> 24) & 0xffL);

			// rewrite crc
			b[22] = sum[0];
			b[23] = sum[1];
			b[24] = sum[2];
			b[25] = sum[3];

			/*buffer = ByteBuffer.allocate(Integer.BYTES);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.put(sum);
			buffer.flip();// need flip
			int psc3 = buffer.getInt();*/

			// output page

			if (doOut) {
				out.write(b);
				out.write(bb);
				out.write(d);
			}

			// System.out.println(" " + (int) crc.getValue() + " - " + psc3);
			System.out.println();
		}
	}

	private static long _ogg_crc_entry(long index) {
		long r;

		r = index << 24;
		for (int i = 0; i < 8; i++) {
			if ((r & 0x80000000L) != 0) {
				r = (r << 1) ^ 0x04c11db7L;
			} else {
				r <<= 1;
			}
		}
		return (r & 0xffffffff);
	}

	private static int touint(byte n) {
		return (n & 0xff);
	}

	/*private byte[] checksum(byte[] b) {
		long[] crc_lookup = new long[256];
		for (int i = 0; i < 256; i++)
			crc_lookup[i] = _ogg_crc_entry(i);

		long crc_reg = 0;

		for (int i = 0; i < b.length; i++) {
			int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(b[i]));
			crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
			crc_reg &= 0xffffffff;
		}
		for (int i = 0; i < packet.length; i++) {
			int tmp = (int) (((crc_reg >>> 24) & 0xff) ^ touint(packet[i]));
			crc_reg = (crc_reg << 8) ^ crc_lookup[tmp];
			crc_reg &= 0xffffffff;
		}

		byte[] sum = new byte[4];
		sum[0] = (byte) (crc_reg & 0xffL);
		sum[1] = (byte) ((crc_reg >>> 8) & 0xffL);
		sum[2] = (byte) ((crc_reg >>> 16) & 0xffL);
		sum[3] = (byte) ((crc_reg >>> 24) & 0xffL);

		return sum;
	}*/
}