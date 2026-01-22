package lt.kostas.chatapp.network;

import com.google.gson.Gson;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
  private PrintWriter out;
  private BufferedReader in;
  private final Gson gson = new Gson();

  public void connect(String host, int port, Consumer<Message> onMessage) throws IOException {
    Socket socket = new Socket(host, port);
    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    /* connection closed */
    Thread readerThread = new Thread(() -> {
      String line;
      try {
        while ((line = in.readLine()) != null) {
          Message m = gson.fromJson(line, Message.class);
          if (m != null) onMessage.accept(m);
        }
      } catch (IOException e) { /* connection closed */ }
    });
    readerThread.setDaemon(true);
    readerThread.start();
  }

  public void send(Message m) {
    if (out != null) out.println(gson.toJson(m));
  }
}
