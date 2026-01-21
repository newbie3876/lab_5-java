package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lt.kostas.chatapp.Room;
import lt.kostas.chatapp.dto.Message;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
  private final Socket socket;
  private final ChatServer server;
  private PrintWriter out;
  private BufferedReader in;
  @Getter
  private String username;
  private final Gson gson = new Gson();

  public ClientHandler(Socket socket, ChatServer server) {
    this.socket = socket;
    this.server = server;
  }

  /**
   * Synchronous send — patikrina socket būseną ir rašo JSON.
   */
  public synchronized void send(Message msg) {
    if (out == null) return;
    // papildoma patikra socket būsenai
    if (socket == null || socket.isClosed()) return;
    try {
      out.println(gson.toJson(msg));
      out.flush();
    } catch (Exception e) {
      // jeigu rašymas nepavyksta - pranešame (neužmušame gijos)
      System.err.println("Klaida siuntant žinutę vartotojui " + username + ": " + e.getMessage());
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
      Message connectMsg;
      try {
        connectMsg = gson.fromJson(line, Message.class);
      } catch (JsonSyntaxException je) {
        // blogas pirmasis pranešimas - uždarome
        send(new Message("ERROR", "server", null, "Invalid CONNECT message"));
        return;
      }

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
      // optionally broadcast join info to the room
      general.broadcast(new Message("INFO", "server", general.getName(), username + " prisijungė."));
      send(new Message("INFO", "server", null, "Connected as " + username));


      while ((line = in.readLine()) != null) {
        Message msg;
        try {
          msg = gson.fromJson(line, Message.class);
        } catch (JsonSyntaxException je) {
          // pranešame vartotojui apie blogą JSON ir tęsiame
          send(new Message("ERROR", "server", null, "Invalid message format"));
          continue;
        }

        if (msg == null || msg.getType() == null) continue;

        switch (msg.getType()) {
          case "CREATE_ROOM" -> {
            server.getOrCreateRoom(msg.getRoom());
          }

          case "JOIN_ROOM" -> {
            Room r = server.getOrCreateRoom(msg.getRoom());
            r.join(this);
            r.broadcast(new Message("INFO", "server", r.getName(), username + " prisijungė į kambarį."));
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
            // graceful disconnect: išeinam iš ciklo
            return;
          }

          default -> {
            // unknown type - gal pranešti arba ignoruoti
            send(new Message("ERROR", "server", null, "Unknown message type: " + msg.getType()));
          }
        }
      }
    } catch (IOException ex) {
      System.err.println("ClientHandler (" + username + ") IO klaida: " + ex.getMessage());
    } finally {
      // cleanup
      try {
        // broadcast leave to rooms
        if (username != null) {
          // pašaliname vartotoją iš serverio (tai pašalins ir iš kambarių)
          server.unregisterUser(username);
        }
      } catch (Exception ignored) {
      }
      // uždarom srautus
      try {
        if (in != null) in.close();
      } catch (IOException ignored) {
      }
      try {
        if (out != null) out.close();
      } catch (Exception ignored) {
      }
      try {
        if (socket != null && !socket.isClosed()) socket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
