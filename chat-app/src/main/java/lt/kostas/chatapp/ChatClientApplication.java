package lt.kostas.chatapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class ChatClientApplication extends Application {
  @Override
  public void start(Stage primaryStage) throws Exception {
    Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/chat-room.fxml")));
    primaryStage.setTitle("Pokalbių kambarys");
    primaryStage.setScene(new Scene(root, 800, 600));
    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
