package lt.kostas.chatapp.enums;

import lombok.Getter;

@Getter
public enum ChatRoomType {
  GENERAL("general", "Bendras"),
  SUPPORT("support", "Palaikymas"),
  RANDOM("random", "Laisva tema");

  private final String id;        // siunčiama per tinklą
  private final String displayLt; // rodoma vartotojui

  ChatRoomType(String id, String displayLt) {
    this.id = id;
    this.displayLt = displayLt;
  }

  public static ChatRoomType fromId(String id) {
    if (id == null) return ChatRoomType.GENERAL;
    for (ChatRoomType room : values()) {
      if (room.id.equalsIgnoreCase(id)) return room;
    }
    return ChatRoomType.GENERAL; // fallback
  }
}
