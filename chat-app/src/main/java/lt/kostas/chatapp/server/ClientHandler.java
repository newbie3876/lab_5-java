package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
  private final Socket socket;
  private final ChatServer server;
  private PrintWriter out;
  private BufferedReader in;
  private String username;
  private final Gson gson = new Gson();

  public ClientHandler(Socket socket, ChatServer server) {
    this.socket = socket;
    this.server = server;
  }

  public String getUsername() {
    return username;
  }

  public void send(Message msg) {
    if (out != null) {
      out.println(gson.toJson(msg));
      out.flush();
    }
  }

  @Override
  public void run() {
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

      // expect first message to be CONNECT with username
      String line = in.readLine();
      if (line == null) return;
      Message connectMsg = gson.fromJson(line, Message.class);
      if (!"CONNECT".equals(connectMsg.getType()) || connectMsg.getFrom() == null) {
        socket.close();
        return;
      }

      this.username = connectMsg.getFrom();
      boolean ok = server.registerUser(username, this);
      if (!ok) {
        send(new Message("ERROR", "server", null, "Username already in use"));
        socket.close();
        return;
      }

      // join general by default
      Room general = server.getOrCreateRoom("general");
      general.join(this);
      send(new Message("INFO", "server", null, "Connected as " + username));

      // read loop
      while ((line = in.readLine()) != null) {
        Message msg = gson.fromJson(line, Message.class);
        if (msg == null || msg.getType() == null) continue;

        switch (msg.getType()) {
          case "CREATE_ROOM" -> {
            server.getOrCreateRoom(msg.getRoom());
          }

          case "JOIN_ROOM" -> {
            Room r = server.getOrCreateRoom(msg.getRoom());
            r.join(this);
          }

          case "ROOM_MSG" -> {
            Room room = server.getRoom(msg.getRoom());
            if (room != null) {
              room.broadcast(msg);
              server.messages.add(msg); // išsaugome žinutę
            } else {
              send(new Message("ERROR", "server", null, "Room not found"));
            }
          }

          case "PRIVATE_MSG" -> {
            ClientHandler target = server.getUser(msg.getTo());
            if (target != null) {
              target.send(msg);
              server.messages.add(msg); // išsaugome žinutę
            } else {
              send(new Message("ERROR", "server", null, "User not found"));
            }
          }

          case "DISCONNECT" -> {
            // graceful disconnect
            in.close();
            out.close();
            socket.close();
          }
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      // cleanup
      if (username != null) {
        server.unregisterUser(username);
      }
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
