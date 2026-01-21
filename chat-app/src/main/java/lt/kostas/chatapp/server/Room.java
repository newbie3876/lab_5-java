package lt.kostas.chatapp.server;

import lt.kostas.chatapp.dto.Message;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class Room {
  private final String name;
  private final Set<ClientHandler> clients = new CopyOnWriteArraySet<>();

  public Room(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void join(ClientHandler client) {
    clients.add(client);
  }

  public void leave(ClientHandler client) {
    clients.remove(client);
  }

  public void broadcast(Message msg) {
    for (ClientHandler c : clients) {
      c.send(msg);
    }
  }

  public Set<String> memberUsernames() {
    return clients.stream()
            .map(ClientHandler::getUsername)
            .filter(u -> u != null)
            .collect(Collectors.toSet());
  }
}
