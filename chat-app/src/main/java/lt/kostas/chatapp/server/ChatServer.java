package lt.kostas.chatapp.server;

import lt.kostas.chatapp.Room;
import lt.kostas.chatapp.dto.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
  private final int port;
  private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();
  private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
  private final PersistenceManager persistence;

  public ChatServer(int port, String storageFile) {
    this.port = port;
    this.persistence = new PersistenceManager(storageFile);
    // DEFAULT ROOM
    Room general = new Room("general", "General");
    rooms.put("general", general);
  }

  public void start() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Server'is dirba šiame port'e: " + port);
      while (!serverSocket.isClosed()) {
        Socket socket = serverSocket.accept(); // jei serverSocket uždaromas kitur, accept() mesti SocketException ir loop baigiasi
        ClientHandler handler = new ClientHandler(socket, this);
        new Thread(handler).start();
      }
    }
  }

  // ChatServer.java
  public boolean registerClient(String username, ClientHandler handler) {
    if (username == null || username.isBlank()) return false;

    ClientHandler existing = clients.putIfAbsent(username, handler);
    if (existing == null) {
      // sėkmingai užregistruotas
      persist();
      System.out.println("Priregistruotas vartotojas: " + username);
      return true;
    } else if (existing == handler) {
      // jau užregistruotas su tuo pačiu handler (idempotentiška)
      return true;
    } else {
      // vardas užimtas kitoje sesijoje
      System.out.println("Registracija nepavyko: '" + username + "' - toks vartotojas jau egzistuoja.");
      return false;
    }
  }

  public void unregisterClient(String username) {
    clients.remove(username);
    // remove from rooms
    for (Room r : rooms.values()) r.members.remove(username);
    persist();
  }

  public void createRoom(String roomId, String displayName, String creator) {
    rooms.computeIfAbsent(roomId, id -> {
      Room r = new Room(id, displayName);
      if (creator != null) r.members.add(creator);
      persist();
      return r;
    });
  }

  public void broadcastToRoom(Message msg) {
    messages.add(msg);
    Room r = rooms.get(msg.roomId);
    if (r == null) return;

    // Auto-join: jeigu siuntėjas dar nėra kambaryje, pridedame jį
    if (msg.from != null) r.members.add(msg.from);

    for (String user : r.members) {
      ClientHandler ch = clients.get(user);
      if (ch != null) ch.sendMessage(msg);
    }
    persist();
  }

  public void sendPrivate(Message msg) {
    messages.add(msg);
    if (msg.to == null) return;
    ClientHandler ch = clients.get(msg.to);
    if (ch != null) ch.sendMessage(msg);
    // also send copy to sender if present
    ClientHandler sender = clients.get(msg.from);
    if (sender != null && sender != ch) sender.sendMessage(msg);
    persist();
  }

  private void persist() {
    // Sukuriame nekintamas 'snapshot' kolekcijas, kad persistence.saveState
    // negautų dalinai pakeistų concurrent kolekcijų.
    Collection<Room> roomsSnapshot = new ArrayList<>(rooms.values());
    Set<String> usersSnapshot = new HashSet<>(clients.keySet());

    List<Message> messagesSnapshot;
    // 'messages' yra Collections.synchronizedList — sinchronizuotai nukopijuojame
    synchronized (messages) {
      messagesSnapshot = new ArrayList<>(messages);
    }
    // Iškviečiame persistence su saugiais snapshot'ais
    persistence.saveState(roomsSnapshot, usersSnapshot, messagesSnapshot);
  }

  public void joinRoom(String roomId, String username) {
    Room r = rooms.get(roomId);
    if (r != null) {
      r.members.add(username);
      persist();
    }
  }

  public static void main(String[] args) throws IOException {
    int port = 55555;
    String file = "data/chat-data.json";
    ChatServer server = new ChatServer(port, file);
    server.start();
  }
}
