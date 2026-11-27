package hospital.ui;

import hospital.simulation.SimulationConfig;
import hospital.simulation.SimulationController;
import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationObserver;
import hospital.simulation.SimulationReport;
import hospital.simulation.SimulationSnapshot;
import hospital.simulation.WordReportExporter;
import hospital.update.UpdateChecker;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.CacheHint;
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
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final int MAX_DISPLAYED_EVENTS = 1_500;
    private static final long PROGRESS_UPDATE_INTERVAL_NANOS = 33_000_000L;

    private final javafx.collections.ObservableList<SimulationEvent> events =
            FXCollections.observableArrayList();
    private final List<SimulationEvent> allEvents = new ArrayList<>();
    private final Map<Integer, DoctorCard> doctorCards = new HashMap<>();
    private final Map<Integer, RoomCard> roomCards = new HashMap<>();
    private final List<SeatSlot> seatSlots = new ArrayList<>();
    private final Object pendingLock = new Object();
    private final List<SimulationEvent> pendingEvents = new ArrayList<>();
    private SimulationSnapshot pendingSnapshot;
    private long currentGeneration;
    private long pendingGeneration;
    private boolean flushScheduled;
    private SimulationController controller;
    private Stage stage;
    private BorderPane root;
    private ScrollPane dashboardScroll;
    private VBox dashboardContent;
    private GridPane liveOperationsGrid;
    private ScrollBar eventListVerticalScrollBar;
    private Timeline autoScrollTimeline;
    private boolean pendingScrollToLiveView;
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
    private Button reportButton;
    private Button themeButton;
    private Button fullScreenButton;
    private boolean darkTheme;
    private boolean timelineAtBottom = true;
    private volatile boolean uiActive = true;
    private SimulationSnapshot latestSnapshot;
    private AnimationTimer progressTimer;
    private long lastProgressUpdateNanos;
    private Status lastRenderedStatus;
    private String lastOutsideText;
    private int lastWaitingOccupancy = -1;
    private int lastWaitingCapacity = -1;
    private int lastTotalPatients = -1;
    private int lastWaiting = -1;
    private int lastOutside = -1;
    private int lastTreating = -1;
    private int lastCompleted = -1;
    private int lastAvailableDoctors = -1;
    private int lastBusyDoctors = -1;
    private int lastAvailableRooms = -1;

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
        if (stopButton.isFocused() && root != null) {
            root.requestFocus();
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
        mark.setCache(true);
        mark.setCacheHint(CacheHint.SPEED);
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
        reportButton = new Button(t("button.report"));
        reportButton.getStyleClass().add("quiet-button");
        reportButton.setOnAction(event -> openReportWindow());
        header.getChildren().addAll(
                mark, titleBlock, spacer, statusBadge, updateButton, reportButton,
                themeButton, fullScreenButton);
        return header;
    }

    private void openReportWindow() {
        if (latestSnapshot == null) {
            showUpdateMessage(Alert.AlertType.INFORMATION, t("report.unavailable"));
            return;
        }
        SimulationReport report = SimulationReport.from(latestSnapshot, List.copyOf(allEvents));
        Stage reportStage = new Stage();
        reportStage.initOwner(stage);
        reportStage.setTitle(t("report.title"));
        reportStage.getIcons().add(new Image(getClass().getResourceAsStream(APP_ICON)));

        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.getStyleClass().add("dashboard-content");
        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox title = new VBox(4);
        Label headingLabel = new Label(t("report.title"));
        headingLabel.getStyleClass().add("page-heading");
        Label summary = new Label(reportSummary(report));
        summary.getStyleClass().add("page-description");
        title.getChildren().addAll(headingLabel, summary);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = new Button(t("report.refresh"));
        refresh.getStyleClass().add("quiet-button");
        refresh.setOnAction(event -> {
            if (latestSnapshot == null) {
                showUpdateMessage(Alert.AlertType.INFORMATION, t("report.unavailable"));
            } else {
                reportStage.getScene().setRoot(createReportRoot(reportStage));
            }
        });
        Button export = new Button(t("report.export"));
        export.getStyleClass().add("primary-button");
        export.setOnAction(event -> exportCurrentReport());
        heading.getChildren().addAll(title, spacer, refresh, export);
        page.getChildren().add(heading);
        page.getChildren().addAll(createReportMetrics(report), reportNarrative(report),
                createReportCharts(report));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("main-scroll", "app-root");
        reportStage.setScene(new Scene(scroll, 1180, 900));
        reportStage.getScene().getStylesheets().setAll(
                getClass().getResource(darkTheme ? DARK_THEME : LIGHT_THEME).toExternalForm());
        reportStage.setMinWidth(840);
        reportStage.setMinHeight(620);
        reportStage.show();
    }

    private ScrollPane createReportRoot(Stage reportStage) {
        SimulationReport report = SimulationReport.from(latestSnapshot, List.copyOf(allEvents));
        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.getStyleClass().add("dashboard-content");
        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox title = new VBox(4);
        Label headingLabel = new Label(t("report.title"));
        headingLabel.getStyleClass().add("page-heading");
        Label summary = new Label(reportSummary(report));
        summary.getStyleClass().add("page-description");
        title.getChildren().addAll(headingLabel, summary);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = new Button(t("report.refresh"));
        refresh.getStyleClass().add("quiet-button");
        refresh.setOnAction(event -> {
            if (latestSnapshot == null) {
                showUpdateMessage(Alert.AlertType.INFORMATION, t("report.unavailable"));
            } else {
                reportStage.getScene().setRoot(createReportRoot(reportStage));
            }
        });
        Button export = new Button(t("report.export"));
        export.getStyleClass().add("primary-button");
        export.setOnAction(event -> exportCurrentReport());
        heading.getChildren().addAll(title, spacer, refresh, export);
        page.getChildren().addAll(heading, createReportMetrics(report), reportNarrative(report),
                createReportCharts(report));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("main-scroll", "app-root");
        return scroll;
    }

    private GridPane createReportMetrics(SimulationReport report) {
        String[] labels = {
                t("report.completed"), t("report.completion.rate"),
                t("report.average.treatment"), t("report.simulation.duration"),
                t("report.events"), t("report.warnings")
        };
        String[] values = {
                report.completedPatients() + " / " + report.totalPatients(),
                String.format(java.util.Locale.ROOT, "%.1f%%", report.completionPercent()),
                String.format(java.util.Locale.ROOT, "%.2f s", report.averageTreatmentSeconds()),
                String.format(java.util.Locale.ROOT, "%.2f s", report.simulationSeconds()),
                Integer.toString(report.eventCount()),
                Integer.toString(report.warnings())
        };
        GridPane metrics = new GridPane();
        metrics.setHgap(12);
        metrics.setVgap(12);
        for (int i = 0; i < labels.length; i++) {
            Label value = new Label(values[i]);
            value.getStyleClass().add("stat-value");
            Label label = new Label(labels[i]);
            label.getStyleClass().add("stat-label");
            VBox card = new VBox(8, label, value);
            card.getStyleClass().add("stat-card");
            card.setCache(true);
            card.setCacheHint(CacheHint.DEFAULT);
            metrics.add(card, i % 3, i / 3);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }
        for (int column = 0; column < 3; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / 3);
            metrics.getColumnConstraints().add(constraint);
        }
        return metrics;
    }

    private VBox createReportCharts(SimulationReport report) {
        VBox charts = new VBox(16);
        HBox upperCharts = new HBox(16);
        PieChart distribution = new PieChart(FXCollections.observableArrayList(
                report.patientGroups().stream()
                        .map(group -> new PieChart.Data(group.label(), group.count()))
                        .toList()));
        distribution.setTitle(t("report.outcomes"));
        distribution.setLegendVisible(true);
        distribution.setLabelsVisible(true);
        distribution.setMinHeight(320);
        distribution.getStyleClass().add("report-chart");

        BarChart<String, Number> patientBars = createBarChart(t("report.patients"));
        XYChart.Series<String, Number> patientSeries = new XYChart.Series<>();
        patientSeries.setName(t("report.count"));
        report.patientGroups().stream()
                .filter(group -> group.count() > 0)
                .forEach(group -> patientSeries.getData().add(
                        new XYChart.Data<>(group.label(), group.count())));
        patientBars.getData().add(patientSeries);
        patientBars.setMinHeight(320);
        HBox.setHgrow(distribution, Priority.ALWAYS);
        HBox.setHgrow(patientBars, Priority.ALWAYS);
        upperCharts.getChildren().addAll(distribution, patientBars);

        BarChart<String, Number> histogram = createBarChart(t("report.duration"));
        XYChart.Series<String, Number> durationSeries = new XYChart.Series<>();
        durationSeries.setName(t("report.treatments"));
        report.treatmentHistogram().forEach(bin -> durationSeries.getData().add(
                new XYChart.Data<>(bin.label(), bin.count())));
        histogram.getData().add(durationSeries);
        histogram.setTitle(t("report.histogram"));
        histogram.setMinHeight(300);
        charts.getChildren().addAll(upperCharts, histogram);
        return charts;
    }

    private BarChart<String, Number> createBarChart(String yAxisTitle) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yAxisTitle);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(12);
        chart.setBarGap(3);
        chart.setMinWidth(390);
        return chart;
    }

    private Label reportNarrative(SimulationReport report) {
        Label narrative = new Label(report.narrative());
        narrative.setWrapText(true);
        narrative.getStyleClass().add("report-narrative");
        return narrative;
    }

    private String reportSummary(SimulationReport report) {
        return String.format(java.util.Locale.ROOT, "%s · %.1f%% %s · %d %s",
                DashboardText.status(report.status()), report.completionPercent(),
                t("report.complete"), report.totalPatients(), t("report.patients.lower"));
    }

    private void exportReport(SimulationReport report) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("report.save"));
        chooser.setInitialFileName("ERONIX-report.docx");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Word document (*.docx)", "*.docx"));
        File destination = chooser.showSaveDialog(stage);
        if (destination == null) {
            return;
        }

        try {
            Files.write(destination.toPath(), WordReportExporter.toDocx(report, Instant.now()));
            Alert alert = new Alert(Alert.AlertType.INFORMATION, t("report.saved"));
            alert.setTitle(t("report.title"));
            alert.setHeaderText(null);
            alert.showAndWait();
        } catch (IOException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    t("report.save.error") + "\n" + exception.getMessage());
            alert.setTitle(t("report.title"));
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    private void exportCurrentReport() {
        if (latestSnapshot == null) {
            showUpdateMessage(Alert.AlertType.INFORMATION, t("report.unavailable"));
            return;
        }
        exportReport(SimulationReport.from(latestSnapshot, List.copyOf(allEvents)));
    }

    private void checkForUpdates(Button button) {
        button.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                String version = System.getProperty("eronix.version", "1.0.6");
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

        Region waitingCard = createWaitingCard();
        Region doctorsCard = createDoctorsCard();
        Region roomsCard = createRoomsCard();
        Region eventsCard = createEventsCard();
        GridPane.setHgrow(waitingCard, Priority.ALWAYS);
        GridPane.setHgrow(doctorsCard, Priority.ALWAYS);
        mainGrid.add(waitingCard, 0, 0);
        mainGrid.add(doctorsCard, 1, 0);
        mainGrid.add(roomsCard, 0, 1);
        mainGrid.add(eventsCard, 1, 1);

        content.getChildren().addAll(intro, createConfigurationCard(), stats, mainGrid);
        dashboardContent = content;
        liveOperationsGrid = mainGrid;
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("main-scroll");
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> stopAutoScroll());
        dashboardScroll = scrollPane;
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
            card.setCache(true);
            card.setCacheHint(CacheHint.DEFAULT);
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

    private Region createWaitingCard() {
        SurfaceCard card = createSurfaceCard(t("waiting.title"), t("waiting.subtitle"));
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
        card.body().getChildren().addAll(
                occupancy, seatsPane, divider(), outsideTitle, outsidePatientsLabel);
        return card.root();
    }

    private Region createDoctorsCard() {
        SurfaceCard card = createSurfaceCard(t("doctors.title"), t("doctors.subtitle"));
        doctorsPane = new VBox(10);
        Label empty = new Label(t("doctors.empty"));
        empty.getStyleClass().add("empty-hint");
        doctorsPane.getChildren().add(empty);
        card.body().getChildren().add(doctorsPane);
        return card.root();
    }

    private Region createRoomsCard() {
        SurfaceCard card = createSurfaceCard(t("rooms.title"), t("rooms.subtitle"));
        roomsPane = new HBox(12);
        roomsPane.setAlignment(Pos.CENTER_LEFT);
        int initialRooms = parsePositiveOrDefault(
                roomCountField == null ? null : roomCountField.getText(), 2);
        populateIdleRoomCards(initialRooms);
        card.body().getChildren().add(roomsPane);
        return card.root();
    }

    private Region createEventsCard() {
        SurfaceCard card = createSurfaceCard(t("events.title"), t("events.subtitle"));
        eventList = new ListView<>(events);
        eventList.getStyleClass().add("event-list");
        eventList.setCellFactory(view -> new EventCell());
        eventList.setPrefHeight(360);
        eventList.addEventFilter(ScrollEvent.SCROLL, this::handleEventListScroll);
        VBox.setVgrow(eventList, Priority.ALWAYS);
        card.body().getChildren().add(eventList);
        VBox.setVgrow(card.root(), Priority.ALWAYS);
        return card.root();
    }

    private void handleEventListScroll(ScrollEvent event) {
        double deltaY = event.getDeltaY();
        if (deltaY == 0) {
            return;
        }
        ScrollBar verticalBar = findEventListVerticalScrollBar();
        boolean canScrollInDirection = verticalBar != null
                && verticalBar.isVisible()
                && verticalBar.getMax() > verticalBar.getMin()
                && (deltaY > 0
                        ? verticalBar.getValue() > verticalBar.getMin() + 0.001
                        : verticalBar.getValue() < verticalBar.getMax() - 0.001);
        if (!canScrollInDirection) {
            if (dashboardScroll != null) {
                event.consume();
                dashboardScroll.fireEvent(event.copyFor(dashboardScroll, dashboardScroll));
            }
            return;
        }
        if (deltaY > 0) {
            timelineAtBottom = false;
        } else {
            Platform.runLater(() -> timelineAtBottom = isTimelineAtBottom());
        }
    }

    private Region createConfigurationCard() {
        SurfaceCard card = createSurfaceCard(t("config.title"), t("config.subtitle"));
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
        card.body().getChildren().addAll(fields, actions);
        return card.root();
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
            long generation = bumpGeneration();
            clearDashboard();
            pendingScrollToLiveView = true;
            setInputsDisabled(true);
            startButton.setDisable(true);
            stopButton.setDisable(false);
            resetButton.setDisable(true);
            SimulationObserver observer = (snapshot, event) ->
                    onSimulationUpdate(generation, snapshot, event);
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
        pendingScrollToLiveView = false;
        stopAutoScroll();
        bumpGeneration();
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        clearDashboard();
        renderIdle();
        setInputsDisabled(false);
        startButton.setDisable(false);
        if (resetButton.isFocused()) {
            startButton.requestFocus();
        }
        stopButton.setDisable(true);
        resetButton.setDisable(true);
    }

    private long bumpGeneration() {
        synchronized (pendingLock) {
            currentGeneration++;
            pendingEvents.clear();
            pendingSnapshot = null;
            return currentGeneration;
        }
    }

    private void onSimulationUpdate(
            long generation, SimulationSnapshot snapshot, SimulationEvent event) {
        if (!uiActive) {
            return;
        }
        boolean schedule = false;
        synchronized (pendingLock) {
            if (generation != currentGeneration) {
                return;
            }
            pendingGeneration = generation;
            pendingSnapshot = snapshot;
            if (event != null) {
                pendingEvents.add(event);
            }
            if (!flushScheduled) {
                flushScheduled = true;
                schedule = true;
            }
        }
        if (schedule) {
            try {
                Platform.runLater(this::flushPendingUpdates);
            } catch (IllegalStateException exception) {
                synchronized (pendingLock) {
                    flushScheduled = false;
                }
                if (uiActive) {
                    throw exception;
                }
            }
        }
    }

    private void flushPendingUpdates() {
        SimulationSnapshot snapshot;
        List<SimulationEvent> batch;
        synchronized (pendingLock) {
            flushScheduled = false;
            if (!uiActive || pendingGeneration != currentGeneration || pendingSnapshot == null) {
                pendingEvents.clear();
                pendingSnapshot = null;
                return;
            }
            snapshot = pendingSnapshot;
            batch = pendingEvents.isEmpty() ? List.of() : new ArrayList<>(pendingEvents);
            pendingEvents.clear();
            pendingSnapshot = null;
        }
        renderUpdate(snapshot, batch);
    }

    private void renderUpdate(SimulationSnapshot snapshot, List<SimulationEvent> newEvents) {
        latestSnapshot = snapshot;
        updateStatValue(totalPatientsStat, snapshot.config().patientCount(), lastTotalPatients);
        lastTotalPatients = snapshot.config().patientCount();
        int waitingCount = snapshot.patientsWaiting();
        updateStatValue(waitingStat, waitingCount, lastWaiting);
        lastWaiting = waitingCount;
        int outsideCount = snapshot.patientsOutside();
        updateStatValue(outsideStat, outsideCount, lastOutside);
        lastOutside = outsideCount;
        int treatingCount = snapshot.patientsBeingTreated();
        updateStatValue(treatingStat, treatingCount, lastTreating);
        lastTreating = treatingCount;
        int completedCount = snapshot.patientsCompleted();
        updateStatValue(completedStat, completedCount, lastCompleted);
        lastCompleted = completedCount;
        int availableDoctorCount = snapshot.availableDoctors();
        updateStatValue(availableDoctorsStat, availableDoctorCount, lastAvailableDoctors);
        lastAvailableDoctors = availableDoctorCount;
        int busyDoctorCount = snapshot.busyDoctors();
        updateStatValue(busyDoctorsStat, busyDoctorCount, lastBusyDoctors);
        lastBusyDoctors = busyDoctorCount;
        int availableRoomCount = snapshot.availableTreatmentRooms();
        updateStatValue(availableRoomsStat, availableRoomCount, lastAvailableRooms);
        lastAvailableRooms = availableRoomCount;

        int capacity = snapshot.config().waitingRoomCapacity();
        if (waitingCount != lastWaitingOccupancy || capacity != lastWaitingCapacity) {
            occupancyLabel.setText(occupancyText(waitingCount, capacity));
            lastWaitingOccupancy = waitingCount;
            lastWaitingCapacity = capacity;
        }
        renderSeats(snapshot);
        renderOutside(snapshot);
        renderDoctors(snapshot);
        renderRooms(snapshot);

        if (!newEvents.isEmpty()) {
            allEvents.addAll(newEvents);
            events.addAll(newEvents);
            trimDisplayedEvents(events, MAX_DISPLAYED_EVENTS);
            if (timelineAtBottom) {
                eventList.scrollTo(events.size() - 1);
            }
        }

        Status status = snapshot.status();
        if (status != lastRenderedStatus) {
            lastRenderedStatus = status;
            updateStatus(status);
            if (status == Status.COMPLETED) {
                completionBanner.setText(t("completion"));
                completionBanner.setVisible(true);
                completionBanner.setManaged(true);
                setInputsDisabled(true);
                startButton.setDisable(true);
                stopButton.setDisable(true);
                resetButton.setDisable(false);
            } else if (status == Status.STOPPED) {
                setInputsDisabled(false);
                startButton.setDisable(false);
                stopButton.setDisable(true);
                resetButton.setDisable(false);
            } else if (status == Status.RUNNING) {
                setInputsDisabled(true);
                startButton.setDisable(true);
                stopButton.setDisable(false);
                resetButton.setDisable(true);
            }
        }
        if (pendingScrollToLiveView && status == Status.RUNNING) {
            pendingScrollToLiveView = false;
            Platform.runLater(this::scrollToLiveOperations);
        }
    }

    private void scrollToLiveOperations() {
        if (dashboardScroll == null || dashboardContent == null || liveOperationsGrid == null) {
            return;
        }
        dashboardScroll.applyCss();
        dashboardScroll.layout();
        double targetVvalue = computeScrollTargetVvalue(
                liveOperationsGrid.getBoundsInParent().getMinY(),
                22.0,
                dashboardContent.getBoundsInLocal().getHeight(),
                dashboardScroll.getViewportBounds().getHeight());
        stopAutoScroll();
        if (Math.abs(dashboardScroll.getVvalue() - targetVvalue) < 0.002) {
            dashboardScroll.setVvalue(targetVvalue);
            return;
        }
        autoScrollTimeline = new Timeline(new KeyFrame(
                Duration.millis(260),
                new KeyValue(dashboardScroll.vvalueProperty(), targetVvalue, Interpolator.EASE_OUT)));
        autoScrollTimeline.play();
    }

    private void stopAutoScroll() {
        if (autoScrollTimeline != null) {
            autoScrollTimeline.stop();
            autoScrollTimeline = null;
        }
    }

    static double computeScrollTargetVvalue(
            double targetMinY, double topMargin, double contentHeight, double viewportHeight) {
        double scrollableHeight = contentHeight - viewportHeight;
        if (scrollableHeight <= 0) {
            return 0;
        }
        double targetY = Math.max(0, targetMinY - topMargin);
        return Math.max(0, Math.min(1.0, targetY / scrollableHeight));
    }

    private static void updateStatValue(Label label, int value, int previousValue) {
        if (value != previousValue) {
            label.setText(Integer.toString(value));
        }
    }

    private ScrollBar findEventListVerticalScrollBar() {
        if (eventListVerticalScrollBar != null) {
            return eventListVerticalScrollBar;
        }
        if (eventList == null) {
            return null;
        }
        for (javafx.scene.Node node : eventList.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar
                    && scrollBar.getOrientation() == Orientation.VERTICAL) {
                eventListVerticalScrollBar = scrollBar;
                return scrollBar;
            }
        }
        return null;
    }

    private boolean isTimelineAtBottom() {
        ScrollBar scrollBar = findEventListVerticalScrollBar();
        if (scrollBar == null || !scrollBar.isVisible()) {
            return true;
        }
        return scrollBar.getValue() >= scrollBar.getMax() - 0.01;
    }

    private void renderSeats(SimulationSnapshot snapshot) {
        String availableText = t("seat.available");
        int capacity = snapshot.config().waitingRoomCapacity();
        if (seatSlots.size() != capacity) {
            seatSlots.clear();
            seatsPane.getChildren().clear();
            for (int i = 0; i < capacity; i++) {
                SeatSlot slot = new SeatSlot(availableText);
                seatSlots.add(slot);
                seatsPane.getChildren().add(slot.label);
            }
        }
        List<String> waiting = snapshot.waitingPatients();
        for (int i = 0; i < seatSlots.size(); i++) {
            SeatSlot slot = seatSlots.get(i);
            boolean shouldOccupy = i < waiting.size();
            String targetText = shouldOccupy ? waiting.get(i) : availableText;
            if (slot.occupied == shouldOccupy && Objects.equals(slot.displayedText, targetText)) {
                continue;
            }
            slot.label.setText(targetText);
            slot.displayedText = targetText;
            if (slot.occupied != shouldOccupy) {
                slot.occupied = shouldOccupy;
                slot.label.getStyleClass().remove(shouldOccupy ? "seat-empty" : "seat-occupied");
                slot.label.getStyleClass().add(shouldOccupy ? "seat-occupied" : "seat-empty");
            }
            slot.transition.stop();
            slot.transition.playFromStart();
        }
    }

    private void renderOutside(SimulationSnapshot snapshot) {
        String text = snapshot.patientsOutside() == 0
                ? t("waiting.none")
                : String.join("  ·  ", snapshot.outsidePatientIds());
        if (!text.equals(lastOutsideText)) {
            outsidePatientsLabel.setText(text);
            lastOutsideText = text;
        }
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
        List<SimulationSnapshot.TreatmentRoom> rooms = snapshot.treatmentRooms();
        if (roomCards.size() != rooms.size()) {
            roomCards.clear();
            roomsPane.getChildren().clear();
            for (SimulationSnapshot.TreatmentRoom room : rooms) {
                RoomCard card = new RoomCard(room.number());
                roomCards.put(room.number(), card);
                roomsPane.getChildren().add(card.container);
            }
        }
        for (SimulationSnapshot.TreatmentRoom room : rooms) {
            roomCards.get(room.number()).update(room);
        }
    }

    private void populateIdleRoomCards(int roomCount) {
        roomCards.clear();
        roomsPane.getChildren().clear();
        for (int room = 1; room <= roomCount; room++) {
            RoomCard card = new RoomCard(room);
            roomCards.put(room, card);
            roomsPane.getChildren().add(card.container);
        }
    }

    private void renderIdle() {
        lastRenderedStatus = Status.IDLE;
        updateStatus(Status.IDLE);
        waitingStat.setText("0");
        totalPatientsStat.setText("0");
        outsideStat.setText("0");
        treatingStat.setText("0");
        completedStat.setText("0");
        availableDoctorsStat.setText("0");
        busyDoctorsStat.setText("0");
        int roomCount = parsePositiveOrDefault(roomCountField.getText(), 2);
        availableRoomsStat.setText(Integer.toString(roomCount));
        resetStatCaches(roomCount);
        occupancyLabel.setText(occupancyText(0, 0));
        lastWaitingOccupancy = 0;
        lastWaitingCapacity = 0;
        String noneOutside = t("waiting.none");
        outsidePatientsLabel.setText(noneOutside);
        lastOutsideText = noneOutside;
        seatsPane.getChildren().clear();
        seatSlots.clear();
        Label emptyDoctors = new Label(t("doctors.empty"));
        emptyDoctors.getStyleClass().add("empty-hint");
        doctorsPane.getChildren().setAll(emptyDoctors);
        populateIdleRoomCards(roomCount);
        completionBanner.setVisible(false);
        completionBanner.setManaged(false);
        events.clear();
        allEvents.clear();
        doctorCards.clear();
        timelineAtBottom = true;
        latestSnapshot = null;
    }

    private void clearDashboard() {
        events.clear();
        allEvents.clear();
        doctorCards.clear();
        roomCards.clear();
        seatSlots.clear();
        seatsPane.getChildren().clear();
        doctorsPane.getChildren().clear();
        roomsPane.getChildren().clear();
        resetStatCaches(-1);
        lastWaitingOccupancy = -1;
        lastWaitingCapacity = -1;
        lastOutsideText = null;
        lastRenderedStatus = null;
        timelineAtBottom = true;
        latestSnapshot = null;
    }

    private void resetStatCaches(int availableRooms) {
        lastTotalPatients = 0;
        lastWaiting = 0;
        lastOutside = 0;
        lastTreating = 0;
        lastCompleted = 0;
        lastAvailableDoctors = 0;
        lastBusyDoctors = 0;
        lastAvailableRooms = availableRooms;
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
                if (now - lastProgressUpdateNanos < PROGRESS_UPDATE_INTERVAL_NANOS) {
                    return;
                }
                lastProgressUpdateNanos = now;
                long currentTime = System.currentTimeMillis();
                List<SimulationSnapshot.Doctor> doctors = latestSnapshot.doctors();
                for (SimulationSnapshot.Doctor doctor : doctors) {
                    DoctorCard card = doctorCards.get(doctor.id());
                    if (card != null && doctor.status() == DoctorStatus.TREATING) {
                        updateProgressBar(card.progress, currentTime, doctor);
                    }
                }
                for (SimulationSnapshot.TreatmentRoom room : latestSnapshot.treatmentRooms()) {
                    if (room.status() != RoomStatus.OCCUPIED) {
                        continue;
                    }
                    RoomCard card = roomCards.get(room.number());
                    SimulationSnapshot.Doctor doctor = findDoctor(doctors, room.doctorId());
                    if (card != null && doctor != null) {
                        updateProgressBar(card.progress, currentTime, doctor);
                    }
                }
            }
        };
        progressTimer.start();
    }

    private static void updateProgressBar(
            ProgressBar bar, long currentTime, SimulationSnapshot.Doctor doctor) {
        int duration = doctor.treatmentDurationMillis();
        double target = duration <= 0
                ? 1.0
                : Math.min(1.0, (double) Math.max(0L, currentTime - doctor.treatmentStartedAtMillis()) / duration);
        double current = bar.getProgress();
        if (Math.abs(current - target) >= 0.004 || (target >= 1.0 && current < 1.0)) {
            bar.setProgress(target);
        }
    }

    static void trimDisplayedEvents(List<SimulationEvent> displayed, int maxDisplayed) {
        int overflow = displayed.size() - maxDisplayed;
        if (overflow > 0) {
            displayed.subList(0, overflow).clear();
        }
    }

    static SimulationSnapshot.Doctor findDoctor(
            List<SimulationSnapshot.Doctor> doctors, int doctorId) {
        int index = doctorId - 1;
        if (index >= 0 && index < doctors.size() && doctors.get(index).id() == doctorId) {
            return doctors.get(index);
        }
        for (SimulationSnapshot.Doctor candidate : doctors) {
            if (candidate.id() == doctorId) {
                return candidate;
            }
        }
        return null;
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
        int roomCount = parsePositiveOrDefault(roomCountField.getText(), 2);
        if (roomCards.size() != roomCount) {
            populateIdleRoomCards(roomCount);
        }
        availableRoomsStat.setText(Integer.toString(roomCount));
        lastAvailableRooms = roomCount;
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

    private SurfaceCard createSurfaceCard(String title, String subtitle) {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("surface-card-backdrop");
        backdrop.setMouseTransparent(true);
        backdrop.setCache(true);
        backdrop.setCacheHint(CacheHint.SPEED);

        VBox body = new VBox(14);
        body.getStyleClass().add("surface-card-body");
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        Label subheading = new Label(subtitle);
        subheading.getStyleClass().add("section-subtitle");
        VBox titleBox = new VBox(4, heading, subheading);
        body.getChildren().add(titleBox);

        StackPane cardRoot = new StackPane(backdrop, body);
        cardRoot.getStyleClass().add("surface-card");
        return new SurfaceCard(cardRoot, body);
    }

    private record SurfaceCard(StackPane root, VBox body) {
    }

    private static final class SeatSlot {
        private final Label label;
        private final FadeTransition transition;
        private String displayedText;
        private boolean occupied;

        private SeatSlot(String availableText) {
            label = new Label(availableText);
            label.getStyleClass().add("seat-empty");
            label.setMinSize(86, 54);
            label.setAlignment(Pos.CENTER);
            transition = new FadeTransition(Duration.millis(180), label);
            transition.setFromValue(0.55);
            transition.setToValue(1.0);
            displayedText = availableText;
        }
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
        private DoctorStatus lastStatus;
        private String lastStatusClass;
        private String lastPatientId;
        private boolean initialized;

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
            progress.setVisible(false);
            progress.setManaged(false);
            container.getChildren().addAll(header, patient, progress);
        }

        private void update(SimulationSnapshot.Doctor doctor) {
            DoctorStatus currentStatus = doctor.status();
            if (!initialized || currentStatus != lastStatus) {
                status.setText(DashboardText.doctorStatus(currentStatus));
                if (lastStatusClass != null) {
                    status.getStyleClass().remove(lastStatusClass);
                }
                lastStatusClass = "doctor-"
                        + currentStatus.name().toLowerCase().replace('_', '-');
                status.getStyleClass().add(lastStatusClass);
                boolean treating = currentStatus == DoctorStatus.TREATING;
                progress.setVisible(treating);
                progress.setManaged(treating);
                if (!treating) {
                    progress.setProgress(0);
                }
                lastStatus = currentStatus;
            }
            String currentPatientId = doctor.patientId();
            if (!initialized || !Objects.equals(currentPatientId, lastPatientId)) {
                patient.setText(currentPatientId == null
                        ? t("doctor.no.patient")
                        : t("doctor.current.patient") + "  ·  " + currentPatientId);
                lastPatientId = currentPatientId;
            }
            initialized = true;
        }
    }

    private final class RoomCard {
        private final VBox container = new VBox(10);
        private final Label state = new Label(t("room.available"));
        private final Label assignment = new Label(t("room.ready"));
        private final ProgressBar progress = new ProgressBar(0);
        private RoomStatus lastStatus = RoomStatus.AVAILABLE;
        private int lastDoctorId;
        private String lastPatientId;

        private RoomCard(int roomNumber) {
            container.getStyleClass().addAll("room-tile", "room-available");
            Label title = new Label(t("room.title") + " " + roomNumber);
            title.getStyleClass().add("room-title");
            state.getStyleClass().add("room-state");
            assignment.getStyleClass().add("room-assignment");
            progress.getStyleClass().addAll("treatment-progress", "room-progress");
            progress.setVisible(false);
            progress.setManaged(false);
            container.getChildren().addAll(title, state, assignment, progress);
            HBox.setHgrow(container, Priority.ALWAYS);
        }

        private void update(SimulationSnapshot.TreatmentRoom room) {
            RoomStatus currentStatus = room.status();
            if (currentStatus != lastStatus) {
                boolean occupied = currentStatus == RoomStatus.OCCUPIED;
                container.getStyleClass().remove(occupied ? "room-available" : "room-occupied");
                container.getStyleClass().add(occupied ? "room-occupied" : "room-available");
                state.setText(t(occupied ? "room.occupied" : "room.available"));
                progress.setVisible(occupied);
                progress.setManaged(occupied);
                if (!occupied) {
                    progress.setProgress(0);
                }
                lastStatus = currentStatus;
            }
            if (currentStatus == RoomStatus.OCCUPIED) {
                if (room.doctorId() != lastDoctorId
                        || !Objects.equals(room.patientId(), lastPatientId)) {
                    assignment.setText(t("doctor.prefix") + " " + room.doctorId()
                            + "  ·  " + room.patientId());
                    lastDoctorId = room.doctorId();
                    lastPatientId = room.patientId();
                }
            } else if (lastDoctorId != 0 || lastPatientId != null) {
                assignment.setText(t("room.ready"));
                lastDoctorId = 0;
                lastPatientId = null;
            }
        }
    }

    private final class EventCell extends ListCell<SimulationEvent> {
        private final Label time = new Label();
        private final Label category = new Label();
        private final Label message = new Label();
        private final HBox row = new HBox(10, time, category, message);
        private EventCategory lastCategory;
        private String lastCategoryClass;

        private EventCell() {
            time.getStyleClass().add("event-time");
            category.getStyleClass().add("event-category");
            message.getStyleClass().add("event-message");
            message.setWrapText(true);
            HBox.setHgrow(message, Priority.ALWAYS);
            row.getStyleClass().add("event-row");
            row.setAlignment(Pos.TOP_LEFT);
        }

        @Override
        protected void updateItem(SimulationEvent event, boolean empty) {
            super.updateItem(event, empty);
            if (empty || event == null) {
                if (getGraphic() != null) {
                    setGraphic(null);
                }
                setText(null);
                return;
            }
            time.setText(EVENT_TIME.format(event.timestamp()));
            EventCategory eventCategory = event.category();
            if (eventCategory != lastCategory) {
                if (lastCategoryClass != null) {
                    category.getStyleClass().remove(lastCategoryClass);
                }
                lastCategoryClass = "event-" + eventCategory.name().toLowerCase().replace('_', '-');
                category.getStyleClass().add(lastCategoryClass);
                category.setText(DashboardText.category(eventCategory));
                lastCategory = eventCategory;
            }
            message.setText(DashboardText.eventMessage(event));
            if (getGraphic() != row) {
                setGraphic(row);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
