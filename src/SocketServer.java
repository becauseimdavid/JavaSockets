
import java.net.ServerSocket;

public class SocketServer {

	ServerSocket serverSocket;
	int port;
	Action onConnect;
	Thread thread;

	public SocketServer(int port, Action onConnect) {
		this.port = port;
		this.onConnect = onConnect;
	}

	protected void onStart() {
		// optional overwrite
	}

	protected void onStop(String reason) {
		// optional overwrite
	}

	public SocketServer start() {
		try {
			serverSocket = new ServerSocket(port);
			thread = new Thread(() -> run());
			thread.start();
		} catch (Exception exc) {
			onStop(exc.getLocalizedMessage());
			return this;
		}
		return this;
	}

	public void stop() {
		try {
			serverSocket.close();
		} catch (Exception exc) {
			return;
		}
	}

	private void run() {
		onStart();
		while (!serverSocket.isClosed()) {
			try {
				java.net.Socket socket = serverSocket.accept();
				onConnect.onConnect(socket);
			} catch (Exception e) {
				break;
			}
		}
		onStop("stoped");
	}

	public static interface Action {

		Socket onConnect(java.net.Socket socket);

	}

}
