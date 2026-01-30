package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
  private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());

  private final Socket socket;
  private final ChatServer server;
  private final Gson gson = new Gson();
  // volatile, nes prieinami iš kelių thread'ų (run() ir server.broadcast())
  private volatile PrintWriter out;
  private volatile String username;

  public ClientHandler(Socket socket, ChatServer server) {
    this.socket = socket;
    this.server = server;
  }

  @Override
  public void run() {
    // try-with-resources užtikrina, kad reader/writer užsidarys, kai išeisime iš bloko
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

      this.out = writer;

      String line;
      while ((line = in.readLine()) != null) {
        Message m;
        try {
          m = gson.fromJson(line, Message.class);
        } catch (Exception ex) {
          logger.log(Level.WARNING, "Negalima deserializuoti žinutės: " + line, ex);
          continue;
        }
        if (m == null || m.type() == null) continue;

        switch (m.type()) {
          case "register":
            this.username = m.from();
            boolean ok = server.registerClient(username, this);
            if (!ok) {
              // grąžiname klaidą klientui ir uždarome ryšį
              sendMessage(new Message("system", "server", username, null, "register-failed"));
              closeQuietly();
              return; // baigiame run() (finally bloko nereikia papildomai)
            } else {
              sendMessage(new Message("system", "server", username, null, "registered"));
            }
            break;

          case "create-room":
            String rid = m.roomId();
            if (rid == null || rid.isEmpty()) {
              rid = m.text() != null ? m.text().trim().toLowerCase().replaceAll("\\s+", "-")
                      : "room-" + System.currentTimeMillis();
            }
            server.createRoom(rid, m.text() == null ? rid : m.text(), username);
            break;

          case "message":
            if (m.to() != null && !m.to().isEmpty()) {
              server.sendPrivate(m);
            } else if (m.roomId() != null && !m.roomId().isEmpty()) {
              server.broadcastToRoom(m);
            }
            break;

          case "join-room":
            server.joinRoom(m.roomId(), username);
            break;

          default:
            // ignoruojame nežinomus tipus
        }
      }
    } catch (IOException e) {
      logger.log(Level.INFO, "IO problemos su klientu " + socket.getRemoteSocketAddress() + ": " + e.getMessage(), e);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Netikėta klaida ClientHandler'e", ex);
    } finally {
      // uždaryti socket ir atregistruoti vartotoją (jeigu reikia)
      closeQuietly();
    }
  }

  public synchronized void sendMessage(Message m) {
    try {
      if (out == null) return;
      String json = gson.toJson(m);
      out.println(json); // autoFlush = true -> println flush'ins
      if (out.checkError()) {
        logger.info("Klaida rašant klientui, atšaukiama registracija: " + username);
        closeQuietly();
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Klaida siunčiant pranešimą klientui " + username, e);
      closeQuietly();
    }
  }

  /**
   * Saugu uždaro socket'ą ir atregistruoja vartotoją (idempotentiškas).
   */
  private void closeQuietly() {
    try {
      if (!socket.isClosed()) socket.close();
    } catch (IOException e) {
      logger.log(Level.FINE, "Negalima uždaryti socket'o", e);
    }

    if (username != null) {
      try {
        server.unregisterClient(username);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Nepavyko atregistruoti kliento " + username, e);
      }
      username = null;
    }
    out = null;
  }
}
