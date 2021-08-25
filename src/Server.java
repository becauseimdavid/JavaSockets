
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Server {

	int port;
	Filter filter;
	Connector connector;

	ServerSocket socket;
	Thread thread;
	boolean running = false;

	public Server(int port) {
		this.port = port;
	}

	public Server(int port, Connector connector) {
		this.port = port;
		this.connector = connector;
	}

	public Server onConnect(Connector connector) {
		this.connector = connector;
		return this;
	}

	public Server filter(Filter filter) {
		this.filter = filter;
		return this;
	}

	public void onStart() {
		// optional overwrite
	}

	public void onStop() {
		// optional overwrite
	}

	public Server start() {
		try {
			if (running)
				throw error(new Exception("SocketServer already running"));
			socket = new ServerSocket(port);
			(thread = new Thread(() -> run())).start();
		} catch (Exception exc) {
			throw error(exc);
		}
		return this;
	}

	public void run() {
		if (connector == null)
			throw error(new NullPointerException("SocketServer can't run without Connector"));
		running = true;
		onStart();
		while (!socket.isClosed()) {
			try {
				Socket pending = socket.accept();
				if (filter == null || filter
						.isAllowed(((InetSocketAddress) pending.getRemoteSocketAddress()).getAddress().getHostAddress()))
					connector.onConnect(pending);
				else
					pending.close();
			} catch (Exception exc) {
				exc.printStackTrace();
			}
		}
		running = false;
		onStop();
	}

	public Server stop() {
		try {
			socket.close();
		} catch (Exception exc) {
			throw error(exc);
		}
		return this;
	}

	public static interface Filter {
		boolean isAllowed(String address);
	}

	public static interface Connector {
		Connectable onConnect(Socket socket);
	}

	static RuntimeException error(Exception exc) {
		return (RuntimeException) new RuntimeException(exc) {

			private static final long serialVersionUID = 1L;

			@Override
			public String toString() {
				String msg = exc.getClass().equals(Exception.class) ? "" : exc.getClass().getName();
				if (exc.getLocalizedMessage() != null)
					msg += (msg == "" ? "/ " : ": ") + exc.getLocalizedMessage();
				return msg;
			}

			@Override
			public synchronized Throwable getCause() {
				return exc.getCause();
			}

			@Override
			public synchronized Throwable fillInStackTrace() {
				super.fillInStackTrace();
				StackTraceElement[] trace = super.getStackTrace();
				super.setStackTrace(Arrays.copyOfRange(trace, 1, trace.length));
				return this;
			}

		}.fillInStackTrace();
	}

}
