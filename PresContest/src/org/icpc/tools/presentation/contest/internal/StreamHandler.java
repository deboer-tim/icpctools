package org.icpc.tools.presentation.contest.internal;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.icpc.tools.presentation.core.DisplayConfig;
import org.icpc.tools.presentation.core.IPresentationHandler;
import org.icpc.tools.presentation.core.Presentation;
import org.icpc.tools.presentation.core.Transition;

import io.humble.ferry.Buffer;
import io.humble.video.Codec;
import io.humble.video.Encoder;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;
import io.humble.video.PixelFormat;
import io.humble.video.Rational;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;

public class StreamHandler implements IPresentationHandler {
	protected Dimension DIM = new Dimension(1920, 1080);
	protected int port;
	protected int fps = 30;
	protected int maxSeconds = 10;
	protected Presentation pres = null;
	protected BufferedImage presImg;

	// protected OutputStream out;
	protected DatagramSocket socket;
	protected Encoder encoder;
	protected MediaPacket packet = MediaPacket.make();
	protected MediaPictureConverter converter;
	protected MediaPicture picture;
	protected int timestamp2;

	public StreamHandler(int port) {
		this.port = port;
		try {
			init();
			doBackground();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setPresentation(Presentation p) {
		setPresentations(0, new Presentation[] { p }, null);
	}

	@Override
	public void setPresentations(long time, Presentation[] newPresentations, Transition[] newTransitions) {
		newPresentations[0].setSize(DIM);
		newPresentations[0].init();
		newPresentations[0].aboutToShow();
		pres = newPresentations[0];
	}

	@Override
	public Dimension getPresentationSize() {
		return DIM;
	}

	@Override
	public String getPresentationName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getFPS() {
		return 30;
	}

	@Override
	public void setDisplayConfig(DisplayConfig p) {
		// ignore
	}

	@Override
	public DisplayConfig getDisplayConfig() {
		// TODO ignore
		return null;
	}

	@Override
	public int getFullScreenWindow() {
		// ignore
		return 0;
	}

	@Override
	public void setNanoTimeDelta(long time) {
		// TODO Auto-generated method stub

	}

	@Override
	public BufferedImage createImage(float scale) {
		// TODO ignore for now
		return null;
	}

	@Override
	public boolean isHidden() {
		return false;
	}

	@Override
	public void setHidden(boolean b) {
		// ignore
	}

	@Override
	public void setProperty(String key, String value) {
		// TODO Auto-generated method stub

	}

	protected void init() throws InterruptedException, IOException {
		final Rational framerate = Rational.make(1, fps);
		String formatname = null;

		final Muxer muxer = Muxer.make("file.m2ts", null, formatname);

		/**
		 * Now, we need to decide what type of codec to use to encode video. Muxers have limited sets
		 * of codecs they can use. We're going to pick the first one that works, or if the user
		 * supplied a codec name, we're going to force-fit that in instead.
		 */
		final MuxerFormat format = muxer.getFormat();
		final Codec codec;
		// if (codecname != null)
		// codec = Codec.findEncodingCodecByName(codecname);
		// else
		codec = Codec.findEncodingCodec(format.getDefaultVideoCodecId());
		//

		encoder = Encoder.make(codec);
		encoder.setWidth(DIM.width);
		encoder.setHeight(DIM.height);
		final PixelFormat.Type pixelformat = PixelFormat.Type.PIX_FMT_YUV420P;
		encoder.setPixelFormat(pixelformat);
		encoder.setTimeBase(framerate);

		if (format.getFlag(MuxerFormat.Flag.GLOBAL_HEADER))
			encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);

		encoder.open(null, null);
		muxer.addNewStream(encoder);
		muxer.open(null, null);
		// MuxerStream stream = muxer.getStream(0);
		// stream.

		/**
		 * Next, we need to make sure we have the right MediaPicture format objects to encode data
		 * with. Java (and most on-screen graphics programs) use some variant of Red-Green-Blue image
		 * encoding (a.k.a. RGB or BGR). Most video codecs use some variant of YCrCb formatting. So
		 * we're going to have to convert. To do that, we'll introduce a MediaPictureConverter object
		 * later.
		 */
		// MediaPictureConverter converter = null;
		// final MediaPicture
		picture = MediaPicture.make(encoder.getWidth(), encoder.getHeight(), pixelformat);
		picture.setTimeBase(framerate);
		// picture.setQuality(10);

		presImg = new BufferedImage(DIM.width, DIM.height, BufferedImage.TYPE_3BYTE_BGR);

		// File f = new File("file.m2ts");
		// out = new DataOutputStream(new FileOutputStream(f));
		// startSocketListener();
		HttpServer server = HttpServer.createSimpleServer(null, 5050);
		server.getServerConfiguration().addHttpHandler(new HttpHandler() {
			@Override
			public void service(Request request, Response response) throws Exception {
				DataOutputStream dout = new DataOutputStream(response.getOutputStream());
				dout.writeUTF("Hello!");
				response.setStatus(200, "awesome");
			}
		});
		server.start();
	}

	/*protected void startSocketListener() {
		Thread t = new Thread("Socket Listener") {
			@Override
			public void run() {
				try {
					ServerSocket socket = new ServerSocket(port);
					while (true) {
						try {
							Socket s = socket.accept();
							out = s.getOutputStream();
						} catch (Exception e) {
							System.out.println("Socket disconnected");
							out = null;
						}
					}
					socket = new DatagramSocket(port);
					while (true) {
						try {
							// Socket s = socket.send(p);
							// out = s.getOutputStream();
						} catch (Exception e) {
							System.out.println("Socket disconnected");
							// out = null;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		t.setDaemon(true);
		t.start();
	}*/

	protected void doBackground() {
		Thread t = new Thread("Background Streaming") {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (Exception e) {
					// ignore
				}
				System.out.println("Starting to stream...");
				int frame = 0;
				long firstFrame = System.currentTimeMillis();
				while (true) {
					if (pres != null) {
						long time = firstFrame + frame * 1000 / fps;
						pres.setTimeMs(time);
						pres.setRepeatTimeMs(time % pres.getRepeat());
						paint();
						output(frame);
					}
					frame++;
					if (frame % fps == 5)
						System.out.print(".");

					try {
						if (frame > fps * maxSeconds) {
							// if (out != null)
							// out.close();
							return;
						}
						long dt = firstFrame + frame * 1000 / fps - System.currentTimeMillis();
						if (dt > 100)
							Thread.sleep(dt);
					} catch (Exception e) {
						// ignore
					}
				}
			}
		};
		t.setDaemon(false);
		t.start();
	}

	protected void paint() {
		Graphics2D g = presImg.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, DIM.width, DIM.height);
		pres.paint(g);
		g.dispose();
	}

	protected void output(int frame) {
		if (converter == null)
			converter = MediaPictureConverterFactory.createConverter(presImg, picture);
		converter.toPicture(picture, presImg, frame);

		do {
			encoder.encode(packet, picture);
			if (packet.isComplete()) {
				Buffer b = packet.getData();
				long pos = packet.getPosition();
				int size = packet.getSize();
				// if (out != null) {
				byte[] bu = b.getByteArray((int) pos, size);
				System.out.print("." + bu.length);
				// }
				/*try {
					// out.write(bu, 0, size);
					InetAddress address = InetAddress.getByName("localhost");
					DatagramPacket dp = new DatagramPacket(bu, size, address, 5050);
					socket.send(dp);
				} catch (IOException e) {
					e.printStackTrace();
				}*/
				// }
			}
		} while (packet.isComplete());
	}
}