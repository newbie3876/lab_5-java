package lt.kostas.chatapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lt.kostas.chatapp.controller.ChatController;

public class ChatClientApplication extends Application {
  @Override
  public void start(Stage primaryStage) throws Exception {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat-log.fxml"));
    Parent root = loader.load();
    ChatController controller = loader.getController();

    primaryStage.setScene(new Scene(root));
    primaryStage.setOnCloseRequest(e -> {
      controller.close();
    });
    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
