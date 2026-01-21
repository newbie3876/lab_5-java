package lt.kostas.chatapp.server;

import lt.kostas.chatapp.dto.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
  public static final int PORT = 5555;
  private final PersistenceManager persistence;

  final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
  final ConcurrentMap<String, ClientHandler> users = new ConcurrentHashMap<>();
  final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

  public ChatServer(String persistFile) {
    this.persistence = new PersistenceManager(persistFile);
  }

  public void start() throws IOException {
    // load rooms/users if needed (optional)
    rooms.putIfAbsent("general", new Room("general"));

    ExecutorService pool = Executors.newCachedThreadPool();
    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      System.out.println("Server started on port " + PORT);
      while (true) {
        Socket socket = serverSocket.accept();
        pool.execute(new ClientHandler(socket, this));
      }
    } finally {
      pool.shutdown();
    }
  }

  // helpers used by ClientHandler
  public boolean registerUser(String username, ClientHandler handler) {
    return users.putIfAbsent(username, handler) == null;
  }

  public void unregisterUser(String username) {
    ClientHandler ch = users.remove(username);
    for (Room r : rooms.values()) {
      r.leave(ch);
    }
  }

  public Room getOrCreateRoom(String name) {
    rooms.putIfAbsent(name, new Room(name));
    return rooms.get(name);
  }

  public Room getRoom(String name) {
    return rooms.get(name);
  }

  public ClientHandler getUser(String username) {
    return users.get(username);
  }

  public void persist() {
    persistence.save(this);
  }

  // main
  public static void main(String[] args) throws IOException {
    ChatServer server = new ChatServer("chatdata.json");

    // Įterpiame shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Saving data before exit...");
      server.persist();
    }));
    server.start();
  }
}
