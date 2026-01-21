package lt.kostas.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PersistenceManager {
  private final String file;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public PersistenceManager(String file) {
    this.file = file;
  }

  public void save(ChatServer server) {
    // Bendras persistavimo modelis
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("users", new ArrayList<>(server.users.keySet())); // vartotojų usernames

    // pokalbių kambariai ir jų nariai
    List<Map<String, Object>> rooms = new ArrayList<>();
    for (Room r : server.rooms.values()) {
      Map<String, Object> rm = new LinkedHashMap<>();
      rm.put("name", r.getName());
      rm.put("members", new ArrayList<>(r.memberUsernames()));
      rooms.add(rm);
    }
    model.put("rooms", rooms);
    model.put("messages", server.messages); // žinučių saugojimas

    try (FileWriter fw = new FileWriter(file)) {
      gson.toJson(model, fw);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}