package hospital.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import hospital.simulation.SimulationConfig;
import hospital.simulation.SimulationController;
import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationSnapshot;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static hospital.simulation.SimulationSnapshot.DoctorStatus;
import static hospital.simulation.SimulationSnapshot.RoomStatus;
import static hospital.simulation.SimulationSnapshot.Status;

public final class MainActivity extends Activity {
    private static final String PORTFOLIO_URL =
            "https://engomarabd-elaziz.github.io/My-Portofolio/";
    private static final int BRAND = Color.rgb(49, 87, 213);
    private static final int TEAL = Color.rgb(8, 127, 120);
    private static final int WARNING = Color.rgb(154, 90, 8);
    private static final int SUCCESS = Color.rgb(36, 112, 68);
    private static final int DARK_BACKGROUND = Color.rgb(16, 23, 37);
    private static final int DARK_SURFACE = Color.rgb(25, 36, 55);
    private static final int DARK_RAISED = Color.rgb(32, 46, 68);
    private static final int DARK_TEXT = Color.rgb(241, 245, 255);
    private static final int DARK_MUTED = Color.rgb(189, 201, 220);
    private static final int DARK_BORDER = Color.rgb(53, 68, 92);
    private static final int LIGHT_BACKGROUND = Color.rgb(243, 246, 251);
    private static final int LIGHT_SURFACE = Color.WHITE;
    private static final int LIGHT_RAISED = Color.rgb(248, 250, 255);
    private static final int LIGHT_TEXT = Color.rgb(23, 36, 58);
    private static final int LIGHT_MUTED = Color.rgb(82, 100, 124);
    private static final int LIGHT_BORDER = Color.rgb(220, 228, 239);
    private static final DateTimeFormatter EVENT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                    .withZone(ZoneId.systemDefault());

