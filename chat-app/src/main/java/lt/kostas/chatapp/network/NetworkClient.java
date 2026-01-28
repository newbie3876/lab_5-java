package lt.kostas.chatapp.network;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class NetworkClient {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;
  private Thread readerThread;
  private final Gson gson = new Gson();

  public void connect(String host, int port, Consumer<Message> onMessage) throws IOException {
    this.socket = new Socket(host, port);

    out = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
            true
    );
    in = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
    );

    readerThread = new Thread(() -> {
      String line;
      try {
        while ((line = in.readLine()) != null) {
          try {
            Message m = gson.fromJson(line, Message.class);
            if (m != null) {
              Platform.runLater(() -> onMessage.accept(m));
            }
          } catch (JsonSyntaxException jse) {
            System.err.println("Netinkamas JSON iš serverio: " + jse.getMessage());
          }
        }
      } catch (IOException e) {
        System.out.println("Reader thread baigė darbą (ryšys uždarytas): " + e.getMessage());
      } finally {
        close(); // uždarom resursus, jei readLine() baigiasi
      }
    }, "NetworkClient-Reader");
    readerThread.setDaemon(true);
    readerThread.start();
  }

  public synchronized void send(Message m) {
    if (out == null) return;
    out.println(gson.toJson(m));
    if (out.checkError()) {
      System.err.println("Klaida siunčiant žinutę į serverį");
    }
  }

  public synchronized void close() {
    try {
      if (socket != null && !socket.isClosed()) socket.close();
    } catch (IOException ignored) {
    }

    try {
      if (in != null) in.close();
    } catch (IOException ignored) {
    }

    if (out != null) {
      out.close();
    }

    if (readerThread != null && !readerThread.isInterrupted()) {
      readerThread.interrupt();
    }

    socket = null;
    in = null;
    out = null;
    readerThread = null;
  }
}
