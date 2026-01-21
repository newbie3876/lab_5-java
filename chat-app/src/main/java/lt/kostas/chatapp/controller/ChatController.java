package lt.kostas.chatapp.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import lt.kostas.chatapp.dto.Message;
import lt.kostas.chatapp.network.NetworkClient;

import java.io.IOException;

public class ChatController {
  @FXML
  private ListView<String> chatList;
  @FXML
  private TextField inputField;

  private NetworkClient client;
  private String username;

  @FXML
  public void initialize() {
    username = "Vartotojas" + (int) (Math.random() * 1000);
    connectAsync();
  }

  @FXML
  private void onSend() {
    if (client != null) {
      client.send(new Message("ROOM_MSG", username, "general", inputField.getText()));
      inputField.clear();
    }
  }

  private void connectAsync() {
    Thread t = new Thread(() -> {
      try {
        client = new NetworkClient();
        client.connect(
                "localhost",
                5555,
                username,
                msg -> Platform.runLater(() ->
                        chatList.getItems().add(
                                (msg.getRoom() != null ? "[" + msg.getRoom() + "] " : "")
                                        + msg.getFrom() + ": " + msg.getMessage()
                        )
                )
        );
      } catch (IOException e) {
        Platform.runLater(() ->
                chatList.getItems().add("Nepavyko prisijungti prie serverio")
        );
      }
    });

    t.setDaemon(true);
    t.start();
  }
}
