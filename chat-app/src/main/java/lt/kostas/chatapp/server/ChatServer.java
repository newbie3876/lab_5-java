package lt.kostas.chatapp.server;

import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.model.Room;
import lt.kostas.chatapp.persistence.PersistenceImpl;

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
  private final PersistenceImpl persistence;

  public ChatServer(int port, String storageFile) {
    this.port = port;
    this.persistence = new PersistenceImpl(storageFile);
    // numatytasis kambarys
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

  public boolean registerClient(String username, ClientHandler handler) {
    if (username == null || username.isBlank()) return false;

    ClientHandler existing = clients.putIfAbsent(username, handler);
    if (existing == null) {
      // sėkminga registracija
      Room general = rooms.get("general");
      if (general != null) general.addMember(username);
      persist();

      // naujam klientui - atsiųsti visus jau prisijungusius vartotojus (kaip user-joined)
      for (String user : clients.keySet()) {
        if (!user.equals(username)) {
          Message already = new Message("user-joined", "server", null, null, user);
          handler.sendMessage(already);
        }
      }
      // visiems kitiems pranešti, kad prisijungė naujas vartotojas
      Message joined = new Message("user-joined", "server", null, null, username);
      for (ClientHandler ch : clients.values()) {
        if (ch != handler) {
          ch.sendMessage(joined);
        }
      }
      // System.out.println("Priregistruotas vartotojas: " + username);
      return true;
    } else if (existing == handler) {
      return true;
    } else {
      System.out.println("Registracija nepavyko: '" + username + "' - toks vartotojas jau egzistuoja.");
      return false;
    }
  }

  public void unregisterClient(String username) {
    clients.remove(username);
    // pašalina narius iš kambarių:
    for (Room r : rooms.values()) {
      r.removeMember(username);
    }
    // pranešti kitiems klientams, kad vartotojas išėjo
    Message left = new Message("user-left", "server", null, null, username);
    for (ClientHandler ch : clients.values()) {
      ch.sendMessage(left);
    }
    persist();
  }

  public void createRoom(String roomId, String displayName, String creator) {
    // Bandome atomiškai įdėti naują kambarį
    Room existing = rooms.putIfAbsent(roomId, new Room(roomId, displayName));
    if (existing == null) {
      // kambarį sukūrėme
      Room r = rooms.get(roomId);
      if (creator != null) r.addMember(creator);
      persist();
      // pranešame visiems klientams, kad sukurtas naujas kambarys
      Message roomMsg = new Message("room-created", "server", null, roomId, displayName);
      for (ClientHandler ch : clients.values()) {
        ch.sendMessage(roomMsg);
      }
      System.out.println("Kambarys sukurtas ir pranešta klientams: " + roomId);
    } else {
      // jeigu toks kambarys jau egzistuoja, tuomet nieko nedarome
      System.out.println("Kambarys jau egzistuoja: " + roomId);
    }
  }

  public void broadcastToRoom(Message msg) {
    System.out.println("broadcastToRoom called. msg=" + msg);
    messages.add(msg);
    Room r = rooms.get(msg.roomId());
    if (r == null) {
      System.out.println("  kambario '" + msg.roomId() + "' nėra");
      return;
    }

    System.out.println("  room members = " + r.getMembersSnapshot());
    for (String user : r.getMembersSnapshot()) {
      ClientHandler ch = clients.get(user);
      System.out.println("    trying to send to '" + user + "' -> handler=" + ch);
      if (ch != null) ch.sendMessage(msg);
    }
    persist();
  }

  public void sendPrivate(Message msg) {
    messages.add(msg);
    if (msg.to() == null || msg.to().isBlank()) return;
    ClientHandler ch = clients.get(msg.to());
    if (ch != null) ch.sendMessage(msg);
    // also send copy to sender if present
    ClientHandler sender = clients.get(msg.from());
    if (sender != null && sender != ch) sender.sendMessage(msg);
    persist();
  }

  private void persist() {
    // Sukuriame nekintamas 'snapshot' kolekcijas, kad persistence.saveState
    // negautų dalinai pakeistų concurrent kolekcijų.
    Collection<Room> roomsSnapshot = new ArrayList<>(rooms.values());
    Set<String> usersSnapshot = new HashSet<>(clients.keySet());

    List<Message> messagesSnapshot;
    // 'messages' yra Collections.synchronizedList - sinchronizuotai nukopijuojame
    synchronized (messages) {
      messagesSnapshot = new ArrayList<>(messages);
    }
    // Iškviečiame persistence su saugiais snapshot'ais
    persistence.saveState(roomsSnapshot, usersSnapshot, messagesSnapshot);
  }

  public void joinRoom(String roomId, String username) {
    Room r = rooms.get(roomId);
    if (r != null) {
      r.addMember(username);
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
