import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShazamFX extends Application {

    private final AudioFingerprinter fingerprinter = new AudioFingerprinter();
    private ObservableList<SongEntry> songsList = FXCollections.observableArrayList();
    private TableView<SongEntry> songsTable;
    private StackPane mainContentArea;
    private ProgressBar progressBar;
    private Label statusLabel;
    private SimpleBooleanProperty isRecording = new SimpleBooleanProperty(false);
    private AtomicBoolean recordingCancelled = new AtomicBoolean(false);

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ShazamFX - Audio Recognition App");

        // Create the main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Create header
        VBox header = createHeader();
        root.setTop(header);

        // Create sidebar with navigation buttons
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // Main content area
        mainContentArea = new StackPane();
        mainContentArea.setPadding(new Insets(10));
        mainContentArea.setBackground(new Background(new BackgroundFill(
                Color.rgb(245, 245, 245), new CornerRadii(5), Insets.EMPTY)));

        // Default view - Songs Library
        showSongsLibrary();

        root.setCenter(mainContentArea);

        // Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        // Set scene
        Scene scene = new Scene(root, 900, 600);
        try {
            String cssPath = "file:style.css";
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.out.println("Warning: Could not load stylesheet. Using default styling.");
        }
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        // Load songs from database on startup
        refreshSongsList();

        // Handle close request
        primaryStage.setOnCloseRequest(event -> {
            fingerprinter.close();
            Platform.exit();
        });
    }

    private VBox createHeader() {
        VBox header = new VBox();
        header.setSpacing(5);
        header.setPadding(new Insets(0, 0, 10, 0));

        // App title
        Text title = new Text("ShazamFX");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setFill(Color.DARKBLUE);

        // Subtitle
        Text subtitle = new Text("Advanced Audio Recognition System");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 14));
        subtitle.setFill(Color.GRAY);

        header.getChildren().addAll(title, subtitle);

        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(200);
        sidebar.setSpacing(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setBackground(new Background(new BackgroundFill(
                Color.rgb(230, 230, 255), new CornerRadii(5), Insets.EMPTY)));

        // Create buttons for each function
        Button libraryBtn = createNavButton("Library", "view-list", e -> showSongsLibrary());
        Button identifyBtn = createNavButton("Identify Song", "microphone", e -> showIdentifyView());
        Button addSongBtn = createNavButton("Add Song", "plus-circle", e -> showAddSongView());
        Button addDirBtn = createNavButton("Add Directory", "folder-plus", e -> showAddDirectoryView());

        sidebar.getChildren().addAll(
                createSidebarHeader(),
                new Separator(),
                libraryBtn,
                identifyBtn,
                addSongBtn,
                addDirBtn
        );

        return sidebar;
    }

    private HBox createSidebarHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        header.setPadding(new Insets(5));

        Text menuText = new Text("Menu");
        menuText.setFont(Font.font("System", FontWeight.BOLD, 16));

        header.getChildren().add(menuText);
        return header;
    }

    private Button createNavButton(String text, String iconName, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.setPrefWidth(180);
        button.setPrefHeight(40);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(action);
        button.getStyleClass().add("nav-button");

        // You would need to have these icons in your resources
        // This is just placeholder logic
        try {
            ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream("/icons/" + iconName + ".png")));
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            button.setGraphic(imageView);
        } catch (Exception e) {
            // If icon not found, just use text
            System.out.println("Icon not found: " + iconName);
        }

        return button;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5));
        statusBar.setSpacing(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        statusLabel = new Label("Ready");

        statusBar.getChildren().addAll(statusLabel, progressBar);
        return statusBar;
    }

    private void showSongsLibrary() {
        VBox content = new VBox();
        content.setSpacing(10);

        // Header
        Text header = new Text("Songs Library");
        header.setFont(Font.font("System", FontWeight.BOLD, 20));

        // Create table for songs
        songsTable = new TableView<>();
        songsTable.setPlaceholder(new Label("No songs in library"));

        TableColumn<SongEntry, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);

        TableColumn<SongEntry, String> nameCol = new TableColumn<>("Song Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(300);

        TableColumn<SongEntry, String> pathCol = new TableColumn<>("File Path");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pathCol.setPrefWidth(300);

        songsTable.getColumns().addAll(idCol, nameCol, pathCol);
        songsTable.setItems(songsList);

        // Search field
        HBox searchBox = new HBox();
        searchBox.setSpacing(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search songs...");
        searchField.setPrefWidth(300);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshSongsList());

        searchBox.getChildren().addAll(new Label("Search:"), searchField, refreshBtn);

        // Add to content
        content.getChildren().addAll(header, searchBox, songsTable);
        VBox.setVgrow(songsTable, Priority.ALWAYS);

        updateMainContent(content);
    }

    private void showIdentifyView() {
        StackPane content = new StackPane(); // Changed from VBox to StackPane for better layout control

        VBox mainContent = new VBox(); // Inner VBox for the actual content
        mainContent.setSpacing(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setMaxWidth(600); // Limit width for better appearance

        // Header
        Text header = new Text("Identify Song");
        header.setFont(Font.font("System", FontWeight.BOLD, 20));

        // Microphone section
        VBox micSection = new VBox();
        micSection.setSpacing(15);
        micSection.setAlignment(Pos.CENTER);
        micSection.setPadding(new Insets(20));
        micSection.setBackground(new Background(new BackgroundFill(
                Color.rgb(240, 240, 255), new CornerRadii(10), Insets.EMPTY)));
        micSection.setMaxWidth(500);

        ImageView micIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/microphone-large.png")));
        micIcon.setFitHeight(80);
        micIcon.setFitWidth(80);

        Text micText = new Text("Click to start listening");
        micText.setFont(Font.font("System", FontWeight.NORMAL, 16));

        Slider durationSlider = new Slider(5, 20, 10);
        durationSlider.setShowTickLabels(true);
        durationSlider.setShowTickMarks(true);
        durationSlider.setMajorTickUnit(5);
        durationSlider.setMinorTickCount(4);
        durationSlider.setSnapToTicks(true);
        durationSlider.setPrefWidth(300);

        HBox sliderBox = new HBox();
        sliderBox.setAlignment(Pos.CENTER);
        sliderBox.setSpacing(10);
        sliderBox.getChildren().addAll(
                new Label("Duration:"),
                durationSlider,
                new Label("seconds")
        );

        Button recordButton = new Button("Start Listening");
        recordButton.setPrefWidth(200);
        recordButton.setPrefHeight(50);
        recordButton.getStyleClass().add("record-button");

        // Result area - now a separate component that will be centered
        VBox resultArea = new VBox();
        resultArea.setSpacing(10);
        resultArea.setAlignment(Pos.CENTER);
        resultArea.setPadding(new Insets(20));
        resultArea.setBackground(new Background(new BackgroundFill(
                Color.rgb(245, 245, 245), new CornerRadii(10), Insets.EMPTY)));
        resultArea.setVisible(false);
        resultArea.setMaxWidth(500);
        resultArea.setMaxHeight(300);

        Text resultHeader = new Text("Result");
        resultHeader.setFont(Font.font("System", FontWeight.BOLD, 18));

        Text songNameText = new Text();
        songNameText.setFont(Font.font("System", FontWeight.NORMAL, 16));

        Text confidenceText = new Text();
        confidenceText.setFont(Font.font("System", FontWeight.NORMAL, 14));

        Button newSearchBtn = new Button("New Search");
        newSearchBtn.setOnAction(e -> {
            resultArea.setVisible(false);
            micSection.setVisible(true);
        });

        resultArea.getChildren().addAll(resultHeader, songNameText, confidenceText, newSearchBtn);

        recordButton.setOnAction(e -> {
            if (isRecording.get()) {
                // Stop recording
                recordingCancelled.set(true);
                recordButton.setText("Start Listening");
                isRecording.set(false);
            } else {
                // Start recording
                recordButton.setText("Cancel");
                isRecording.set(true);
                recordingCancelled.set(false);

                int durationMs = (int) (durationSlider.getValue() * 1000);

                recordAndIdentify(durationMs, songNameText, confidenceText, micSection, resultArea);
            }
        });

        // File identification option
        Separator separator = new Separator();
        separator.setPrefWidth(500);

        HBox fileSection = new HBox();
        fileSection.setAlignment(Pos.CENTER);
        fileSection.setSpacing(10);

        Button fileButton = new Button("Identify from File");
        fileButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Audio File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.flac"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File selectedFile = fileChooser.showOpenDialog(null);

            if (selectedFile != null) {
                identifyFromFile(selectedFile, songNameText, confidenceText, micSection, resultArea);
            }
        });

        fileSection.getChildren().addAll(new Label("Or"), fileButton);

        micSection.getChildren().addAll(micIcon, micText, sliderBox, recordButton, separator, fileSection);

        mainContent.getChildren().addAll(header, micSection);

        // Add both components to the StackPane (they will overlap)
        content.getChildren().addAll(mainContent, resultArea);

        // Position the resultArea in the center of the StackPane
        StackPane.setAlignment(resultArea, Pos.CENTER);

        updateMainContent(content);
    }

    // Also update the recordAndIdentify method to work with the new layout
    private void recordAndIdentify(int durationMs, Text songNameText, Text confidenceText,
                                   VBox micSection, VBox resultArea) {
        statusLabel.setText("Recording...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<EnhancedMatcher.MatchResult> task = new Task<>() {
            @Override
            protected EnhancedMatcher.MatchResult call() throws Exception {
                return fingerprinter.identifySong(durationMs);
            }

            @Override
            protected void succeeded() {
                EnhancedMatcher.MatchResult result = getValue();
                Platform.runLater(() -> {
                    isRecording.set(false);
                    progressBar.setVisible(false);
                    statusLabel.setText("Ready");

                    if (result.isMatched()) {
                        songNameText.setText("Song: " + result.getSongName());
                        confidenceText.setText("Confidence: " + result.getMatchCount() + " matching points");
                    } else {
                        songNameText.setText("No match found");
                        confidenceText.setText("");
                    }

                    micSection.setVisible(false);
                    resultArea.setVisible(true);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    isRecording.set(false);
                    progressBar.setVisible(false);
                    statusLabel.setText("Error occurred during recording");
                    showAlert(Alert.AlertType.ERROR, "Recording Error",
                            "An error occurred while recording or processing audio.",
                            getException().getMessage());
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // Also update the identifyFromFile method to work with the new layout
    private void identifyFromFile(File file, Text songNameText, Text confidenceText,
                                  VBox micSection, VBox resultArea) {
        statusLabel.setText("Processing file...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<EnhancedMatcher.MatchResult> task = new Task<>() {
            @Override
            protected EnhancedMatcher.MatchResult call() throws Exception {
                return fingerprinter.identifyFile(file.getAbsolutePath());
            }

            @Override
            protected void succeeded() {
                EnhancedMatcher.MatchResult result = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Ready");

                    if (result.isMatched()) {
                        songNameText.setText("Song: " + result.getSongName());
                        confidenceText.setText("Confidence: " + result.getMatchCount() + " matching points");
                    } else {
                        songNameText.setText("No match found");
                        confidenceText.setText("");
                    }

                    micSection.setVisible(false);
                    resultArea.setVisible(true);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Error processing file");
                    showAlert(Alert.AlertType.ERROR, "Processing Error",
                            "An error occurred while processing the audio file.",
                            getException().getMessage());
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showAddSongView() {
        VBox content = new VBox();
        content.setSpacing(20);
        content.setPadding(new Insets(20));

        // Header
        Text header = new Text("Add Song to Library");
        header.setFont(Font.font("System", FontWeight.BOLD, 20));

        // Form
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(20));
        form.setBackground(new Background(new BackgroundFill(
                Color.rgb(240, 240, 255), new CornerRadii(10), Insets.EMPTY)));

        Label filePathLabel = new Label("Audio File:");
        TextField filePathField = new TextField();
        filePathField.setEditable(false);
        filePathField.setPrefWidth(300);

        Label songNameLabel = new Label("Song Name:");
        TextField songNameField = new TextField();

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Audio File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.flac"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File selectedFile = fileChooser.showOpenDialog(null);

            if (selectedFile != null) {
                filePathField.setText(selectedFile.getAbsolutePath());

                // Try to auto-fill song name from filename
                String fileName = selectedFile.getName();
                int extensionIndex = fileName.lastIndexOf('.');
                if (extensionIndex > 0) {
                    fileName = fileName.substring(0, extensionIndex);
                }
                songNameField.setText(fileName);
            }
        });

        Button addButton = new Button("Add to Library");
        addButton.setPrefWidth(150);
        addButton.getStyleClass().add("action-button");

        addButton.setOnAction(e -> {
            String filePath = filePathField.getText();
            String songName = songNameField.getText();

            if (filePath.isEmpty() || songName.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Information",
                        "Please provide both a file and song name.", null);
                return;
            }

            addSongToLibrary(filePath, songName);
        });

        form.add(filePathLabel, 0, 0);
        form.add(filePathField, 1, 0);
        form.add(browseButton, 2, 0);
        form.add(songNameLabel, 0, 1);
        form.add(songNameField, 1, 1);
        form.add(addButton, 1, 2);

        content.getChildren().addAll(header, form);

        updateMainContent(content);
    }

    private void showAddDirectoryView() {
        VBox content = new VBox();
        content.setSpacing(20);
        content.setPadding(new Insets(20));

        // Header
        Text header = new Text("Add Directory of Songs");
        header.setFont(Font.font("System", FontWeight.BOLD, 20));

        // Form
        VBox form = new VBox();
        form.setSpacing(15);
        form.setPadding(new Insets(20));
        form.setBackground(new Background(new BackgroundFill(
                Color.rgb(240, 240, 255), new CornerRadii(10), Insets.EMPTY)));
        form.setMaxWidth(600);

        Text instruction = new Text("Select a directory containing audio files (MP3, WAV, FLAC).\n" +
                "The app will attempt to extract song names from metadata or file names.");
        instruction.setWrappingWidth(550);

        HBox directorySelectionBox = new HBox();
        directorySelectionBox.setSpacing(10);
        directorySelectionBox.setAlignment(Pos.CENTER_LEFT);

        Label directoryLabel = new Label("Directory:");
        TextField directoryField = new TextField();
        directoryField.setEditable(false);
        directoryField.setPrefWidth(300);

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Directory with Audio Files");
            File selectedDirectory = directoryChooser.showDialog(null);

            if (selectedDirectory != null) {
                directoryField.setText(selectedDirectory.getAbsolutePath());
            }
        });

        directorySelectionBox.getChildren().addAll(directoryLabel, directoryField, browseButton);

        // Options
        VBox options = new VBox();
        options.setSpacing(10);

        CheckBox includeSubdirectoriesCheckbox = new CheckBox("Include subdirectories");
        CheckBox useMetadataCheckbox = new CheckBox("Use metadata for song names when available");
        useMetadataCheckbox.setSelected(true);

        options.getChildren().addAll(includeSubdirectoriesCheckbox, useMetadataCheckbox);

        Button addButton = new Button("Add All Songs");
        addButton.setPrefWidth(150);
        addButton.getStyleClass().add("action-button");

        addButton.setOnAction(e -> {
            String directoryPath = directoryField.getText();

            if (directoryPath.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Directory Selected",
                        "Please select a directory containing audio files.", null);
                return;
            }

            boolean includeSubdirectories = includeSubdirectoriesCheckbox.isSelected();
            boolean useMetadata = useMetadataCheckbox.isSelected();

            addDirectoryToLibrary(directoryPath, includeSubdirectories, useMetadata);
        });

        form.getChildren().addAll(instruction, directorySelectionBox, options, addButton);

        content.getChildren().addAll(header, form);

        updateMainContent(content);
    }

    private void addSongToLibrary(String filePath, String songName) {
        statusLabel.setText("Adding song...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                // For MP3 files, try converting to WAV first (like in your console app)
                File tempFile = null;
                String fileToProcess = filePath;

                try {
                    if (filePath.toLowerCase().endsWith(".mp3")) {
                        updateMessage("Converting MP3 to WAV format...");
                        tempFile = convertMp3ToWav(new File(filePath));
                        if (tempFile != null) {
                            fileToProcess = tempFile.getAbsolutePath();
                        }
                    }

                    // Now process the file (either original or converted)
                    return fingerprinter.addSong(fileToProcess, songName);
                } finally {
                    // Clean up temp file if it was created
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }

            @Override
            protected void succeeded() {
                boolean success = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);

                    if (success) {
                        statusLabel.setText("Song added successfully");
                        showAlert(Alert.AlertType.INFORMATION, "Success",
                                "Song added to library successfully.", null);
                        refreshSongsList();
                        showSongsLibrary();
                    } else {
                        statusLabel.setText("Failed to add song");
                        showAlert(Alert.AlertType.ERROR, "Error",
                                "Failed to add song to library.", null);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Error adding song");
                    showAlert(Alert.AlertType.ERROR, "Error",
                            "An error occurred while adding the song.",
                            getException().getMessage());
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void addDirectoryToLibrary(String directoryPath, boolean includeSubdirectories, boolean useMetadata) {
        statusLabel.setText("Adding songs from directory...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                // This would need to be implemented in your backend
                // For now, we'll use a simplified approach
                File directory = new File(directoryPath);
                return processDirectory(directory, includeSubdirectories, useMetadata);
            }

            private int processDirectory(File directory, boolean includeSubdirs, boolean useMetadata) {
                int count = 0;
                File[] files = directory.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory() && includeSubdirs) {
                            count += processDirectory(file, true, useMetadata);
                        } else if (isAudioFile(file.getName())) {
                            String fileName = file.getName();
                            String songName = fileName.substring(0, fileName.lastIndexOf('.'));
                            String filePath = file.getAbsolutePath();
                            File tempFile = null;

                            try {
                                // For MP3 files, convert to WAV first
                                if (filePath.toLowerCase().endsWith(".mp3")) {
                                    updateMessage("Converting: " + fileName);
                                    tempFile = convertMp3ToWav(file);
                                    if (tempFile != null) {
                                        filePath = tempFile.getAbsolutePath();
                                    }
                                }

                                updateMessage("Processing: " + fileName);
                                if (fingerprinter.addSong(filePath, songName)) {
                                    count++;
                                }
                            } finally {
                                // Clean up temp file
                                if (tempFile != null && tempFile.exists()) {
                                    tempFile.delete();
                                }
                            }
                        }
                    }
                }
                return count;
            }

            private boolean isAudioFile(String fileName) {
                fileName = fileName.toLowerCase();
                return fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".flac");
            }

            @Override
            protected void succeeded() {
                int count = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Added " + count + " songs");

                    showAlert(Alert.AlertType.INFORMATION, "Import Complete",
                            "Successfully added " + count + " songs to the library.", null);

                    refreshSongsList();
                    showSongsLibrary();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Error adding songs");
                    showAlert(Alert.AlertType.ERROR, "Error",
                            "An error occurred while adding songs from directory.",
                            getException().getMessage());
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshSongsList() {
        statusLabel.setText("Loading songs...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return fingerprinter.listSongs();
            }

            @Override
            protected void succeeded() {
                List<String> songs = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Ready");

                    songsList.clear();
                    for (int i = 0; i < songs.size(); i++) {
                        songsList.add(new SongEntry(i + 1, songs.get(i), ""));
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Error loading songs");
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void updateMainContent(javafx.scene.Node content) {
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(content);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class SongEntry {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty path;

        public SongEntry(int id, String name, String path) {
            this.id = new SimpleStringProperty(String.valueOf(id));
            this.name = new SimpleStringProperty(name);
            this.path = new SimpleStringProperty(path);
        }

        public String getId() {
            return id.get();
        }

        public String getName() {
            return name.get();
        }

        public String getPath() {
            return path.get();
        }
    }

    // Add this method from your console app
    private File convertMp3ToWav(File mp3File) {
        try {
            // Create temp file for WAV output
            File wavFile = File.createTempFile("temp_", ".wav");

            // Get AudioInputStream from MP3 file
            AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File);
            AudioFormat baseFormat = mp3Stream.getFormat();

            // Convert to PCM format that can be processed
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, mp3Stream);

            // Write to WAV file
            AudioSystem.write(pcmStream, AudioFileFormat.Type.WAVE, wavFile);

            // Close streams
            pcmStream.close();
            mp3Stream.close();

            return wavFile;
        } catch (Exception e) {
            System.out.println("Error converting MP3 to WAV: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}