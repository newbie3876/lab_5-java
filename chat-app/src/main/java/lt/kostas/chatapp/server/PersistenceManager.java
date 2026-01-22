package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import lt.kostas.chatapp.Room;
import lt.kostas.chatapp.dto.Message;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PersistenceManager {
  private final Path path;
  private final Gson gson = new Gson();

  public PersistenceManager(String filePath) {
    this.path = Paths.get(filePath);
  }

  private synchronized void write(Map<String, Object> model) throws IOException {
    Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
    Files.createDirectories(path.getParent() == null ? Paths.get(".") : path.getParent());
    try {
      Files.write(tmp, gson.toJson(model).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void saveState(Collection<Room> rooms, Collection<String> users, Collection<Message> messages) {
    Map<String, Object> model = new HashMap<>();
    model.put("rooms", rooms);
    model.put("users", users);
    model.put("messages", messages);
    try {
      write(model);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
