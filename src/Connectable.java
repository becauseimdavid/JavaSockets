import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class Connectable {

	Socket socket;
	Thread thread;

	protected Connectable(Socket socket) {
		if (socket == null)
			return;
		try {
			(this.socket = socket).setSoLinger(true, 1000);
			(thread = new Thread(() -> run())).start();
		} catch (Exception exc) {
			throw Server.error(exc);
		}
	}

	void connect(String host, int port) throws Exception {
		(socket = new Socket(host, port)).setSoLinger(true, 1000);
		(thread = new Thread(() -> run())).start();
	}

	abstract void run();

	public boolean isOpen() {
		return (socket.isConnected() && !socket.isClosed() && socket.isBound());
	}

	public String getAddress() {
		return ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();
	}

	public void close() {
		try {
			this.socket.close();
		} catch (Exception exc) {
			throw Server.error(exc);
		}
	}

}