
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Socket {

	static ExecutorService executor = Executors.newCachedThreadPool();
	java.net.Socket socket;
	Thread thread;

	public Socket(java.net.Socket socket) {
		this.socket = socket;
		(thread = new Thread(() -> run())).start();
	}

	public Socket(String address, int port) {
		try {
			socket = new java.net.Socket(address, port);
			(thread = new Thread(() -> run())).start();
		} catch (Exception exc) {
			onDisconnect(exc.getLocalizedMessage());
			return;
		}
	}

	protected boolean onConnect() {
		return true;
	}

	protected void onDisconnect(String reason) {
		// optional overwrite
	}

	protected void onError(Exception exc) {
		exc.printStackTrace();
	}

	protected abstract void in(String input);

	public void out(String output) {
		if (!isOpen())
			throw new RuntimeException("Can't write output while socket isn't open");
		try {
			OutputStream outputStream = socket.getOutputStream();
			PrintWriter printWriter = new PrintWriter(outputStream);
			printWriter.write(output + "\n");
			printWriter.flush();
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}

	public boolean isOpen() {
		return (socket != null && socket.isConnected() && !socket.isClosed() && socket.isBound());
	}

	public void close() {
		try {
			socket.close();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void run() {
		try {
			socket.setSoLinger(true, 0);
			try {
				readInput(socket.getInputStream());
			} catch (Exception e) {
				onDisconnect(e.getLocalizedMessage());
				return;
			}
			onDisconnect("closed");
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	protected void readInput(InputStream stream) throws Exception {
		if (!onConnect())
			return;
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		for (String line = null; isOpen() && (line = reader.readLine()) != null;) {
			final String data = line;
			executor.execute(() -> {
				in(data);
			});
		}
	}

}
