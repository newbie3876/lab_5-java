package lt.kostas.chatapp.network;

import com.google.gson.Gson;
import lombok.NoArgsConstructor;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

@NoArgsConstructor
public class NetworkClient {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;
  private final Gson gson = new Gson();
  private Consumer<Message> onMessage;
  private Thread listenerThread;
  private volatile boolean running = false;

  public void connect(String host, int port, String username, Consumer<Message> onMessage) throws IOException {
    this.onMessage = onMessage;
    this.socket = new Socket(host, port);
    this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.running = true;

    // Pirmas pranešimas - prisijungimas
    send(new Message("CONNECT", username, null, null));
    startListener(username);
  }

  private void startListener(String username) {
    listenerThread = new Thread(() -> {
      try {
        String line;
        while (running && (line = in.readLine()) != null) {
          Message m = gson.fromJson(line, Message.class);
          if (m != null && onMessage != null) {
            onMessage.accept(m);
          }
        }
      } catch (IOException e) {
        // pranešti UI apie atsijungimą, jei onMessage yra nustatytas
        if (onMessage != null) {
          onMessage.accept(new Message("SYSTEM", "server", null, "Atsijungta nuo serverio."));
        }
      } finally {
        running = false;
        // uždaryti resursus, jei dar neuždaryti
        safeCloseStreams();
      }
    }, "client-listener-" + username);

    listenerThread.setDaemon(true);
    listenerThread.start();
  }

  public synchronized void send(Message msg) {
    if (socket != null && !socket.isClosed() && out != null) {
      out.println(gson.toJson(msg));
      out.flush();
    } else {
      throw new IllegalStateException("Not connected");
    }
  }

  public synchronized void close() {
    running = false;
    safeCloseStreams();
    if (listenerThread != null) {
      listenerThread.interrupt();
      listenerThread = null;
    }
    try {
      if (socket != null && !socket.isClosed()) socket.close();
    } catch (IOException ignored) {
    }
    socket = null;
    onMessage = null;
  }

  private void safeCloseStreams() {
    try {
      if (out != null) out.close();
    } catch (Exception ignored) {
    }
    try {
      if (in != null) in.close();
    } catch (Exception ignored) {
    }
  }
}
