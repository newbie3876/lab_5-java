package lt.kostas.chatapp.model;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
  private final String id;
  private final String displayName;
  private final Set<String> members = ConcurrentHashMap.newKeySet();

  public Room(String id, String displayName) {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Kambario ID(pavadinimas) negali būti tuščias");
    this.id = id;
    this.displayName = displayName == null ? id : displayName;
  }

  public void addMember(String username) {
    if (username == null || username.isBlank()) return;
    members.add(username);
  }

  public void removeMember(String username) {
    if (username == null || username.isBlank()) return;
    members.remove(username);
  }

  /**
   * Grąžina saugią kopiją iteracijoms.
   */
  public Set<String> getMembersSnapshot() {
    return new HashSet<>(members);
  }
}

