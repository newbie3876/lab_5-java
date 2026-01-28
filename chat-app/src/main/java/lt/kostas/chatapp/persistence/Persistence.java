package lt.kostas.chatapp.persistence;

import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.model.Room;

import java.util.Collection;

public interface Persistence {
  void saveState(Collection<Room> rooms, Collection<String> users, Collection<Message> messages);
}
