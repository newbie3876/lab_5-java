package lt.kostas.chatapp.dto;

public class Message {
  public String type;
  public String from;
  public String to;
  public String roomId;
  public String text;
  public long timestamp;

  public Message() {
  }

  public Message(String type, String from, String to, String roomId, String text) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.roomId = roomId;
    this.text = text;
    this.timestamp = System.currentTimeMillis();
  }
}
