package lt.kostas.chatapp.server;

import lt.kostas.chatapp.Room;
import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.enums.ChatRoomType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ChatServer {
  public static final int PORT = 5555;
  private final PersistenceManager persistence;
  private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());

  final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
  final ConcurrentMap<String, ClientHandler> users = new ConcurrentHashMap<>();
  final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

  // Executor and serverSocket as fields so we can control lifecycle from stop()
  private final ExecutorService pool = Executors.newCachedThreadPool();
  private ServerSocket serverSocket;
  private volatile boolean running = false;

  public ChatServer(String persistFile) {
    this.persistence = new PersistenceManager(persistFile);
  }

  public void start() throws IOException {
    // Ensure default rooms exist
    for (ChatRoomType type : ChatRoomType.values()) {
      rooms.putIfAbsent(type.getId(), new Room(type.getId()));
    }

    serverSocket = new ServerSocket(PORT);
    running = true;
    System.out.println("Server'is paleistas šiame port'e: " + PORT);

    try {
      while (running && !serverSocket.isClosed()) {
        try {
          Socket socket = serverSocket.accept();
          pool.execute(new ClientHandler(socket, this));
        } catch (IOException e) {
          // If running was set to false and socket was closed, accept() may throw — ignore in that case
          if (running) {
            LOGGER.log(Level.SEVERE, "Klaida priimant ryšį", e);
          }
        }
      }
    } finally {
      // ensure resources are released if start() exits
      stop();
    }
  }

  /**
   * Gracefully stop the server: stop accepting, close server socket, shutdown pool and persist data.
   */
  public synchronized void stop() {
    if (!running && (serverSocket == null || serverSocket.isClosed())) {
      return; // already stopped
    }

    running = false;

    // close server socket to unblock accept()
    if (serverSocket != null && !serverSocket.isClosed()) {
      try {
        serverSocket.close();
      } catch (IOException e) {
        System.err.println("Klaida uždarant ServerSocket: " + e.getMessage());
      }
    }

    // shutdown executor service gracefully
    pool.shutdown();
    try {
      if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
        // force shutdown if not terminated in time
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          System.err.println("ExecutorService nepavyko užbaigti po priverstinio shutdown.");
        }
      }
    } catch (InterruptedException ie) {
      // re-interrupt thread and force shutdown
      Thread.currentThread().interrupt();
      pool.shutdownNow();
    }
    // persist data
    try {
      persist();
    } catch (Exception e) {
      System.err.println("Klaida išsaugant duomenis: " + e.getMessage());
    }
    System.out.println("Server'is sustojo.");
  }

  // pagalbiniai metodai skirti ClientHandler klasei
  public boolean registerUser(String username, ClientHandler handler) {
    return users.putIfAbsent(username, handler) == null;
  }

  public void unregisterUser(String username) {
    ClientHandler ch = users.remove(username);
    if (ch == null) return; // nothing to remove
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

  public synchronized void persist() {
    persistence.save(this);
  }

  public static void main(String[] args) throws IOException {
    Path dataFile = Paths.get("data", "chat-data.json");
    Files.createDirectories(dataFile.getParent());

    ChatServer server = new ChatServer(dataFile.toString());

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Užklausimas sustabdyti serverį — pradedame shutdown...");
      server.stop();
    }));
    server.start();
  }
}
