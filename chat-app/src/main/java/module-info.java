module lt.kostas.chatapp {
  requires javafx.controls;
  requires javafx.fxml;

  requires org.controlsfx.controls;
  requires com.google.gson;
  requires static lombok;
  requires java.logging;

  opens lt.kostas.chatapp to javafx.fxml;
  exports lt.kostas.chatapp;

  exports lt.kostas.chatapp.server;
  opens lt.kostas.chatapp.server to javafx.fxml;

  exports lt.kostas.chatapp.network;
  opens lt.kostas.chatapp.network to javafx.fxml;

  exports lt.kostas.chatapp.dto;
  opens lt.kostas.chatapp.dto to javafx.fxml, com.google.gson;

  exports lt.kostas.chatapp.controller;
  opens lt.kostas.chatapp.controller to javafx.fxml;
}