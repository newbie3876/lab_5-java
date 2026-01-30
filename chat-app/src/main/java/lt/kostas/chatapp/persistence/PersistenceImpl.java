package lt.kostas.chatapp.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.model.Room;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PersistenceImpl implements Persistence {
  private final Path path;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Logger logger = Logger.getLogger(PersistenceImpl.class.getName());

  public PersistenceImpl(String filePath) {
    this.path = Paths.get(filePath).toAbsolutePath();
  }

  private synchronized void write(Map<String, Object> model) throws IOException {
    Path dir = path.getParent() == null ? Paths.get(".").toAbsolutePath() : path.getParent();
    Files.createDirectories(dir);
    Path tmp = dir.resolve(path.getFileName().toString() + ".tmp");

    try {
      Files.writeString(tmp, gson.toJson(model), StandardCharsets.UTF_8,
              StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
      logger.fine("Duomenys išsaugoti šiame faile: " + path);
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public synchronized void saveState(Collection<Room> rooms, Collection<String> users, Collection<Message> messages) {
    Map<String, Object> model = new HashMap<>();
    model.put("rooms", rooms);
    model.put("users", users);
    model.put("messages", messages);

    try {
      write(model);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Nepavyko išsaugoti duomenų faile: " + path, e);
    }
  }
}

