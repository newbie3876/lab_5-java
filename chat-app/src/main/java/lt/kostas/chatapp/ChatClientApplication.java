package lt.kostas.chatapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatClientApplication extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/main-view.fxml")
    );
    Scene scene = new Scene(loader.load(), 600, 400);
    stage.setScene(scene);
    stage.setTitle("Pokalbių sistema");
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
