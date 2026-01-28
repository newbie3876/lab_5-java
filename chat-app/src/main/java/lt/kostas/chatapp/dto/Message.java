package lt.kostas.chatapp.dto;

public record Message(
        String type,
        String from,
        String to,
        String roomId,
        String text,
        long timestamp
) {
  // klasės konstruktorius, kuris automatiškai nustato timestamp
  public Message(String type, String from, String to, String roomId, String text) {
    this(type, from, to, roomId, text, System.currentTimeMillis());
  }
}
