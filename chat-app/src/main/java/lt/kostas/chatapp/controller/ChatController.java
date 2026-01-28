package lt.kostas.chatapp.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.network.NetworkClient;

public class ChatController {
  @FXML
  private TextField usernameField;
  @FXML
  private Button connectButton;
  @FXML
  private ComboBox<String> roomSelector;
  @FXML
  private ComboBox<String> recipientSelector;
  @FXML
  private TextArea chatArea;
  @FXML
  private TextArea inputField;
  @FXML
  private Button sendButton;
  @FXML
  private TextField newRoomField;
  @FXML
  private Button createRoomButton;

  private NetworkClient client;
  private String username;

  @FXML
  public void initialize() {
    // Saugūs null patikrinimai (jei FXML neatlieka injekcijos)
    if (roomSelector != null) {
      roomSelector.getItems().add("general");
      roomSelector.getSelectionModel().selectFirst();
    }
    if (recipientSelector != null) {
      recipientSelector.getItems().add("");
      recipientSelector.getSelectionModel().selectFirst();
    }

    connectButton.setOnAction(ev -> doConnect());
    sendButton.setOnAction(ev -> doSend());
    createRoomButton.setOnAction(ev -> doCreateRoom());

    if (roomSelector != null) {
      roomSelector.setOnAction(ev -> {
        if (client != null && username != null && !username.isEmpty()) {
          String selectedRoom = roomSelector.getValue();
          if (selectedRoom != null && !selectedRoom.isEmpty()) {
            Message join = new Message("join-room", username, null, selectedRoom, null);
            client.send(join);
          }
        }
      });
    }

    // 'Enter' išsiunčia žinutę, 'Shift+Enter' įterpia naują eilutę
    if (inputField != null) {
      inputField.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
        if (ev.getCode() == KeyCode.ENTER) {
          if (ev.isShiftDown()) {
            int pos = inputField.getCaretPosition();
            inputField.insertText(pos, "\n");
            inputField.positionCaret(pos + 1);
            ev.consume();
          } else {
            ev.consume();
            doSend();
          }
        }
      });
    }
  }

  private void doConnect() {
    username = usernameField.getText().trim();
    if (username.isEmpty()) return;
    client = new NetworkClient();
    try {
      client.connect("localhost", 55555, this::onMessage);
      Message m = new Message("register", username, null, null, null);
      client.send(m);

      String currentRoom = roomSelector.getValue();
      if (currentRoom != null && !currentRoom.isEmpty()) {
        Message join = new Message("join-room", username, null, currentRoom, null);
        client.send(join);
      }
      appendLocal("Prisijungta kaip: " + username);
    } catch (Exception e) {
      appendLocal("Prisijungimas nepavyko: " + e.getMessage());
      client = null;
    }
  }

  private void doSend() {
    if (client == null) {
      appendLocal("Nesu prisijungęs prie serverio.");
      return;
    }
    if (username == null || username.isEmpty()) {
      appendLocal("Reikia būti prisijungus prieš siunčiant žinutes.");
      return;
    }

    String text = inputField.getText();
    if (text == null) return;
    if (text.trim().isEmpty()) return;

    String to = null;
    if (recipientSelector != null) {
      String sel = recipientSelector.getValue();
      if (sel != null && !sel.isBlank()) {
        to = sel.trim();
      }
    }

    Message m;
    if (to != null) {
      m = new Message("message", username, to, null, text);
    } else {
      String roomId = roomSelector != null ? roomSelector.getValue() : null;
      if (roomId == null || roomId.isEmpty()) {
        appendLocal("Pasirinkite kambarį.");
        return;
      }
      m = new Message("message", username, null, roomId, text);
    }

    try {
      client.send(m);
    } catch (Exception e) {
      appendLocal("Klaida siunčiant: " + e.getMessage());
    }
    inputField.clear();
  }

  private void doCreateRoom() {
    if (client == null || username == null) {
      appendLocal("Reikia būti prisijungus prieš kuriant kambarį.");
      return;
    }
    String rn = newRoomField.getText();
    if (rn == null || rn.isEmpty()) return;
    String id = rn.trim().toLowerCase().replaceAll("\\s+", "-");
    Message m = new Message("create-room", username, null, id, rn);
    client.send(m);

    if (roomSelector != null && !roomSelector.getItems().contains(id)) {
      roomSelector.getItems().add(id);
    }
    Message join = new Message("join-room", username, null, id, null);
    client.send(join);
    newRoomField.clear();
  }

  private void onMessage(Message m) {
    Platform.runLater(() -> {
      // Saugiai nuskaityti tipą — jei nėra, laikome "message"
      String type = "message";
      try {
        String t = m.type();
        if (t != null) type = t;
      } catch (NoSuchMethodError | AbstractMethodError | Exception err) {
        type = "message";
      }

      switch (type) {
        case "room-created" -> {
          String id = m.roomId();
          if (id != null && roomSelector != null && !roomSelector.getItems().contains(id)) {
            roomSelector.getItems().add(id);
          }
          return;
        }
        case "user-joined" -> {
          // Išsaugome m.text() ir m.from() į lokalius kintamuosius
          String text = m.text();
          String from = m.from();
          String user = (text != null && !text.isBlank()) ? text : from;
          if (user != null && recipientSelector != null && !recipientSelector.getItems().contains(user)) {
            recipientSelector.getItems().add(user);
          }
          return;
        }
        case "user-left" -> {
          String text = m.text();
          String from = m.from();
          String user = (text != null && !text.isBlank()) ? text : from;
          if (recipientSelector != null) recipientSelector.getItems().remove(user);
          return;
        }
      }
      // message (viešas/privatus) — taip pat naudojame lokalius kintamuosius
      String msgText = m.text();
      String from = m.from();
      String resolvedFrom = (from != null) ? from : "server";
      String to = m.to();

      if (to != null && !to.isBlank()) {
        chatArea.appendText(String.format("[PM] %s -> %s: %s%n", resolvedFrom, to, msgText));
      } else {
        String room = m.roomId() != null ? m.roomId() : "pm";
        chatArea.appendText(String.format("[%s] %s: %s%n", room, resolvedFrom, msgText));
      }
    });
  }

  private void appendLocal(String text) {
    Platform.runLater(() -> chatArea.appendText("[local] " + text + "\n"));
  }
}
