package hospital.ui;

import hospital.simulation.SimulationConfig;
import hospital.simulation.SimulationController;
import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationObserver;
import hospital.simulation.SimulationSnapshot;
import hospital.update.UpdateChecker;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static hospital.simulation.SimulationSnapshot.DoctorStatus;
import static hospital.simulation.SimulationSnapshot.EventCategory;
import static hospital.simulation.SimulationSnapshot.RoomStatus;
import static hospital.simulation.SimulationSnapshot.Status;

public final class HospitalApp extends Application {
    private static final DateTimeFormatter EVENT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final String LIGHT_THEME = "/styles/light.css";
    private static final String DARK_THEME = "/styles/dark.css";
    private static final String APP_ICON = "/icons/app-icon.png";
    private static final String PORTFOLIO_URL =
            "https://engomarabd-elaziz.github.io/My-Portofolio/";

    private final javafx.collections.ObservableList<SimulationEvent> events =
            FXCollections.observableArrayList();
    private final Map<Integer, DoctorCard> doctorCards = new HashMap<>();
    private final Map<Integer, ProgressBar> roomProgressBars = new HashMap<>();
    private final List<Label> seatLabels = new ArrayList<>();
    private SimulationController controller;
    private Stage stage;
    private BorderPane root;
    private Label statusBadge;
    private Label waitingStat;
    private Label totalPatientsStat;
    private Label outsideStat;
    private Label treatingStat;
    private Label completedStat;
    private Label availableDoctorsStat;
    private Label busyDoctorsStat;
    private Label availableRoomsStat;
    private Label occupancyLabel;
    private Label outsidePatientsLabel;
    private Label completionBanner;
    private FlowPane seatsPane;
    private VBox doctorsPane;
    private HBox roomsPane;
    private ListView<SimulationEvent> eventList;
    private TextField capacityField;
    private TextField doctorCountField;
    private TextField patientCountField;
    private TextField roomCountField;
    private TextField minArrivalField;
    private TextField maxArrivalField;
    private TextField minTreatmentField;
    private TextField maxTreatmentField;
    private Button startButton;
    private Button stopButton;
    private Button resetButton;
    private Button themeButton;
    private Button fullScreenButton;
    private boolean darkTheme;
    private boolean timelineAtBottom = true;
    private volatile boolean uiActive = true;
    private SimulationSnapshot latestSnapshot;
    private AnimationTimer progressTimer;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createHeader());
        root.setCenter(createDashboard());
        root.setBottom(createCopyrightFooter());
        applyTheme();

        Scene scene = new Scene(root, 1500, 1000);
        stage.setTitle(t("app.title"));
        stage.getIcons().add(new Image(getClass().getResourceAsStream(APP_ICON)));
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setFullScreenExitHint("Press Esc to exit full screen, or use the button in the header.");
        stage.setScene(scene);
        stage.fullScreenProperty().addListener((observable, wasFullScreen, isFullScreen) ->
                updateFullScreenButton(isFullScreen));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });
        stage.setOnCloseRequest(event -> {
            uiActive = false;
            if (controller != null) {
                controller.stop();
            }
            if (progressTimer != null) {
                progressTimer.stop();
            }
        });
        stage.show();
        stage.setFullScreen(true);
        renderIdle();
        startProgressTimer();
    }

    private void stopSimulation() {
        if (controller == null || latestSnapshot == null
                || latestSnapshot.status() != Status.RUNNING) {
            return;
        }
        stopButton.setDisable(true);
        statusBadge.setText(t("status.stopping"));
        statusBadge.getStyleClass().removeAll(
                "status-idle", "status-running", "status-completed", "status-stopped");
        statusBadge.getStyleClass().add("status-stopped");
        controller.stop();
    }

    @Override
    public void stop() {
        uiActive = false;
        if (controller != null) {
            controller.stop();
        }
        if (progressTimer != null) {
            progressTimer.stop();
        }
    }

    private HBox createHeader() {
        HBox header = new HBox(18);
        header.getStyleClass().add("topbar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 30, 18, 30));

        ImageView mark = new ImageView(new Image(getClass().getResourceAsStream(APP_ICON)));
        mark.setFitWidth(46);
        mark.setFitHeight(46);
        mark.setPreserveRatio(true);
        mark.setSmooth(true);
        mark.setAccessibleText(t("app.icon"));
        mark.getStyleClass().add("brand-mark");
        VBox titleBlock = new VBox(3);
        Label title = new Label(t("app.title"));
        title.getStyleClass().add("app-title");
        Label subtitle = new Label(t("app.subtitle"));
        subtitle.getStyleClass().add("app-subtitle");
        titleBlock.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBadge = new Label(t("status.idle"));
        statusBadge.getStyleClass().addAll("status-badge", "status-idle");
        themeButton = new Button(t("theme.dark"));
        themeButton.getStyleClass().addAll("quiet-button", "icon-button");
        themeButton.setOnAction(event -> {
            darkTheme = !darkTheme;
            applyTheme();
        });
        themeButton.setAccessibleText(t("theme.tooltip"));
        fullScreenButton = new Button(t("fullscreen.exit"));
        fullScreenButton.getStyleClass().add("quiet-button");
        fullScreenButton.setTooltip(new javafx.scene.control.Tooltip(t("fullscreen.tooltip")));
        fullScreenButton.setOnAction(event ->
                stage.setFullScreen(!stage.isFullScreen()));
        Button updateButton = new Button(t("button.updates"));
        updateButton.getStyleClass().add("quiet-button");
        updateButton.setOnAction(event -> checkForUpdates(updateButton));
        header.getChildren().addAll(
                mark, titleBlock, spacer, statusBadge, updateButton, themeButton, fullScreenButton);
        return header;
    }

    private void checkForUpdates(Button button) {
        button.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                String version = System.getProperty("eronix.version", "1.0.0");
                return UpdateChecker.checkLatest(version);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((result, failure) -> Platform.runLater(() -> {
            button.setDisable(false);
            if (failure != null) {
                showUpdateMessage(Alert.AlertType.ERROR, t("updates.error"));
            } else if (result.updateAvailable()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(t("updates.title"));
                alert.setHeaderText(t("updates.available") + " " + result.latestVersion());
                alert.setContentText(t("updates.open"));
                alert.getButtonTypes().setAll(
                        new javafx.scene.control.ButtonType(t("updates.download")),
                        javafx.scene.control.ButtonType.CANCEL);
                alert.showAndWait().filter(buttonType ->
                        buttonType.getText().equals(t("updates.download")))
                        .ifPresent(buttonType -> getHostServices().showDocument(result.releaseUrl()));
            } else {
                showUpdateMessage(Alert.AlertType.INFORMATION, t("updates.current"));
            }
        }));
    }

    private void showUpdateMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(t("updates.title"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private HBox createCopyrightFooter() {
        HBox footer = new HBox(4);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8, 16, 8, 16));
        footer.getStyleClass().add("copyright-footer");

        Label notice = new Label("© 2026 ");
        notice.getStyleClass().add("copyright-text");
        Hyperlink owner = new Hyperlink("Eng. Omar Abdelaziz");
        owner.getStyleClass().add("copyright-link");
        owner.setAccessibleText("Open Eng. Omar Abdelaziz's portfolio");
        owner.setOnAction(event -> getHostServices().showDocument(PORTFOLIO_URL));
        footer.getChildren().addAll(notice, owner);
        return footer;
    }

    private ScrollPane createDashboard() {
        VBox content = new VBox(22);
        content.getStyleClass().add("dashboard-content");
        content.setPadding(new Insets(24, 30, 34, 30));

        HBox intro = new HBox(18);
        intro.setAlignment(Pos.CENTER_LEFT);
        VBox introText = new VBox(5);
        Label heading = new Label(t("overview.title"));
        heading.getStyleClass().add("page-heading");
        Label description = new Label(t("overview.subtitle"));
        description.getStyleClass().add("page-description");
        introText.getChildren().addAll(heading, description);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        completionBanner = new Label();
        completionBanner.getStyleClass().add("completion-banner");
        completionBanner.setVisible(false);
        completionBanner.setManaged(false);
        intro.getChildren().addAll(introText, spacer, completionBanner);

        GridPane stats = createStats();
        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(20);
        mainGrid.setVgap(20);
        mainGrid.getColumnConstraints().addAll(
                new ColumnConstraints(0, 440, Double.MAX_VALUE),
                new ColumnConstraints(0, 720, Double.MAX_VALUE));
        mainGrid.getColumnConstraints().get(0).setPercentWidth(39);
        mainGrid.getColumnConstraints().get(1).setPercentWidth(61);

        VBox waitingCard = createWaitingCard();
        VBox doctorsCard = createDoctorsCard();
        VBox roomsCard = createRoomsCard();
        VBox eventsCard = createEventsCard();
        GridPane.setHgrow(waitingCard, Priority.ALWAYS);
        GridPane.setHgrow(doctorsCard, Priority.ALWAYS);
        mainGrid.add(waitingCard, 0, 0);
        mainGrid.add(doctorsCard, 1, 0);
        mainGrid.add(roomsCard, 0, 1);
        mainGrid.add(eventsCard, 1, 1);

        content.getChildren().addAll(intro, createConfigurationCard(), stats, mainGrid);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");
        return scrollPane;
    }

    private GridPane createStats() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        String[] names = {
                t("stat.total"), t("stat.waiting"), t("stat.outside"), t("stat.treating"),
                t("stat.completed"), t("stat.doctors.available"), t("stat.doctors.busy"),
                t("stat.rooms.available")
        };
        Label[] values = new Label[names.length];
        for (int i = 0; i < names.length; i++) {
            values[i] = new Label("0");
            values[i].getStyleClass().add("stat-value");
            VBox card = new VBox(9, new Label(names[i]), values[i]);
            card.getStyleClass().add("stat-card");
            card.getChildren().getFirst().getStyleClass().add("stat-label");
            grid.add(card, i % 4, i / 4);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }
        totalPatientsStat = values[0];
        waitingStat = values[1];
        outsideStat = values[2];
        treatingStat = values[3];
        completedStat = values[4];
        availableDoctorsStat = values[5];
        busyDoctorsStat = values[6];
        availableRoomsStat = values[7];
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    private VBox createWaitingCard() {
        VBox card = card(t("waiting.title"), t("waiting.subtitle"));
        HBox occupancy = new HBox(8);
        occupancy.setAlignment(Pos.CENTER_LEFT);
        occupancyLabel = new Label(occupancyText(0, 0));
        occupancyLabel.getStyleClass().add("section-value");
        occupancy.getChildren().add(occupancyLabel);
        seatsPane = new FlowPane(10, 10);
        seatsPane.getStyleClass().add("seat-grid");
        Label outsideTitle = new Label(t("waiting.outside"));
        outsideTitle.getStyleClass().add("section-label");
        outsidePatientsLabel = new Label(t("waiting.none"));
        outsidePatientsLabel.getStyleClass().add("outside-patients");
        card.getChildren().addAll(occupancy, seatsPane, divider(), outsideTitle, outsidePatientsLabel);
        return card;
    }

    private VBox createDoctorsCard() {
        VBox card = card(t("doctors.title"), t("doctors.subtitle"));
        doctorsPane = new VBox(10);
        Label empty = new Label(t("doctors.empty"));
        empty.getStyleClass().add("empty-hint");
        doctorsPane.getChildren().add(empty);
        card.getChildren().add(doctorsPane);
        return card;
    }

    private VBox createRoomsCard() {
        VBox card = card(t("rooms.title"), t("rooms.subtitle"));
        roomsPane = new HBox(12);
        roomsPane.setAlignment(Pos.CENTER_LEFT);
        int initialRooms = parsePositiveOrDefault(
                roomCountField == null ? null : roomCountField.getText(), 2);
        for (int room = 1; room <= initialRooms; room++) {
            roomsPane.getChildren().add(roomCard(room));
        }
        card.getChildren().add(roomsPane);
        return card;
    }

    private VBox createEventsCard() {
        VBox card = card(t("events.title"), t("events.subtitle"));
        eventList = new ListView<>(events);
        eventList.getStyleClass().add("event-list");
        eventList.setCellFactory(view -> new EventCell());
        eventList.setPrefHeight(360);
        eventList.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() > 0) {
                timelineAtBottom = false;
            } else if (event.getDeltaY() < 0) {
                Platform.runLater(() -> timelineAtBottom = isTimelineAtBottom());
            }
        });
        VBox.setVgrow(eventList, Priority.ALWAYS);
        card.getChildren().add(eventList);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox createConfigurationCard() {
        VBox card = card(t("config.title"), t("config.subtitle"));
        GridPane fields = new GridPane();
        fields.setHgap(14);
        fields.setVgap(12);
        capacityField = field("5");
        doctorCountField = field("3");
        patientCountField = field("12");
        roomCountField = field("2");
        minArrivalField = field("0.15");
        maxArrivalField = field("0.7");
        minTreatmentField = field("1");
        maxTreatmentField = field("5");
        addField(fields, t("field.capacity"), capacityField, 0, 0);
        addField(fields, t("field.doctors"), doctorCountField, 1, 0);
        addField(fields, t("field.patients"), patientCountField, 2, 0);
        addField(fields, t("field.rooms"), roomCountField, 3, 0);
        addField(fields, t("field.arrival.min"), minArrivalField, 0, 1);
        addField(fields, t("field.arrival.max"), maxArrivalField, 1, 1);
        addField(fields, t("field.treatment.min"), minTreatmentField, 2, 1);
        addField(fields, t("field.treatment.max"), maxTreatmentField, 3, 1);
        capacityField.setTooltip(new javafx.scene.control.Tooltip(t("field.capacity") + ": 1–30"));
        doctorCountField.setTooltip(new javafx.scene.control.Tooltip(t("field.doctors") + ": 1–20"));
        patientCountField.setTooltip(new javafx.scene.control.Tooltip(t("field.patients") + ": 1–200"));
        roomCountField.setTooltip(new javafx.scene.control.Tooltip(t("field.rooms") + ": 1–20"));
        minArrivalField.setTooltip(new javafx.scene.control.Tooltip(t("field.arrival.min")));
        maxArrivalField.setTooltip(new javafx.scene.control.Tooltip(t("field.arrival.max")));
        minTreatmentField.setTooltip(new javafx.scene.control.Tooltip(t("field.treatment.min") + ": 1–5"));
        maxTreatmentField.setTooltip(new javafx.scene.control.Tooltip(t("field.treatment.max") + ": 1–5"));
        roomCountField.textProperty().addListener((observable, oldValue, newValue) ->
                updateIdleRoomCount());

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        startButton = new Button(t("button.start"));
        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(event -> startSimulation());
        stopButton = new Button(t("button.stop"));
        stopButton.getStyleClass().add("quiet-button");
        stopButton.setOnAction(event -> stopSimulation());
        stopButton.setDisable(true);
        resetButton = new Button(t("button.reset"));
        resetButton.getStyleClass().add("quiet-button");
        resetButton.setOnAction(event -> resetSimulation());
        resetButton.setDisable(true);
        Label guidance = new Label(t("config.hint"));
        guidance.getStyleClass().add("form-hint");
        actions.getChildren().addAll(startButton, stopButton, resetButton, guidance);
        card.getChildren().addAll(fields, actions);
        return card;
    }

    private void startSimulation() {
        try {
            SimulationConfig config = new SimulationConfig(
                    readInteger(capacityField, t("field.capacity"), 1, 30),
                    readInteger(doctorCountField, t("field.doctors"), 1, 20),
                    readInteger(patientCountField, t("field.patients"), 1, 200),
                    readInteger(roomCountField, t("field.rooms"), 1, 20),
                    readSecondsMillis(minArrivalField, t("field.arrival.min"), 0, 60),
                    readSecondsMillis(maxArrivalField, t("field.arrival.max"), 0, 60),
                    readSecondsMillis(minTreatmentField, t("field.treatment.min"), 1, 5),
                    readSecondsMillis(maxTreatmentField, t("field.treatment.max"), 1, 5));
            if (controller != null) {
                controller.stop();
            }
            clearDashboard();
            setInputsDisabled(true);
            startButton.setDisable(true);
            stopButton.setDisable(false);
            resetButton.setDisable(true);
            SimulationObserver observer = this::onSimulationUpdate;
            controller = new SimulationController(config, observer);
            controller.start();
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void resetSimulation() {
        if (latestSnapshot != null && latestSnapshot.status() == Status.RUNNING) {
            return;
        }
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        clearDashboard();
        renderIdle();
        setInputsDisabled(false);
        startButton.setDisable(false);
        stopButton.setDisable(true);
        resetButton.setDisable(true);
    }

    private void onSimulationUpdate(SimulationSnapshot snapshot, SimulationEvent event) {
        if (!uiActive) {
            return;
        }
        try {
            Platform.runLater(() -> renderUpdate(snapshot, event));
        } catch (IllegalStateException exception) {
            if (uiActive) {
                throw exception;
            }
        }
    }

    private void renderUpdate(SimulationSnapshot snapshot, SimulationEvent event) {
        latestSnapshot = snapshot;
        updateStatus(snapshot.status());
        totalPatientsStat.setText(Integer.toString(snapshot.config().patientCount()));
        waitingStat.setText(Integer.toString(snapshot.patientsWaiting()));
        outsideStat.setText(Integer.toString(snapshot.patientsOutside()));
        treatingStat.setText(Integer.toString(snapshot.patientsBeingTreated()));
        completedStat.setText(Integer.toString(snapshot.patientsCompleted()));
        availableDoctorsStat.setText(Integer.toString(snapshot.availableDoctors()));
        busyDoctorsStat.setText(Integer.toString(snapshot.busyDoctors()));
        availableRoomsStat.setText(Integer.toString(snapshot.availableTreatmentRooms()));
        occupancyLabel.setText(occupancyText(
                snapshot.patientsWaiting(), snapshot.config().waitingRoomCapacity()));
        renderSeats(snapshot);
        renderOutside(snapshot);
        renderDoctors(snapshot);
        renderRooms(snapshot);

        if (event != null) {
            events.add(event);
        }
        eventList.refresh();
        if (event != null && timelineAtBottom) {
            eventList.scrollTo(events.size() - 1);
        }
        if (snapshot.status() == Status.COMPLETED) {
            completionBanner.setText(t("completion"));
            completionBanner.setVisible(true);
            completionBanner.setManaged(true);
            setInputsDisabled(true);
            startButton.setDisable(true);
            stopButton.setDisable(true);
            resetButton.setDisable(false);
        } else if (snapshot.status() == Status.STOPPED) {
            setInputsDisabled(false);
            startButton.setDisable(false);
            stopButton.setDisable(true);
            resetButton.setDisable(false);
        } else if (snapshot.status() == Status.RUNNING) {
            setInputsDisabled(true);
            startButton.setDisable(true);
            stopButton.setDisable(false);
            resetButton.setDisable(true);
        }
    }

    private boolean isTimelineAtBottom() {
        return eventList.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scrollBar -> scrollBar.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .map(scrollBar -> scrollBar.getValue() >= scrollBar.getMax() - 0.01)
                .orElse(true);
    }

    private void renderSeats(SimulationSnapshot snapshot) {
        if (seatLabels.size() != snapshot.config().waitingRoomCapacity()) {
            seatLabels.clear();
            seatsPane.getChildren().clear();
            for (int i = 0; i < snapshot.config().waitingRoomCapacity(); i++) {
                Label seat = new Label(t("seat.available"));
                seat.getStyleClass().add("seat-empty");
                seat.setMinSize(86, 54);
                seat.setAlignment(Pos.CENTER);
                seatLabels.add(seat);
                seatsPane.getChildren().add(seat);
            }
        }
        for (int i = 0; i < seatLabels.size(); i++) {
            Label seat = seatLabels.get(i);
            if (i < snapshot.waitingPatients().size()) {
                String patient = snapshot.waitingPatients().get(i);
                boolean changed = !seat.getText().equals(patient);
                seat.setText(patient);
                seat.getStyleClass().remove("seat-empty");
                if (!seat.getStyleClass().contains("seat-occupied")) {
                    seat.getStyleClass().add("seat-occupied");
                }
                if (changed) {
                    animateSeat(seat);
                }
            } else {
                boolean changed = !seat.getText().equals(t("seat.available"));
                seat.setText(t("seat.available"));
                seat.getStyleClass().remove("seat-occupied");
                if (!seat.getStyleClass().contains("seat-empty")) {
                    seat.getStyleClass().add("seat-empty");
                }
                if (changed) {
                    animateSeat(seat);
                }
            }
        }
    }

    private void animateSeat(Label seat) {
        FadeTransition transition = new FadeTransition(Duration.millis(180), seat);
        transition.setFromValue(0.55);
        transition.setToValue(1.0);
        transition.play();
    }

    private void renderOutside(SimulationSnapshot snapshot) {
        if (snapshot.patientsOutside() == 0) {
            outsidePatientsLabel.setText(t("waiting.none"));
            return;
        }
        outsidePatientsLabel.setText(String.join("  ·  ", snapshot.outsidePatientIds()));
    }

    private void renderDoctors(SimulationSnapshot snapshot) {
        if (doctorCards.size() != snapshot.doctors().size()) {
            doctorCards.clear();
            doctorsPane.getChildren().clear();
            for (SimulationSnapshot.Doctor doctor : snapshot.doctors()) {
                DoctorCard card = new DoctorCard(doctor.id());
                doctorCards.put(doctor.id(), card);
                doctorsPane.getChildren().add(card.container);
            }
        }
        for (SimulationSnapshot.Doctor doctor : snapshot.doctors()) {
            doctorCards.get(doctor.id()).update(doctor);
        }
    }

    private void renderRooms(SimulationSnapshot snapshot) {
        roomProgressBars.clear();
        roomsPane.getChildren().clear();
        for (SimulationSnapshot.TreatmentRoom room : snapshot.treatmentRooms()) {
            VBox tile = new VBox(10);
            tile.getStyleClass().addAll("room-tile",
                    room.status() == RoomStatus.OCCUPIED ? "room-occupied" : "room-available");
            Label title = new Label(t("room.title") + " " + room.number());
            title.getStyleClass().add("room-title");
            Label state = new Label(t(room.status() == RoomStatus.OCCUPIED
                    ? "room.occupied" : "room.available"));
            state.getStyleClass().add("room-state");
            Label assignment = new Label(room.status() == RoomStatus.OCCUPIED
                    ? t("doctor.prefix") + " " + room.doctorId() + "  ·  " + room.patientId()
                    : t("room.ready"));
            assignment.getStyleClass().add("room-assignment");
            tile.getChildren().addAll(title, state, assignment);
            if (room.status() == RoomStatus.OCCUPIED) {
                ProgressBar progress = new ProgressBar(0);
                progress.getStyleClass().addAll("treatment-progress", "room-progress");
                roomProgressBars.put(room.number(), progress);
                tile.getChildren().add(progress);
            }
            HBox.setHgrow(tile, Priority.ALWAYS);
            roomsPane.getChildren().add(tile);
        }
    }

    private void renderIdle() {
        statusBadge.setText(t("status.idle"));
        statusBadge.getStyleClass().removeAll(
                "status-running", "status-completed", "status-stopped", "status-idle");
        if (!statusBadge.getStyleClass().contains("status-idle")) {
            statusBadge.getStyleClass().add("status-idle");
        }
        waitingStat.setText("0");
        totalPatientsStat.setText("0");
        outsideStat.setText("0");
        treatingStat.setText("0");
        completedStat.setText("0");
        availableDoctorsStat.setText("0");
        busyDoctorsStat.setText("0");
        availableRoomsStat.setText(Integer.toString(
                parsePositiveOrDefault(roomCountField.getText(), 2)));
        occupancyLabel.setText(occupancyText(0, 0));
        outsidePatientsLabel.setText(t("waiting.none"));
        seatsPane.getChildren().clear();
        seatLabels.clear();
        doctorsPane.getChildren().setAll(new Label(t("doctors.empty")));
        roomsPane.getChildren().clear();
        int roomCount = parsePositiveOrDefault(roomCountField.getText(), 2);
        for (int room = 1; room <= roomCount; room++) {
            roomsPane.getChildren().add(roomCard(room));
        }
        roomProgressBars.clear();
        completionBanner.setVisible(false);
        completionBanner.setManaged(false);
        events.clear();
        doctorCards.clear();
        latestSnapshot = null;
    }

    private void clearDashboard() {
        events.clear();
        doctorCards.clear();
        seatLabels.clear();
        seatsPane.getChildren().clear();
        doctorsPane.getChildren().clear();
        latestSnapshot = null;
    }

    private void updateStatus(Status status) {
        statusBadge.setText(DashboardText.status(status));
        statusBadge.getStyleClass().removeAll(
                "status-idle", "status-running", "status-completed", "status-stopped");
        statusBadge.getStyleClass().add(switch (status) {
            case IDLE -> "status-idle";
            case RUNNING -> "status-running";
            case COMPLETED -> "status-completed";
            case STOPPED -> "status-stopped";
        });
    }

    private void startProgressTimer() {
        progressTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (latestSnapshot == null || latestSnapshot.status() != Status.RUNNING) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                for (SimulationSnapshot.Doctor doctor : latestSnapshot.doctors()) {
                    DoctorCard card = doctorCards.get(doctor.id());
                    if (card != null && doctor.status() == DoctorStatus.TREATING) {
                        double elapsed = currentTime - doctor.treatmentStartedAtMillis();
                        card.progress.setProgress(Math.min(1.0,
                                elapsed / doctor.treatmentDurationMillis()));
                    }
                }
                for (SimulationSnapshot.TreatmentRoom room : latestSnapshot.treatmentRooms()) {
                    if (room.status() != RoomStatus.OCCUPIED) {
                        continue;
                    }
                    ProgressBar progress = roomProgressBars.get(room.number());
                    SimulationSnapshot.Doctor doctor = latestSnapshot.doctors().stream()
                            .filter(candidate -> candidate.id() == room.doctorId())
                            .findFirst()
                            .orElseThrow();
                    if (progress != null) {
                        double elapsed = currentTime - doctor.treatmentStartedAtMillis();
                        progress.setProgress(Math.min(1.0,
                                elapsed / doctor.treatmentDurationMillis()));
                    }
                }
            }
        };
        progressTimer.start();
    }

    private void applyTheme() {
        if (root == null) {
            return;
        }
        String theme = darkTheme ? DARK_THEME : LIGHT_THEME;
        root.getStylesheets().setAll(getClass().getResource(theme).toExternalForm());
        if (themeButton != null) {
            themeButton.setText(darkTheme ? "☀" : "☾");
            themeButton.setTooltip(new javafx.scene.control.Tooltip(
                    t(darkTheme ? "theme.light" : "theme.dark")));
            themeButton.setAccessibleText(t(darkTheme ? "theme.light" : "theme.dark"));
        }
    }

    private void updateFullScreenButton(boolean fullScreen) {
        if (fullScreenButton != null) {
            fullScreenButton.setText(t(fullScreen ? "fullscreen.exit" : "fullscreen.enter"));
        }
    }

    private String t(String key) {
        return DashboardText.text(key);
    }

    private String occupancyText(int occupied, int capacity) {
        return occupied + " / " + capacity + " " + t("waiting.seats");
    }

        private void updateIdleRoomCount() {
            if (latestSnapshot != null || roomsPane == null || roomCountField == null) {
                return;
            }
            roomsPane.getChildren().clear();
            int roomCount = parsePositiveOrDefault(roomCountField.getText(), 2);
            for (int room = 1; room <= roomCount; room++) {
                roomsPane.getChildren().add(roomCard(room));
            }
            availableRoomsStat.setText(Integer.toString(roomCount));
        }

    private static int parsePositiveOrDefault(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 && parsed <= 20 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private VBox card(String title, String subtitle) {
        VBox box = new VBox(14);
        box.getStyleClass().add("surface-card");
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        Label subheading = new Label(subtitle);
        subheading.getStyleClass().add("section-subtitle");
        VBox titleBox = new VBox(4, heading, subheading);
        box.getChildren().add(titleBox);
        return box;
    }

    private VBox roomCard(int roomNumber) {
        VBox tile = new VBox(10);
        tile.getStyleClass().addAll("room-tile", "room-available");
        Label title = new Label(t("room.title") + " " + roomNumber);
        title.getStyleClass().add("room-title");
        Label state = new Label(t("room.available"));
        state.getStyleClass().add("room-state");
        Label patient = new Label(t("room.ready"));
        patient.getStyleClass().add("room-assignment");
        tile.getChildren().addAll(title, state, patient);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private Region divider() {
        Region line = new Region();
        line.getStyleClass().add("divider");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        return line;
    }

    private TextField field(String value) {
        TextField field = new TextField(value);
        field.getStyleClass().add("config-input");
        field.setPrefColumnCount(8);
        return field;
    }

    private void addField(GridPane grid, String labelText, TextField input, int column, int row) {
        VBox group = new VBox(6, new Label(labelText), input);
        group.getStyleClass().add("field-group");
        group.getChildren().getFirst().getStyleClass().add("field-label");
        grid.add(group, column, row);
        GridPane.setHgrow(group, Priority.ALWAYS);
    }

    private int readInteger(TextField field, String label, int minimum, int maximum) {
        int value;
        try {
            value = Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(String.format(
                    t("validation.integer"), label));
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(String.format(
                    t("validation.range"), label, minimum, maximum));
        }
        return value;
    }

    private int readSecondsMillis(TextField field, String label, int minimumSeconds, int maximumSeconds) {
        String input = field.getText().trim().replace(',', '.');
        if (!input.matches("\\d+(?:\\.\\d{1,3})?")) {
            throw new IllegalArgumentException(String.format(t("validation.seconds"), label));
        }
        double seconds;
        try {
            seconds = Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(String.format(t("validation.seconds"), label));
        }
        if (!Double.isFinite(seconds) || seconds < minimumSeconds || seconds > maximumSeconds) {
            throw new IllegalArgumentException(String.format(
                    t("validation.range"), label, minimumSeconds, maximumSeconds));
        }
        return (int) Math.round(seconds * 1_000);
    }

    private void setInputsDisabled(boolean disabled) {
        for (TextField field : List.of(capacityField, doctorCountField, patientCountField, roomCountField,
                minArrivalField, maxArrivalField, minTreatmentField, maxTreatmentField)) {
            field.setDisable(disabled);
        }
    }

    private void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(t("alert.title"));
        alert.setHeaderText(t("alert.header"));
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().setAll(root.getStylesheets());
        alert.showAndWait();
    }

    private final class DoctorCard {
        private final VBox container = new VBox(8);
        private final Label status = new Label(DashboardText.doctorStatus(
                SimulationSnapshot.DoctorStatus.IDLE));
        private final Label patient = new Label(t("doctor.no.patient"));
        private final ProgressBar progress = new ProgressBar(0);

        private DoctorCard(int id) {
            container.getStyleClass().add("doctor-card");
            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(t("doctor.prefix") + " " + id);
            name.getStyleClass().add("doctor-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            status.getStyleClass().add("doctor-status");
            header.getChildren().addAll(name, spacer, status);
            patient.getStyleClass().add("doctor-patient");
            progress.getStyleClass().add("treatment-progress");
            container.getChildren().addAll(header, patient, progress);
        }

        private void update(SimulationSnapshot.Doctor doctor) {
            status.setText(DashboardText.doctorStatus(doctor.status()));
            status.getStyleClass().removeAll("doctor-idle", "doctor-waiting-for-room",
                    "doctor-treating", "doctor-available", "doctor-stopped");
            status.getStyleClass().add("doctor-"
                    + doctor.status().name().toLowerCase().replace('_', '-'));
            patient.setText(doctor.patientId() == null
                    ? t("doctor.no.patient")
                    : t("doctor.current.patient") + "  ·  " + doctor.patientId());
            boolean treating = doctor.status() == DoctorStatus.TREATING;
            progress.setVisible(treating);
            progress.setManaged(treating);
            if (!treating) {
                progress.setProgress(0);
            }
        }
    }

    private final class EventCell extends ListCell<SimulationEvent> {
        @Override
        protected void updateItem(SimulationEvent event, boolean empty) {
            super.updateItem(event, empty);
            if (empty || event == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label time = new Label(EVENT_TIME.format(event.timestamp()));
            time.getStyleClass().add("event-time");
            Label category = new Label(DashboardText.category(event.category()));
            category.getStyleClass().addAll("event-category",
                    "event-" + event.category().name().toLowerCase().replace('_', '-'));
            Label message = new Label(DashboardText.eventMessage(event));
            message.getStyleClass().add("event-message");
            message.setWrapText(true);
            HBox row = new HBox(10, time, category, message);
            row.getStyleClass().add("event-row");
            row.setAlignment(Pos.TOP_LEFT);
            setGraphic(row);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
