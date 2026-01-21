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
  private TextField inputField;
  @FXML
  private ComboBox<ChatRoomType> roomSelector;
  @FXML
  private TextField newRoomField;
  @FXML
  private TextField recipientField;
  @FXML
  private CheckBox privateCheckbox;

  private String username;
  private volatile NetworkClient client;
  private final Map<String, ObservableList<String>> roomMessages = new HashMap<>();
  private static final ChatRoomType DEFAULT_ROOM = ChatRoomType.GENERAL;

  @FXML
  public void initialize() {
    username = "Vartotojas" + (int) (Math.random() * 1000);
    // Sisteminiai kambariai
    roomSelector.getItems().setAll(ChatRoomType.values());
    roomSelector.setValue(DEFAULT_ROOM);
    // Inicializuojam enum kambarių žinutes
    for (ChatRoomType room : ChatRoomType.values()) {
      roomMessages.put(room.getId(), FXCollections.observableArrayList());
    }
    // Rodyti žinutes tik pasirinktam enum kambariui
    roomSelector.valueProperty().addListener((obs, oldRoom, newRoom) -> {
      if (newRoom == null) return;
      chatList.setItems(roomMessages.get(newRoom.getId()));
    });

    chatList.setItems(roomMessages.get(DEFAULT_ROOM.getId()));
    // Lietuviški pavadinimai ComboBox
    roomSelector.setCellFactory(cb -> new ListCell<>() {
      @Override
      protected void updateItem(ChatRoomType item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.getDisplayLt());
      }
    });
    roomSelector.setButtonCell(roomSelector.getCellFactory().call(null));
    connectAsync();
  }

  @FXML
  private void onSend() {
    if (client == null) {
      chatList.getItems().add("[SISTEMA] Nėra prisijungimo prie serverio.");
      return;
    }

    String text = inputField.getText();
    if (text == null || (text = text.trim()).isEmpty()) return;

    try {
      if (privateCheckbox != null && privateCheckbox.isSelected()) {
        String toUser = recipientField == null ? null : recipientField.getText();
        if (toUser == null || toUser.isBlank()) {
          chatList.getItems().add("[SISTEMA] Įveskite gavėjo vardą privatinei žinutei.");
          return;
        }
        Message pm = Message.builder()
                .type("PRIVATE_MSG")
                .from(username)
                .to(toUser.trim())
                .message(text)
                .build();
        client.send(pm);
        // (nebūtinai clear recipient)
      } else {
        ChatRoomType selectedRoom =
                roomSelector != null && roomSelector.getValue() != null
                        ? roomSelector.getValue()
                        : DEFAULT_ROOM;
        if (selectedRoom == null) selectedRoom = DEFAULT_ROOM;
        Message rm = Message.builder()
                .type("ROOM_MSG")
                .from(username)
                .room(selectedRoom.getId())
                .message(text)
                .build();
        client.send(rm);
      }
      inputField.clear();
    } catch (Exception ex) {
      final String err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
      Platform.runLater(() -> chatList.getItems().add("[SISTEMA] Klaida siunčiant: " + err));
    }
  }

  @FXML
  private void onCreateRoom() {
    if (client == null) return;

    String newRoom = newRoomField.getText();
    if (newRoom == null || newRoom.isBlank()) return;

    newRoom = newRoom.trim();

    client.send(Message.builder()
            .type("CREATE_ROOM")
            .from(username)
            .room(newRoom)
            .build());
    // žinutės saugomos, bet nerodomos, kol neprisijungta
    roomMessages.putIfAbsent(
            newRoom,
            FXCollections.observableArrayList()
    );
    chatList.getItems().add(
            "[SISTEMA] Kambarys sukurtas serveryje: " + newRoom
    );
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

                  switch (msg.getType()) {

                    case "ROOM_MSG" -> {
                      String roomId = msg.getRoom();
                      if (roomId == null) return;
                      roomMessages.putIfAbsent(
                              roomId,
                              FXCollections.observableArrayList()
                      );
                      roomMessages.get(roomId)
                              .add(msg.getFrom() + ": " + msg.getMessage());
                    }

                    case "PRIVATE_MSG" -> {
                      chatList.getItems().add(
                              "[PRIVATI] " + msg.getFrom() + ": " + msg.getMessage()
                      );
                    }

                    case "INFO" -> {
                      chatList.getItems().add(
                              "[INFO] " + msg.getMessage()
                      );
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

  public void close() {
    if (client != null) {
      client.close();
      client = null;
    }
  }
}
