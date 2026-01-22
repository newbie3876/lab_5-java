package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
  private final Socket socket;
  private final ChatServer server;
  private final Gson gson = new Gson();
  private PrintWriter out;
  private String username;

  public ClientHandler(Socket socket, ChatServer server) {
    this.socket = socket;
    this.server = server;
  }

  @Override
  public void run() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

      String line;
      while ((line = in.readLine()) != null) {
        Message m = gson.fromJson(line, Message.class);
        if (m == null || m.type == null) continue;
        switch (m.type) {
          case "register":
            this.username = m.from;
            boolean ok = server.registerClient(username, this);
            if (!ok) {
              // grąžiname klaidą klientui ir neleidžiame tęsti registracijos
              sendMessage(new Message("system", "server", username, null, "register-failed"));
              // optional: break connection
            } else {
              sendMessage(new Message("system", "server", username, null, "registered"));
            }
            break;
          case "create-room":
            String rid = m.roomId;
            if (rid == null || rid.isEmpty())
              rid = m.text != null ? m.text.trim().toLowerCase().replaceAll("\\s+", "-") : "room-" + System.currentTimeMillis();
            server.createRoom(rid, m.text == null ? rid : m.text, username);
            break;
          case "message":
            if (m.to != null && !m.to.isEmpty()) server.sendPrivate(m);
            else if (m.roomId != null && !m.roomId.isEmpty()) server.broadcastToRoom(m);
            break;
          case "join-room":
            server.joinRoom(m.roomId, username);
            break;
          default:
            // ignore
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      if (username != null) server.unregisterClient(username);
    }
  }

  public synchronized void sendMessage(Message m) {
    try {
      if (out == null) return;
      String json = gson.toJson(m);
      out.println(json);
      if (out.checkError()) {
        // klientas uždarytas / error -> registruojame removal
        if (username != null) server.unregisterClient(username);
      }
    } catch (Exception e) {
      e.printStackTrace();
      if (username != null) server.unregisterClient(username);
    }
  }
}