    private final List<String> eventLines = new ArrayList<>();
    private final EditText[] numberFields = new EditText[8];
    private boolean darkTheme;
    private SimulationController controller;
    private SimulationSnapshot latestSnapshot;
    private LinearLayout root;
    private LinearLayout content;
    private final List<LinearLayout> pages = new ArrayList<>();
    private final List<Button> navigationButtons = new ArrayList<>();
    private LinearLayout pageHost;
    private int activePage;
    private LinearLayout seatsGrid;
    private LinearLayout doctorCards;
    private LinearLayout roomCards;
    private LinearLayout timeline;
    private ScrollView timelineScroll;
    private TextView statusLabel;
    private TextView[] statValues;
    private TextView occupancyLabel;
    private TextView outsideLabel;
    private TextView completionLabel;
    private Button startButton;
    private Button stopButton;
    private Button resetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        darkTheme = getPreferences(MODE_PRIVATE).getBoolean("dark_theme", false);
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildDashboard();
        renderIdle();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        rebuildForCurrentConfiguration();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.stop();
        }
        super.onDestroy();
    }

    private void buildDashboard() {
        Palette palette = palette();
        pages.clear();
        navigationButtons.clear();
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        getWindow().getDecorView().setSystemUiVisibility(
                darkTheme ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        setContentView(root);

        buildHeader(palette);

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        createPage("Overview", "A live snapshot of emergency department activity");
        createPage("Waiting room", "Follow patients waiting for care");
        createPage("Care team", "Doctors and shared treatment rooms");
        createPage("Activity", "Chronological simulation events");
        createPage("Settings", "Configure and control the simulation");

        content = pages.get(4);
        buildSetupCard(palette);

        content = pages.get(0);
        buildStatisticsCard(palette);
        completionLabel = text("✓  All patients have been treated", 14, SUCCESS, true);
        completionLabel.setVisibility(View.GONE);
        addCard(completionLabel);

        content = pages.get(1);
        buildWaitingCard(palette);

        content = pages.get(2);
        buildDoctorsCard(palette);
        buildRoomsCard(palette);

        content = pages.get(3);
        buildTimelineCard(palette);

        buildNavigation(palette);
        buildCopyrightFooter(palette);
        selectPage(activePage);
    }

    private void buildCopyrightFooter(Palette palette) {
        String ownerName = "Eng. Omar Abdelaziz";
        String notice = "© 2026 " + ownerName;
        SpannableString linkedNotice = new SpannableString(notice);
        int ownerStart = notice.indexOf(ownerName);
        linkedNotice.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PORTFOLIO_URL));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException exception) {
                    Toast.makeText(MainActivity.this,
                            "No browser is available to open the portfolio link.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }, ownerStart, notice.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView footer = text(linkedNotice, 11, palette.muted, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(9), dp(12), dp(9));
        footer.setBackgroundColor(palette.surface);
        footer.setMovementMethod(LinkMovementMethod.getInstance());
        footer.setLinkTextColor(BRAND);
        footer.setContentDescription("Copyright 2026, Eng. Omar Abdelaziz. "
                + "Tap the name to open the portfolio.");
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void createPage(String title, String subtitle) {
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        LinearLayout pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(14), dp(14), dp(14), dp(24));

        FrameLayout centeredContent = new FrameLayout(this);
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int contentWidthDp = screenWidth >= 840 ? 760 : Math.max(260, screenWidth - 24);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                dp(contentWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        centeredContent.addView(pageContent, contentParams);
        pageScroll.addView(centeredContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(pageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageContent.addView(text(title, 23, palette().text, true));
        TextView description = text(subtitle, 13, palette().muted, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(3);
        descriptionParams.bottomMargin = dp(14);
        pageContent.addView(description, descriptionParams);

        pages.add(pageContent);
        content = pageContent;
        pageScroll.setVisibility(View.GONE);
    }

    private void buildNavigation(Palette palette) {
        String[] labels = {"Home", "Waiting", "Team", "Events", "Settings"};
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(6), dp(7), dp(6), dp(7));
        navigation.setBackgroundColor(palette.surface);
        HorizontalScrollView navigationScroll = new HorizontalScrollView(this);
        navigationScroll.setHorizontalScrollBarEnabled(false);
        navigationScroll.setFillViewport(true);
        navigationScroll.addView(navigation, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navigationScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            Button tab = new Button(this);
            tab.setText(labels[i]);
            tab.setAllCaps(false);
            tab.setTextSize(11);
            tab.setMinWidth(0);
            tab.setPadding(dp(2), dp(5), dp(2), dp(5));
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    0, dp(44), 1);
            tabParams.leftMargin = dp(2);
            tabParams.rightMargin = dp(2);
            navigation.addView(tab, tabParams);
            navigationButtons.add(tab);
            tab.setOnClickListener(view -> selectPage(page));
        }
    }

    private void selectPage(int page) {
        if (pages.isEmpty()) {
            return;
        }
        activePage = Math.max(0, Math.min(page, pages.size() - 1));
        for (int i = 0; i < pages.size(); i++) {
            pageHost.getChildAt(i).setVisibility(i == activePage ? View.VISIBLE : View.GONE);
        }
        Palette palette = palette();
        for (int i = 0; i < navigationButtons.size(); i++) {
            Button tab = navigationButtons.get(i);
            boolean selected = i == activePage;
            tab.setTextColor(selected ? BRAND : palette.muted);
            tab.setBackground(round(selected ? palette.brandSoft : palette.surface,
                    Color.TRANSPARENT, 12));
        }
        if (activePage == 3 && timelineScroll != null) {
            timelineScroll.post(() -> timelineScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void buildHeader(Palette palette) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(12), dp(12));
        header.setBackground(round(palette.surface, palette.border, 18));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.eronix_icon);
        icon.setContentDescription("ERONIX application icon");
        header.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(12), 0, 0, 0);
        TextView title = text("ERONIX", 21, palette.text, true);
        TextView subtitle = text("Emergency Room Simulation", 12, palette.muted, false);
        identity.addView(title);
        identity.addView(subtitle);
        header.addView(identity, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusLabel = text("IDLE", 11, BRAND, true);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
        statusLabel.setBackground(round(palette.brandSoft, Color.TRANSPARENT, 20));
        header.addView(statusLabel);

        Button themeButton = button(darkTheme ? "☀" : "☾", palette, false);
        themeButton.setContentDescription("Toggle light or dark theme");
        themeButton.setTextSize(20);
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        themeParams.leftMargin = dp(8);
        header.addView(themeButton, themeParams);
        themeButton.setOnClickListener(view -> {
            darkTheme = !darkTheme;
            getPreferences(MODE_PRIVATE).edit().putBoolean("dark_theme", darkTheme).apply();
            rebuildForCurrentConfiguration();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        root.addView(header, params);
    }

    private void buildSetupCard(Palette palette) {
        LinearLayout card = card("Simulation setup",
                "Choose patient, room, and timing settings before starting", palette);
        String[] labels = {
                "Waiting seats", "Doctors", "Patients", "Treatment rooms",
                "Min arrival (sec)", "Max arrival (sec)",
                "Min treatment (sec)", "Max treatment (sec)"
        };
        String[] defaults = {"5", "3", "12", "2", "0.15", "0.7", "1", "5"};
        int columns = gridColumns();
        for (int rowStart = 0; rowStart < labels.length; rowStart += columns) {
            LinearLayout fieldsRow = new LinearLayout(this);
            fieldsRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < columns && rowStart + col < labels.length; col++) {
                int index = rowStart + col;
                LinearLayout fieldGroup = new LinearLayout(this);
                fieldGroup.setOrientation(LinearLayout.VERTICAL);
                TextView label = text(labels[index], 12, palette.muted, true);
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(defaults[index]);
                input.setTextSize(15);
                input.setTextColor(palette.text);
                input.setHintTextColor(palette.muted);
                input.setPadding(dp(12), dp(4), dp(12), dp(4));
                input.setBackground(round(palette.raised, palette.border, 10));
                input.setInputType(index < 4
                        ? InputType.TYPE_CLASS_NUMBER
                        : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                numberFields[index] = input;
                fieldGroup.addView(label);
                LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(47));
                inputParams.topMargin = dp(5);
                fieldGroup.addView(input, inputParams);
                LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col == 0) {
                    fieldParams.rightMargin = dp(8);
                } else {
                    fieldParams.leftMargin = dp(8);
                }
                fieldsRow.addView(fieldGroup, fieldParams);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = rowStart == 0 ? dp(10) : dp(12);
            card.addView(fieldsRow, rowParams);
        }

        TextView hint = text("Treatment duration follows the assignment: 1–5 seconds.",
                12, palette.muted, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(12);
        card.addView(hint, hintParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        startButton = button("Start", palette, true);
        stopButton = button("Stop", palette, false);
        resetButton = button("Reset", palette, false);
        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        actionParams.leftMargin = dp(8);
        actions.addView(stopButton, actionParams);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        resetParams.leftMargin = dp(8);
        actions.addView(resetButton, resetParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(14);
        card.addView(actions, actionsParams);
        startButton.setOnClickListener(view -> startSimulation());
        stopButton.setOnClickListener(view -> stopSimulation());
        resetButton.setOnClickListener(view -> resetSimulation());
        addCard(card);
    }

    private void buildStatisticsCard(Palette palette) {
        LinearLayout card = card("Live statistics", "Current emergency department activity", palette);
        String[] labels = {"Total patients", "Waiting", "Outside", "Being treated",
                "Completed", "Doctors available", "Doctors busy", "Rooms available"};
        statValues = new TextView[labels.length];
        int columns = gridColumns();
        for (int rowStart = 0; rowStart < labels.length; rowStart += columns) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < columns && rowStart + col < labels.length; col++) {
                int index = rowStart + col;
                LinearLayout stat = new LinearLayout(this);
                stat.setOrientation(LinearLayout.VERTICAL);
                stat.setPadding(dp(12), dp(10), dp(12), dp(10));
                stat.setBackground(round(palette.raised, palette.border, 12));
                TextView label = text(labels[index], 11, palette.muted, true);
                TextView value = text("0", 22, palette.text, true);
                statValues[index] = value;
                stat.addView(label);
                stat.addView(value);
                LinearLayout.LayoutParams statParams = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col > 0) {
                    statParams.rightMargin = dp(6);
                }
                line.addView(stat, statParams);
            }
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = rowStart == 0 ? dp(10) : dp(8);
            card.addView(line, lineParams);
        }
        addCard(card);
    }

    private void buildWaitingCard(Palette palette) {
        LinearLayout card = card("Waiting room", "Capacity and patient flow", palette);
        occupancyLabel = text("0 / 5 occupied seats", 13, palette.text, true);
        card.addView(occupancyLabel);
        seatsGrid = new LinearLayout(this);
        seatsGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(10);
        card.addView(seatsGrid, gridParams);
        TextView outsideTitle = text("Waiting outside", 12, palette.muted, true);
        LinearLayout.LayoutParams outsideTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outsideTitleParams.topMargin = dp(12);
        card.addView(outsideTitle, outsideTitleParams);
        outsideLabel = text("No patients waiting outside", 13, palette.text, false);
        card.addView(outsideLabel);
        addCard(card);
    }

    private void buildDoctorsCard(Palette palette) {
        LinearLayout card = card("Doctors", "Live availability and treatment progress", palette);
        doctorCards = new LinearLayout(this);
        doctorCards.setOrientation(LinearLayout.VERTICAL);
        doctorCards.addView(text("Doctor cards will appear when the simulation starts.",
                13, palette.muted, false));
        card.addView(doctorCards);
        addCard(card);
    }

    private void buildRoomsCard(Palette palette) {
        LinearLayout card = card("Treatment rooms", "Shared treatment resources", palette);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        roomCards = new LinearLayout(this);
        roomCards.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(roomCards);
        card.addView(horizontal);
        addCard(card);
        renderIdleRooms();
    }

    private void buildTimelineCard(Palette palette) {
        LinearLayout card = card("Live event timeline",
                "Simulation events in chronological order", palette);
        timelineScroll = new ScrollView(this);
        timelineScroll.setFillViewport(false);
        timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.addView(text("Events will appear here when the simulation starts.",
                13, palette.muted, false));
        timelineScroll.addView(timeline);
        int timelineHeight = getResources().getConfiguration().screenHeightDp < 480 ? 170 : 230;
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(timelineHeight));
        card.addView(timelineScroll, scrollParams);
        addCard(card);
    }

    private LinearLayout card(String title, String subtitle, Palette palette) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(round(palette.surface, palette.border, 16));
        TextView heading = text(title, 17, palette.text, true);
        TextView description = text(subtitle, 12, palette.muted, false);
        card.addView(heading);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        card.addView(description, subtitleParams);
        return card;
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        content.addView(card, params);
    }

    private void startSimulation() {
        try {
            SimulationConfig config = new SimulationConfig(
                    integerField(0, "Waiting seats", 1, 30),
                    integerField(1, "Doctors", 1, 20),
                    integerField(2, "Patients", 1, 200),
                    integerField(3, "Treatment rooms", 1, 20),
                    secondsField(4, "Min arrival interval", 0, 60),
                    secondsField(5, "Max arrival interval", 0, 60),
                    secondsField(6, "Min treatment", 1, 5),
                    secondsField(7, "Max treatment", 1, 5));
            eventLines.clear();
            latestSnapshot = null;
            controller = new SimulationController(config, this::onSimulationUpdate);
            controller.setConsoleEnabled(false);
            setInputsEnabled(false);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            resetButton.setEnabled(false);
            completionLabel.setVisibility(View.GONE);
            controller.start();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopSimulation() {
        if (controller == null || (latestSnapshot != null
                && latestSnapshot.status() != Status.RUNNING)) {
            return;
        }
        stopButton.setEnabled(false);
        statusLabel.setText("STOPPING");
        controller.stop();
    }

    private void resetSimulation() {
        if (latestSnapshot != null && latestSnapshot.status() == Status.RUNNING) {
            return;
        }
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        latestSnapshot = null;
        eventLines.clear();
        buildDashboard();
        renderIdle();
    }

    private void rebuildForCurrentConfiguration() {
        String[] values = new String[numberFields.length];
        for (int i = 0; i < numberFields.length; i++) {
            values[i] = numberFields[i].getText().toString();
        }
        buildDashboard();
        for (int i = 0; i < numberFields.length; i++) {
            numberFields[i].setText(values[i]);
        }
        if (controller == null) {
            renderIdle();
        } else if (latestSnapshot == null) {
            renderStarting();
        } else {
            renderSnapshot(latestSnapshot, null);
        }
    }

    private void onSimulationUpdate(SimulationSnapshot snapshot, SimulationEvent event) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            latestSnapshot = snapshot;
            if (event != null) {
                eventLines.add(String.format(Locale.getDefault(), "%s   %-8s  %s",
                        EVENT_TIME.format(event.timestamp()), category(event), event.message()));
                if (eventLines.size() > 300) {
                    eventLines.remove(0);
                }
            }
            renderSnapshot(snapshot, event);
        });
    }

    private void renderIdle() {
        Palette palette = palette();
        statusLabel.setText("IDLE");
        for (TextView value : statValues) {
            value.setText("0");
        }
        occupancyLabel.setText("0 / " + numberFields[0].getText() + " occupied seats");
        outsideLabel.setText("No patients waiting outside");
        completionLabel.setVisibility(View.GONE);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        resetButton.setEnabled(false);
        setInputsEnabled(true);
        doctorCards.removeAllViews();
        doctorCards.addView(text("Doctor cards will appear when the simulation starts.",
                13, palette.muted, false));
        timeline.removeAllViews();
        timeline.addView(text("Events will appear here when the simulation starts.",
                13, palette.muted, false));
        renderIdleRooms();
    }

    private void renderStarting() {
        statusLabel.setText("STARTING");
        setInputsEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        resetButton.setEnabled(false);
        renderIdleRooms();
    }

    private void renderSnapshot(SimulationSnapshot snapshot, SimulationEvent event) {
        Palette palette = palette();
        statusLabel.setText(snapshot.status().name());
        setInputsEnabled(snapshot.status() != Status.RUNNING);
        startButton.setEnabled(snapshot.status() == Status.STOPPED
                || snapshot.status() == Status.IDLE);
        stopButton.setEnabled(snapshot.status() == Status.RUNNING);
        resetButton.setEnabled(snapshot.status() != Status.RUNNING);
        statValues[0].setText(Integer.toString(snapshot.config().patientCount()));
        statValues[1].setText(Integer.toString(snapshot.patientsWaiting()));
        statValues[2].setText(Integer.toString(snapshot.patientsOutside()));
        statValues[3].setText(Integer.toString(snapshot.patientsBeingTreated()));
        statValues[4].setText(Integer.toString(snapshot.patientsCompleted()));
        statValues[5].setText(Integer.toString(snapshot.availableDoctors()));
        statValues[6].setText(Integer.toString(snapshot.busyDoctors()));
        statValues[7].setText(Integer.toString(snapshot.availableTreatmentRooms()));
        occupancyLabel.setText(snapshot.patientsWaiting() + " / "
                + snapshot.config().waitingRoomCapacity() + " occupied seats");
        outsideLabel.setText(snapshot.outsidePatientIds().isEmpty()
                ? "No patients waiting outside"
                : String.join("   •   ", snapshot.outsidePatientIds()));
        completionLabel.setVisibility(snapshot.status() == Status.COMPLETED
                ? View.VISIBLE : View.GONE);

        renderSeats(snapshot, palette);
        renderDoctors(snapshot, palette);
        renderRooms(snapshot, palette);
        renderTimeline();
    }

    private void renderSeats(SimulationSnapshot snapshot, Palette palette) {
        seatsGrid.removeAllViews();
        List<String> waiting = snapshot.waitingPatients();
        int capacity = snapshot.config().waitingRoomCapacity();
        int columns = seatColumns();
        for (int offset = 0; offset < capacity; offset += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int i = offset; i < Math.min(offset + columns, capacity); i++) {
                String patient = i < waiting.size() ? waiting.get(i) : null;
                TextView seat = text(patient == null ? "Empty" : patient, 12,
                        patient == null ? palette.muted : BRAND, true);
                seat.setGravity(Gravity.CENTER);
                seat.setPadding(dp(7), dp(9), dp(7), dp(9));
                seat.setBackground(round(patient == null ? palette.raised : palette.brandSoft,
                        palette.border, 10));
                LinearLayout.LayoutParams seatParams = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                seatParams.rightMargin = dp(4);
                row.addView(seat, seatParams);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(5);
            seatsGrid.addView(row, rowParams);
        }
    }

    private void renderDoctors(SimulationSnapshot snapshot, Palette palette) {
        doctorCards.removeAllViews();
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 2 : 1;
        LinearLayout doctorRow = null;
        int doctorIndex = 0;
        for (SimulationSnapshot.Doctor doctor : snapshot.doctors()) {
            if (doctorIndex % columns == 0) {
                doctorRow = new LinearLayout(this);
                doctorRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dp(7);
                doctorCards.addView(doctorRow, rowParams);
            }
            LinearLayout doctorCard = new LinearLayout(this);
            doctorCard.setOrientation(LinearLayout.VERTICAL);
            doctorCard.setPadding(dp(12), dp(11), dp(12), dp(11));
            doctorCard.setBackground(round(palette.raised, palette.border, 12));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView doctorName = text("Doctor " + doctor.id(), 14, palette.text, true);
            row.addView(doctorName, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            int statusColor = doctor.status() == DoctorStatus.TREATING ? TEAL
                    : doctor.status() == DoctorStatus.WAITING_FOR_ROOM ? WARNING : palette.muted;
            TextView doctorStatus = text(doctorStatus(doctor.status()), 12, statusColor, true);
            row.addView(doctorStatus);
            doctorCard.addView(row);
            String patientText = doctor.patientId() == null
                    ? "No patient assigned" : "Current patient  ·  " + doctor.patientId();
            TextView patient = text(patientText, 12, palette.muted, false);
            LinearLayout.LayoutParams patientParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            patientParams.topMargin = dp(6);
            doctorCard.addView(patient, patientParams);
            if (doctor.status() == DoctorStatus.TREATING) {
                ProgressBar progress = new ProgressBar(this, null,
                        android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                long elapsed = Math.max(0, System.currentTimeMillis()
                        - doctor.treatmentStartedAtMillis());
                progress.setProgress((int) Math.min(100,
                        elapsed * 100 / Math.max(1, doctor.treatmentDurationMillis())));
                progress.setProgressTintList(ColorStateList.valueOf(TEAL));
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
                progressParams.topMargin = dp(8);
                doctorCard.addView(progress, progressParams);
            }
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (columns == 1) {
                cardParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                cardParams.weight = 0;
            } else if (doctorIndex % columns == 0) {
                cardParams.rightMargin = dp(5);
            } else {
                cardParams.leftMargin = dp(5);
            }
            doctorRow.addView(doctorCard, cardParams);
            doctorIndex++;
        }
    }

    private void renderRooms(SimulationSnapshot snapshot, Palette palette) {
        roomCards.removeAllViews();
        for (SimulationSnapshot.TreatmentRoom room : snapshot.treatmentRooms()) {
            boolean occupied = room.status() == RoomStatus.OCCUPIED;
            LinearLayout roomCard = new LinearLayout(this);
            roomCard.setOrientation(LinearLayout.VERTICAL);
            roomCard.setPadding(dp(13), dp(12), dp(13), dp(12));
            roomCard.setBackground(round(occupied ? palette.brandSoft : palette.raised,
                    palette.border, 12));
            TextView title = text("Treatment room " + room.number(), 13, palette.text, true);
            TextView state = text(occupied ? "Occupied" : "Available", 12,
                    occupied ? BRAND : TEAL, true);
            TextView assignment = text(occupied
                    ? "Doctor " + room.doctorId() + "  ·  " + room.patientId()
                    : "Ready for the next patient", 11, palette.muted, false);
            roomCard.addView(title);
            roomCard.addView(state);
            roomCard.addView(assignment);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(205),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            roomCards.addView(roomCard, params);
        }
    }

    private void renderIdleRooms() {
        if (roomCards == null) {
            return;
        }
        roomCards.removeAllViews();
        int count = 2;
        try {
            count = Math.max(1, Math.min(20,
                    Integer.parseInt(numberFields[3].getText().toString().trim())));
        } catch (NumberFormatException ignored) {
            // The input is validated when the simulation starts.
        }
        Palette palette = palette();
        for (int i = 1; i <= count; i++) {
            LinearLayout roomCard = new LinearLayout(this);
            roomCard.setOrientation(LinearLayout.VERTICAL);
            roomCard.setPadding(dp(13), dp(12), dp(13), dp(12));
            roomCard.setBackground(round(palette.raised, palette.border, 12));
            roomCard.addView(text("Treatment room " + i, 13, palette.text, true));
            roomCard.addView(text("Available", 12, TEAL, true));
            roomCard.addView(text("Ready for the next patient", 11, palette.muted, false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(205),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            roomCards.addView(roomCard, params);
        }
    }

    private void renderTimeline() {
        timeline.removeAllViews();
        for (String line : eventLines) {
            TextView event = text(line, 11, palette().text, false);
            event.setTypeface(Typeface.MONOSPACE);
            event.setPadding(dp(3), dp(6), dp(3), dp(6));
            timeline.addView(event);
        }
        timelineScroll.post(() -> timelineScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setInputsEnabled(boolean enabled) {
        for (EditText field : numberFields) {
            field.setEnabled(enabled);
        }
    }

    private int integerField(int index, String label, int minimum, int maximum) {
        int value;
        try {
            value = Integer.parseInt(numberFields[index].getText().toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }

    private int secondsField(int index, String label, int minimum, int maximum) {
        String value = numberFields[index].getText().toString().trim().replace(',', '.');
        if (!value.matches("\\d+(?:\\.\\d{1,3})?")) {
            throw new IllegalArgumentException(
                    label + " must be seconds with up to 3 decimal places.");
        }
        double seconds = Double.parseDouble(value);
        if (!Double.isFinite(seconds) || seconds < minimum || seconds > maximum) {
            throw new IllegalArgumentException(
                    label + " must be between " + minimum + " and " + maximum + " seconds.");
        }
        return (int) Math.round(seconds * 1_000);
    }

    private Button button(String label, Palette palette, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : palette.text);
        button.setBackground(round(primary ? BRAND : palette.raised, palette.border, 12));
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sizeSp);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private TextView text(CharSequence value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sizeSp);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private Palette palette() {
        return darkTheme
                ? new Palette(DARK_BACKGROUND, DARK_SURFACE, DARK_RAISED,
                DARK_TEXT, DARK_MUTED, DARK_BORDER, Color.rgb(41, 56, 95))
                : new Palette(LIGHT_BACKGROUND, LIGHT_SURFACE, LIGHT_RAISED,
                LIGHT_TEXT, LIGHT_MUTED, LIGHT_BORDER, Color.rgb(233, 238, 255));
    }

    private String category(SimulationEvent event) {
        return switch (event.category()) {
            case SYSTEM -> "SYSTEM";
            case PATIENT -> "PATIENT";
            case WAITING_ROOM -> "WAITING";
            case DOCTOR -> "DOCTOR";
            case TREATMENT_ROOM -> "ROOM";
            case WARNING -> "NOTICE";
            case SUCCESS -> "SUCCESS";
        };
    }

    private String doctorStatus(DoctorStatus status) {
        return switch (status) {
            case IDLE -> "Idle";
            case WAITING_FOR_ROOM -> "Waiting for room";
            case TREATING -> "Treating";
            case AVAILABLE -> "Available";
            case STOPPED -> "Stopped";
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int gridColumns() {
        return getResources().getConfiguration().screenWidthDp >= 840 ? 4 : 2;
    }

    private int seatColumns() {
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int contentWidth = screenWidth >= 840 ? 760 : Math.max(260, screenWidth - 24);
        return Math.max(2, Math.min(8, (contentWidth - 42) / 88));
    }

    private record Palette(
            int background,
            int surface,
            int raised,
            int text,
            int muted,
            int border,
            int brandSoft) {
    }
}
