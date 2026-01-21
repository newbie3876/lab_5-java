package lt.kostas.chatapp.network;

import com.google.gson.Gson;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;
  private final Gson gson = new Gson();
  private Consumer<Message> onMessage;

  public NetworkClient() {
    // tuščias konstruktorius – OK
  }

  public void connect(String host, int port, String username, Consumer<Message> onMessage)
          throws IOException {

    this.onMessage = onMessage;
    this.socket = new Socket(host, port);
    this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    send(new Message("CONNECT", username, null, null));
    startListener(username);
  }

  private void startListener(String username) {
    Thread t = new Thread(() -> {
      try {
        String line;
        while ((line = in.readLine()) != null) {
          Message m = gson.fromJson(line, Message.class);
          if (m != null && onMessage != null) {
            onMessage.accept(m);
          }
        }
      } catch (IOException e) {
        System.err.println("Disconnected from server");
      }
    }, "client-listener-" + username);

    t.setDaemon(true);
    t.start();
  }

  public void send(Message msg) {
    if (socket != null && !socket.isClosed() && out != null) {
      out.println(gson.toJson(msg));
      out.flush();
    }
  }

  public void close() {
    try {
      if (in != null) in.close();
      if (out != null) out.close();
      if (socket != null) socket.close();
    } catch (IOException ignored) {
    }
  }
}

