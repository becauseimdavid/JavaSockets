
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;

public abstract class WebSocket extends Socket {

	String method, uri, protcol;
	HashMap<String, String> headers = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;

		public String get(Object key) {
			String value = super.get(key);
			return value == null ? "" : value;
		};
	};
	boolean mask;

	public WebSocket(java.net.Socket socket) {
		super(socket);
		mask = false;
	}

	public WebSocket(String address, int port) {
		super(address, port);
		mask = true;
	}

	protected void in(byte[] input) throws Exception {
		// optional overwrite
	}

	protected void onClose(int code) throws Exception {
		// optional overwrite
	}

	private ByteArrayOutputStream data = new ByteArrayOutputStream();
	private byte continuationType;

	@Override
	protected void readInput(InputStream stream) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String[] http = reader.readLine().split(" ");
		method = http[0];
		uri = http[1];
		protcol = http[2];
		for (String line = null; isOpen() && (line = reader.readLine()) != null;) {
			if (!line.contains(":"))
				break;
			int index = line.indexOf(':');
			headers.put(line.substring(0, index).trim().toLowerCase(), line.substring(++index).trim());
		}
		if (headers.get("connection").equalsIgnoreCase("upgrade")
				&& headers.get("upgrade").equalsIgnoreCase("websocket") && headers.get("sec-websocket-key") != "") {
			if (!onConnect())
				return;
			byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n" + "Connection: Upgrade\r\n"
					+ "Upgrade: websocket\r\n" + "Sec-WebSocket-Accept: "
					+ Base64.getEncoder()
							.encodeToString(MessageDigest.getInstance("SHA-1")
									.digest((headers.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
											.getBytes("UTF-8")))
					+ "\r\n\r\n").getBytes("UTF-8");
			socket.getOutputStream().write(response, 0, response.length);
			while (isOpen()) {
				Frame frame = Frame.read(stream);
				if (!frame.fin) {
					continuationType = frame.opcode;
					data = new ByteArrayOutputStream();
					data.write(frame.payload);
				} else if (frame.opcode == 0x0) {
					data.write(frame.payload);
					if (frame.fin) {
						if (continuationType == 0x1) {
							in(new String(data.toByteArray(), "UTF-8"));
						} else if (continuationType == 0x2) {
							in(data.toByteArray());
						}
					}
				} else if (frame.opcode == 0x1) {
					in(new String(frame.payload, "UTF-8"));
				} else if (frame.opcode == 0x2) {
					in(frame.payload);
				} else if (frame.opcode == 0x8) {
					short code = (short) ((frame.payload[0] << 8) | (frame.payload[1] & 0xff));
					onClose(code);
					close();
				} else if (frame.opcode == 0x9) {
					socket.getOutputStream()
							.write(new Frame(true, false, false, false, 0xA, frame.payload).toByteArray(mask));
				} else if (frame.opcode == 0xA) {
					socket.getOutputStream()
							.write(new Frame(true, false, false, false, 0xA, new byte[0]).toByteArray(mask));
				}
			}
		} else {
			close();
		}
	}

	@Override
	public void out(String output) {
		if (!isOpen())
			throw new RuntimeException("Can't write output while socket isn't open");
		try {
			socket.getOutputStream()
					.write(new Frame(true, false, false, false, 0x1, output.getBytes("UTF-8")).toByteArray(mask));
		} catch (Exception exc) {
			onError(exc);
		}
	}

	public void out(byte[] binary) {
		if (!isOpen())
			throw new RuntimeException("Can't write output while socket isn't open");
		try {
			socket.getOutputStream().write(new Frame(true, false, false, false, 0x2, binary).toByteArray(mask));
		} catch (Exception exc) {
			onError(exc);
		}
	}

	public void close(int code) {
		try {
			short c = (short) code;
			byte[] payload = new byte[2];
			payload[0] = ((byte) (((byte) (c >> 8)) & 0xff));
			payload[1] = ((byte) (((byte) (c >> 0)) & 0xff));
			socket.getOutputStream().write(new Frame(true, false, false, false, 0x8, payload).toByteArray(mask));
			socket.close();
		} catch (Exception exc) {
			onError(exc);
		}
	}

	static class Frame {

		private boolean fin, rsv1, rsv2, rsv3;
		private byte opcode;
		private byte[] payload;

		Frame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, byte opcode, byte[] payload) {
			this.fin = fin;
			this.rsv1 = rsv1;
			this.rsv2 = rsv2;
			this.rsv3 = rsv3;
			this.opcode = opcode;
			this.payload = payload;
		}

		Frame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, int opcode, byte[] payload) {
			this(fin, rsv1, rsv2, rsv3, (byte) opcode, payload);
		}

		public boolean isFin() {
			return fin;
		}

		public void setFin(boolean fin) {
			this.fin = fin;
		}

		public boolean isRsv1() {
			return rsv1;
		}

		public void setRsv1(boolean rsv1) {
			this.rsv1 = rsv1;
		}

		public boolean isRsv2() {
			return rsv2;
		}

		public void setRsv2(boolean rsv2) {
			this.rsv2 = rsv2;
		}

		public boolean isRsv3() {
			return rsv3;
		}

		public void setRsv3(boolean rsv3) {
			this.rsv3 = rsv3;
		}

		public byte getOpcode() {
			return opcode;
		}

		public void setOpcode(byte opcode) {
			this.opcode = opcode;
		}

		public byte[] getPayload() {
			return payload;
		}

		public void setPayload(byte[] payload) {
			this.payload = payload;
		}

		byte[] toByteArray(boolean mask) throws Exception {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			int sizebytes = payload.length <= 125 ? 1 : (payload.length <= 65535 ? 2 : 8);
			byte optcode = opcode;
			byte one = (byte) (fin ? -128 : 0);
			one |= optcode;
			if (rsv1)
				one |= 0x40;
			if (rsv2)
				one |= 0x20;
			if (rsv3)
				one |= 0x10;
			output.write(one);
			byte[] payloadlengthbytes = new byte[sizebytes];
			int highest = 8 * sizebytes - 8;
			for (int i = 0; i < sizebytes; i++)
				payloadlengthbytes[i] = (byte) (payload.length >>> (highest - 8 * i));
			assert (payloadlengthbytes.length == sizebytes);

			byte maskByte = mask ? (byte) -128 : 0;
			if (sizebytes == 1) {
				output.write((byte) (payloadlengthbytes[0] | maskByte));
			} else if (sizebytes == 2) {
				output.write((byte) ((byte) 126 | maskByte));
				output.write(payloadlengthbytes);
			} else if (sizebytes == 8) {
				output.write((byte) ((byte) 127 | maskByte));
				output.write(payloadlengthbytes);
			} else {
				throw new IllegalStateException("Size representation not supported/specified");
			}
			if (mask) {
				ByteBuffer maskkey = ByteBuffer.allocate(4);
				maskkey.putInt(new Random().nextInt());
				output.write(maskkey.array());
				for (int i = 0; i < payload.length; i++)
					output.write((byte) (payload[i] ^ maskkey.get(i % 4)));
			} else {
				output.write(payload);
			}
			return output.toByteArray();
		}

		static Frame read(InputStream stream) throws Exception {
			byte b1 = (byte) stream.read();
			boolean fin = b1 >> 8 != 0;
			boolean rsv1 = (b1 & 0x40) != 0;
			boolean rsv2 = (b1 & 0x20) != 0;
			boolean rsv3 = (b1 & 0x10) != 0;
			byte b2 = (byte) stream.read();
			boolean mask = (b2 & -128) != 0;
			int payloadlength = (byte) (b2 & ~(byte) 128);
			byte opcode = (byte) (b1 & 15);
			if (!(payloadlength >= 0 && payloadlength <= 125)) {
				if (opcode == 0x9 || opcode == 0xA || opcode == 0x8)
					throw new Exception("Invalid frame: more than 125 octets");
				if (payloadlength == 126) {
					byte[] sizebytes = new byte[3];
					sizebytes[1] = (byte) stream.read();
					sizebytes[2] = (byte) stream.read();
					payloadlength = new BigInteger(sizebytes).intValue();
				} else {
					byte[] bytes = new byte[8];
					for (int i = 0; i < 8; i++)
						bytes[i] = (byte) stream.read();
					long length = new BigInteger(bytes).longValue();
					payloadlength = (int) length;
				}
			}
			byte[] payload = new byte[payloadlength];
			if (mask) {
				byte[] maskskey = new byte[4];
				stream.read(maskskey);
				for (int i = 0; i < payloadlength; i++)
					payload[i] = (byte) (stream.read() ^ maskskey[i % 4]);
			} else
				stream.read(payload);
			return new Frame(fin, rsv1, rsv2, rsv3, opcode, payload);
		}

	}

}
