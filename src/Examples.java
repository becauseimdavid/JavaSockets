
public class Examples {

	public static void main(String[] args) {

		/* Socket Server */
		new SocketServer(12345, s -> new Socket(s) {

			@Override
			public void in(String input) {
				System.out.println("Message from Client: " + input);
				out("Pong");
			}

		}).start();

		/* Socket */
		new Socket("localhost", 12345) {

			@Override
			public void in(String input) {
				System.out.println("Message from Server: " + input);
			}

		}.out("Ping");

		/* WebSocket Server */
		new SocketServer(12345, s -> new WebSocket(s) {

			@Override
			public void in(String input) {
				System.out.println(input);
				out("Callback message to client.");
			}

		}).start();

	}

}
