package lt.kostas.chatapp.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.enums.ChatRoomType;
import lt.kostas.chatapp.network.NetworkClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChatController {
  @FXML
  private ListView<String> chatList;
  @FXML
  private TextArea inputField;
  @FXML
  private ComboBox<String> roomSelector; // rodomi display pavadinimai
  @FXML
  private TextField newRoomField;
  @FXML
  private TextField recipientField;
  @FXML
  private CheckBox privateCheckbox;

  private String username;
  private volatile NetworkClient client;
  private final Map<String, ObservableList<String>> roomMessages = new HashMap<>(); // key = roomId
  private final Map<String, String> displayToId = new HashMap<>();
  private final Map<String, String> idToDisplay = new HashMap<>();
  private final ObservableList<String> roomDisplays = FXCollections.observableArrayList();

  private static final ChatRoomType DEFAULT_ROOM = ChatRoomType.GENERAL;

  @FXML
  public void initialize() {
    username = "Vartotojas" + (int) (Math.random() * 1000);

    // Initialize mappings & UI from enum defaults
    for (ChatRoomType rt : ChatRoomType.values()) {
      String id = rt.getId();
      String display = rt.getDisplayLt();
      idToDisplay.put(id, display);
      displayToId.put(display, id);
      if (!roomDisplays.contains(display)) roomDisplays.add(display);
      roomMessages.putIfAbsent(id, FXCollections.observableArrayList());
    }

    roomSelector.setItems(roomDisplays);
    String defaultDisplay = idToDisplay.getOrDefault(DEFAULT_ROOM.getId(), DEFAULT_ROOM.getId());
    roomSelector.setValue(defaultDisplay);
    chatList.setItems(roomMessages.get(DEFAULT_ROOM.getId()));

    // show localized names in dropdown (we already store displays)
    roomSelector.setCellFactory(cb -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item);
      }
    });
    roomSelector.setButtonCell(roomSelector.getCellFactory().call(null));

    // when user changes selected room -> set chat view and notify server (JOIN_ROOM)
    roomSelector.valueProperty().addListener((obs, oldDisplay, newDisplay) -> {
      if (newDisplay == null) return;
      String roomId = displayToId.get(newDisplay);
      if (roomId == null) {
        // dynamic room that client only knows by display -> derive id
        roomId = toIdFromDisplay(newDisplay);
        displayToId.put(newDisplay, roomId);
        idToDisplay.put(roomId, newDisplay);
      }
      roomMessages.putIfAbsent(roomId, FXCollections.observableArrayList());
      chatList.setItems(roomMessages.get(roomId));

      // inform server (if connected) that we joined this room
      if (client != null) sendJoinRoom(roomId);
    });
    connectAsync();
  }

  @FXML
  private void onSend() {
    if (client == null) {
      chatList.getItems().add("[SISTEMA] Neprisijungta prie serverio.");
      return;
    }

    String text = inputField.getText();
    if (text == null || (text = text.trim()).isEmpty()) return;

    try {
      if (privateCheckbox != null && privateCheckbox.isSelected()) {
        String toUser = recipientField == null ? null : recipientField.getText();
        if (toUser == null || toUser.isBlank()) {
          chatList.getItems().add("[SISTEMA] Įveskite gavėjo vardą privačiai žinutei.");
          return;
        }
        Message pm = Message.builder()
                .type("PRIVATE_MSG")
                .from(username)
                .to(toUser.trim())
                .message(text)
                .build();
        client.send(pm);
        // show local confirmation for sender
        chatList.getItems().add("[PRIVATI] sau -> " + toUser.trim() + ": " + text);
      } else {
        String selectedDisplay = roomSelector == null ? null : roomSelector.getValue();
        String roomId = selectedDisplay == null
                ? DEFAULT_ROOM.getId()
                : displayToId.getOrDefault(selectedDisplay, toIdFromDisplay(selectedDisplay));

        // ensure there's a roomMessages list
        roomMessages.putIfAbsent(roomId, FXCollections.observableArrayList());

        Message rm = Message.builder()
                .type("ROOM_MSG")
                .from(username)
                .room(roomId)
                .message(text)
                .build();
        client.send(rm);

        // optionally show the sent message locally in the same room's list
        roomMessages.get(roomId).add(username + " (aš): " + text);
      }
      inputField.clear();
    } catch (Exception ex) {
      final String err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
      Platform.runLater(() -> chatList.getItems().add("[SISTEMA] Klaida siunčiant: " + err));
    }
  }

  @FXML
  private void onCreateRoom() {
    if (client == null) {
      chatList.getItems().add("[SISTEMA] Negalima sukurti kambario: nėra prisijungimo.");
      return;
    }

    String display = newRoomField.getText();
    if (display == null || display.isBlank()) return;
    display = display.trim();

    String id = toIdFromDisplay(display);
    // add to local maps and UI if not exists
    if (!displayToId.containsKey(display)) {
      displayToId.put(display, id);
      idToDisplay.put(id, display);
      roomDisplays.add(display);
    }
    roomMessages.putIfAbsent(id, FXCollections.observableArrayList());

    // send CREATE_ROOM to server
    client.send(Message.builder()
            .type("CREATE_ROOM")
            .from(username)
            .room(id)
            .message(display) // send display optionally
            .build());

    // select and auto-join
    roomSelector.getSelectionModel().select(display);
    sendJoinRoom(id);

    chatList.getItems().add("[SISTEMA] Kambarys sukurtas: " + display + " (" + id + ")");
    newRoomField.clear();
  }

  private void connectAsync() {
    Thread t = new Thread(() -> {
      try {
        client = new NetworkClient();
        client.connect(
                "localhost",
                5555,
                username,
                msg -> Platform.runLater(() -> {
                  if (msg == null || msg.getType() == null) return;
                  switch (msg.getType()) {

                    case "ROOM_MSG" -> {
                      String roomId = msg.getRoom();
                      if (roomId == null) return;
                      roomMessages.putIfAbsent(roomId, FXCollections.observableArrayList());
                      String display = idToDisplay.getOrDefault(roomId, roomId);
                      // add message to that room's list
                      roomMessages.get(roomId).add(msg.getFrom() + ": " + msg.getMessage());
                      // if this room is currently selected, ensure chatList shows it (listener handles it)
                    }
                    case "PRIVATE_MSG" -> {
                      // show private to recipient (if this client is recipient) or as info
                      String from = msg.getFrom();
                      String body = msg.getMessage();
                      chatList.getItems().add("[PRIVATI] " + from + ": " + body);
                    }
                    case "INFO" -> {
                      chatList.getItems().add("[INFO] " + msg.getMessage());
                    }
                    case "ROOM_CREATED" -> {
                      // Server announces a room (room id in msg.room, optional display in msg.message)
                      String announcedId = msg.getRoom();
                      String announcedDisplay = msg.getMessage(); // optional
                      onServerAnnouncedRoom(announcedId, announcedDisplay);
                    }
                    case "ROOM_LIST" -> {
                      // flexible parsing: msg.message may be comma-separated list: "id1:idDisplay1,id2:idDisplay2"
                      String payload = msg.getMessage();
                      if (payload != null && !payload.isBlank()) {
                        String[] parts = payload.split(",");
                        for (String p : parts) {
                          p = p.trim();
                          if (p.isEmpty()) continue;
                          if (p.contains(":")) {
                            String[] kv = p.split(":", 2);
                            onServerAnnouncedRoom(kv[0].trim(), kv[1].trim());
                          } else {
                            onServerAnnouncedRoom(p, null);
                          }
                        }
                      }
                    }
                  }
                })
        );
      } catch (IOException e) {
        Platform.runLater(() ->
                chatList.getItems().add("[SISTEMA] Nepavyko prisijungti prie serverio: " + e.getMessage())
        );
        client = null;
      }
    }, "connect-thread-" + username);
    t.setDaemon(true);
    t.start();
  }

  private void onServerAnnouncedRoom(String roomId, String optDisplay) {
    if (roomId == null || roomId.isBlank()) return;
    String display = optDisplay == null || optDisplay.isBlank() ? idToDisplay.getOrDefault(roomId, roomId) : optDisplay;

    // update maps and UI on JavaFX thread
    Platform.runLater(() -> {
      if (!idToDisplay.containsKey(roomId)) {
        idToDisplay.put(roomId, display);
      }
      if (!displayToId.containsKey(display)) {
        displayToId.put(display, roomId);
      }
      if (!roomDisplays.contains(display)) {
        roomDisplays.add(display);
      }
      roomMessages.putIfAbsent(roomId, FXCollections.observableArrayList());
    });
  }

  private void sendJoinRoom(String roomId) {
    if (client == null) return;
    client.send(Message.builder()
            .type("JOIN_ROOM")
            .from(username)
            .room(roomId)
            .build());
  }

  private String toIdFromDisplay(String display) {
    if (display == null) return "";
    return display.trim().toLowerCase().replaceAll("\\s+", "-");
  }

  public void close() {
    if (client != null) {
      client.close();
      client = null;
    }
  }
}
