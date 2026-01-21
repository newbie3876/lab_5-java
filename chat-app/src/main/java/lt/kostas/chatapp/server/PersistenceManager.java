package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lt.kostas.chatapp.Room;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PersistenceManager {
  private static final Logger LOGGER = Logger.getLogger(PersistenceManager.class.getName());

  private final String file;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public PersistenceManager(String file) {
    this.file = file;
  }

  public void save(ChatServer server) {
    Path path = Paths.get(file);
    try {
      // užtikriname, kad katalogas egzistuoja (jei parent == null, paliekam current dir)
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Nepavyko sukurti katalogo failui: " + file, e);
      return;
    }

    // Snapshot serverio duomenų (kad nekiltų problemų, kai kolekcijos keičiasi tuo pačiu metu)
    List<String> usersSnapshot = new ArrayList<>(server.users.keySet());

    List<Map<String, Object>> roomsSnapshot = new ArrayList<>();
    for (Room r : server.rooms.values()) {
      Map<String, Object> rm = new LinkedHashMap<>();
      rm.put("name", r.getName());
      rm.put("members", new ArrayList<>(r.memberUsernames()));
      roomsSnapshot.add(rm);
    }

    List<?> messagesSnapshot = new ArrayList<>(server.messages);

    // Sudarome modelį
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("users", usersSnapshot);
    model.put("rooms", roomsSnapshot);
    model.put("messages", messagesSnapshot);

    // Rašome į laikinu failą, tada pervardiname (atomic replace, jei įmanoma)
    Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
    try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      gson.toJson(model, writer);
      writer.flush();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Klaida rašant laikinu failu: " + tmp, e);
      // pašaliname tmp, jei egzistuoja
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
      }
      return;
    }
    // bandome atominiu būdu perkelti tmp į galutinį failą
    try {
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException amnse) {
      LOGGER.log(Level.WARNING, "ATOMIC_MOVE nepalaikomas, darau paprastą replace.", amnse);
      try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Nepavyko perkelti laikino failo į galutinį vietą: " + path, e);
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Nepavyko perkelti laikino failo į galutinį vietą: " + path, e);
    }
  }
}
