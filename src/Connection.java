import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Created by BecauseImDavid on 8/25/2021.
 */

public abstract class Connection extends Connectable {

	public Connection(Socket socket) {
		super(socket);
	}

	public Connection(String host, int port) {
		super(null);
		try {
			connect(host, port);
		} catch (Exception exc) {
			throw Server.error(exc);
		}
	}

	@Override
	public void run() {
		onOpen();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			for (String data = null; isOpen() && (data = reader.readLine()) != null;)
				in(data);
		} catch (Exception exc) {
			throw Server.error(exc);
		}
		onClose();
	}

	abstract void in(String input);

	public void out(String output) {
		if (!isOpen())
			throw Server.error(new Exception("Can't write output while Connection isn't open"));
		try {
			PrintWriter writer = new PrintWriter(socket.getOutputStream());
			writer.write(output + "\n");
			writer.flush();
		} catch (Exception exc) {
			throw Server.error(exc);
		}
	}

	public void onOpen() {
		// optional overwrite
	}

	public void onClose() {
		// optional overwrite
	}

}
