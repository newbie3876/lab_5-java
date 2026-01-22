package lt.kostas.chatapp;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
  public String id;
  public String displayName;
  // thread-safe set of members
  public Set<String> members = ConcurrentHashMap.newKeySet();

  public Room() {
  }

  public Room(String id, String displayName) {
    this.id = id;
    this.displayName = displayName;
  }
}
