package lt.kostas.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Message {
  private String type;     // CONNECT, ROOM_MSG, PRIVATE_MSG, CREATE_ROOM
  private String from;
  private String to;
  private String room;
  private String message;

  // papildomas konstruktorius Message objektų kūrimui:
  public Message(String type, String from, String room, String message) {
    this.type = type;
    this.from = from;
    this.room = room;
    this.message = message;
  }
}
