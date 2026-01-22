package lt.kostas.chatapp.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
  private TextArea chatArea;
  @FXML
  private TextField inputField;
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
    // Naudokime aiškų room id; serveris turi tokį patį id.
    roomSelector.getItems().add("general");
    roomSelector.getSelectionModel().selectFirst();

    connectButton.setOnAction(ev -> doConnect());
    sendButton.setOnAction(ev -> doSend());
    createRoomButton.setOnAction(ev -> doCreateRoom());

    // Kai vartotojas pakeičia pasirinkimą, praneškime serveriui, kad joinina tą kambarį.
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

  private void doConnect() {
    username = usernameField.getText().trim();
    if (username.isEmpty()) return;
    client = new NetworkClient();
    try {
      client.connect("localhost", 55555, this::onMessage);
      // send register
      Message m = new Message("register", username, null, null, null);
      client.send(m);
      // po registracijos automatiškai joininam dabartinį pasirinktą kambarį
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
      appendLocal("Nesu prijungęs prie serverio.");
      return;
    }
    String text = inputField.getText();
    if (text == null || text.isEmpty()) return;
    String roomId = roomSelector.getValue();
    if (roomId == null || roomId.isEmpty()) {
      appendLocal("Pasirinkite kambarį.");
      return;
    }
    Message m = new Message("message", username, null, roomId, text);
    client.send(m);
    inputField.clear();
    // NEPRIDĖTI lokaliai — laukiame serverio broadcast (ji mums taip pat bus atsiųsta)
  }

  private void doCreateRoom() {
    if (client == null || username == null) {
      appendLocal("Reikia prisijungti prieš kuriant kambarį.");
      return;
    }
    String rn = newRoomField.getText();
    if (rn == null || rn.isEmpty()) return;
    String id = rn.trim().toLowerCase().replaceAll("\\s+", "-");
    Message m = new Message("create-room", username, null, id, rn); // roomId=id, text=displayName
    client.send(m);

    // Pridedame ROOM ID (ne display text) į ComboBox, kad toliau naudotume tą patį id
    if (!roomSelector.getItems().contains(id)) {
      roomSelector.getItems().add(id);
    }
    // automatiškai joininam ką tik sukurtą kambarį
    Message join = new Message("join-room", username, null, id, null);
    client.send(join);

    newRoomField.clear();
  }

  private void onMessage(Message m) {
    Platform.runLater(() -> {
      String room = m.roomId != null ? m.roomId : "pm";
      String from = m.from != null ? m.from : "server";
      String line = String.format("[%s] %s: %s", room, from, m.text);
      chatArea.appendText(line + "\n");
    });
  }

  private void appendLocal(String text) {
    Platform.runLater(() -> chatArea.appendText("[local] " + text + "\n"));
  }
}
