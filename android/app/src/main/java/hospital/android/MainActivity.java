package hospital.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

import androidx.core.app.NotificationCompat;

import hospital.update.UpdateChecker;
import hospital.simulation.SimulationConfig;
import hospital.simulation.SimulationController;
import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationReport;
import hospital.simulation.SimulationSnapshot;
import hospital.simulation.WordReportExporter;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static hospital.simulation.SimulationSnapshot.DoctorStatus;
import static hospital.simulation.SimulationSnapshot.RoomStatus;
import static hospital.simulation.SimulationSnapshot.Status;

public final class MainActivity extends Activity {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int EXPORT_REPORT_REQUEST = 9104;
    private static final String PORTFOLIO_URL =
            "https://engomarabd-elaziz.github.io/My-Portofolio/";
    private static final String CHANNEL_ID = "eronix_simulation";
    private static final int NOTIF_SIM_DONE = 1;
    private static final int NOTIF_UPDATE   = 2;
    private static final int MAX_EVENTS     = 300;

    // brand colours
    private static final int BRAND   = Color.rgb(49, 87, 213);
    private static final int TEAL    = Color.rgb(8, 127, 120);
    private static final int WARNING = Color.rgb(154, 90, 8);
    private static final int SUCCESS = Color.rgb(36, 112, 68);

    // dark palette
    private static final int DARK_BACKGROUND = Color.rgb(16, 23, 37);
    private static final int DARK_SURFACE    = Color.rgb(25, 36, 55);
    private static final int DARK_RAISED     = Color.rgb(32, 46, 68);
    private static final int DARK_TEXT       = Color.rgb(241, 245, 255);
    private static final int DARK_MUTED      = Color.rgb(189, 201, 220);
    private static final int DARK_BORDER     = Color.rgb(53, 68, 92);

    // light palette
    private static final int LIGHT_BACKGROUND = Color.rgb(243, 246, 251);
    private static final int LIGHT_SURFACE    = Color.WHITE;
    private static final int LIGHT_RAISED     = Color.rgb(248, 250, 255);
    private static final int LIGHT_TEXT       = Color.rgb(23, 36, 58);
    private static final int LIGHT_MUTED      = Color.rgb(82, 100, 124);
    private static final int LIGHT_BORDER     = Color.rgb(220, 228, 239);

    private static final DateTimeFormatter EVENT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                    .withZone(ZoneId.systemDefault());

    // navigation tab labels + emoji icons
    private static final String[] NAV_LABELS  = {"Home", "Waiting", "Team", "Events", "Report", "Settings"};
    private static final String[] NAV_ICONS   = {"🏠", "🪑", "👨‍⚕️", "📋", "📊", "⚙️"};

    // settings keys
    private static final String PREF_DARK    = "dark_theme";
    private static final String PREF_SEATS   = "pref_seats";
    private static final String PREF_DOCTORS = "pref_doctors";
    private static final String PREF_PATIENTS = "pref_patients";
    private static final String PREF_ROOMS   = "pref_rooms";
    private static final String PREF_MIN_ARR = "pref_min_arr";
    private static final String PREF_MAX_ARR = "pref_max_arr";
    private static final String PREF_MIN_TRT = "pref_min_trt";
    private static final String PREF_MAX_TRT = "pref_max_trt";

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<String>           eventLines   = new ArrayList<>();
    private final List<SimulationEvent>  reportEvents = new ArrayList<>();
    private final EditText[]             numberFields = new EditText[8];

    private boolean               darkTheme;
    private SimulationController  controller;
    private SimulationSnapshot    latestSnapshot;

    // root views
    private LinearLayout          root;
    private LinearLayout          content;
    private LinearLayout          pageHost;
    private int                   activePage;

    // page containers (index = page #)
    private final List<LinearLayout> pages             = new ArrayList<>();
    private final List<Button>       navigationButtons = new ArrayList<>();

    // live-update widgets
    private LinearLayout seatsGrid;
    private LinearLayout doctorCards;
    private LinearLayout roomCards;
    private LinearLayout timeline;
    private ScrollView   timelineScroll;
    private TextView     statusLabel;
    private ProgressBar  simProgressBar;
    private TextView[]   statValues;
    private TextView     reportSummary;
    private TextView[]   reportValues;

    private final List<ReportChartView> reportCharts = new ArrayList<>();

    private Button   exportReportButton;
    private Button   shareReportButton;
    private byte[]   pendingReport;
    private byte[]   lastReportBytes;      // cached for share
    private GestureDetector swipeDetector;

    private TextView occupancyLabel;
    private TextView outsideLabel;
    private TextView completionLabel;
    private Button   startButton;
    private Button   stopButton;
    private Button   resetButton;

    // incremental rendering caches
    private List<String>                      lastEventLines   = new ArrayList<>();
    private List<SimulationSnapshot.Doctor>   lastDoctors      = null;
    private List<SimulationSnapshot.TreatmentRoom> lastRooms   = null;
    private List<String>                      lastSeats        = null;
    private int                               lastCapacity     = -1;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        darkTheme = prefs().getBoolean(PREF_DARK, false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy) * 1.5f
                        && Math.abs(velocityX) > 200) {
                    if (dx < 0 && activePage < pages.size() - 1) {
                        selectPage(activePage + 1);
                        return true;
                    } else if (dx > 0 && activePage > 0) {
                        selectPage(activePage - 1);
                        return true;
                    }
                }
                return false;
            }
        });
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        buildDashboard();
        renderIdle();
        checkForUpdatesOnStartup();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeDetector != null && swipeDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        rebuildForCurrentConfiguration();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) controller.stop();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REPORT_REQUEST) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingReport = null;
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null || pendingReport == null) {
                throw new IOException("Could not open the selected document.");
            }
            out.write(pendingReport);
            Toast.makeText(this, "Word report saved successfully.", Toast.LENGTH_LONG).show();
        } catch (IOException ex) {
            Toast.makeText(this, "Could not save the Word report: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            pendingReport = null;
        }
    }

    // ── Dashboard construction ─────────────────────────────────────────────────

    private void buildDashboard() {
        Palette palette = palette();
        pages.clear();
        navigationButtons.clear();
        reportCharts.clear();
        lastDoctors = null;
        lastRooms   = null;
        lastSeats   = null;
        lastCapacity = -1;
        lastEventLines.clear();

        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        //noinspection deprecation
        getWindow().getDecorView().setSystemUiVisibility(
                darkTheme ? 0
                        : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
        createPage("Report", "Simulation outcomes and performance");
        createPage("Settings", "Configure and control the simulation");

        content = pages.get(5); buildSetupCard(palette);
        content = pages.get(0); buildStatisticsCard(palette);
        completionLabel = text("✓  All patients have been treated", 14, SUCCESS, true);
        completionLabel.setVisibility(View.GONE);
        addCard(completionLabel);

        content = pages.get(1); buildWaitingCard(palette);
        content = pages.get(2); buildDoctorsCard(palette); buildRoomsCard(palette);
        content = pages.get(3); buildTimelineCard(palette);
        content = pages.get(4); buildReportPage(palette);

        buildNavigation(palette);
        buildCopyrightFooter(palette);
        selectPage(activePage);
    }

    private void buildHeader(Palette palette) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(10), dp(10));
        header.setBackground(round(palette.surface, palette.border, 18));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.eronix_icon);
        icon.setContentDescription("ERONIX application icon");
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(10), 0, 0, 0);
        identity.addView(text("ERONIX", 20, palette.text, true));
        // version subtitle
        String version = "v" + BuildConfig.VERSION_NAME + "  ·  Emergency Room Simulation";
        identity.addView(text(version, 11, palette.muted, false));
        header.addView(identity, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // simulation progress bar in header (shown only while running)
        LinearLayout rightSide = new LinearLayout(this);
        rightSide.setOrientation(LinearLayout.VERTICAL);
        rightSide.setGravity(Gravity.CENTER_HORIZONTAL);

        statusLabel = text("IDLE", 10, BRAND, true);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(dp(9), dp(6), dp(9), dp(6));
        statusLabel.setBackground(round(palette.brandSoft, Color.TRANSPARENT, 20));
        rightSide.addView(statusLabel);

        simProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        simProgressBar.setMax(100);
        simProgressBar.setProgress(0);
        simProgressBar.setProgressTintList(ColorStateList.valueOf(BRAND));
        simProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(dp(72), dp(5));
        pbParams.topMargin = dp(4);
        rightSide.addView(simProgressBar, pbParams);

        LinearLayout.LayoutParams rsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rsParams.leftMargin = dp(6);
        header.addView(rightSide, rsParams);

        // theme toggle
        Button themeButton = button(darkTheme ? "☀" : "☾", palette, false);
        themeButton.setContentDescription("Toggle light or dark theme");
        themeButton.setTextSize(18);
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        themeParams.leftMargin = dp(6);
        header.addView(themeButton, themeParams);
        themeButton.setOnClickListener(v -> {
            darkTheme = !darkTheme;
            prefs().edit().putBoolean(PREF_DARK, darkTheme).apply();
            rebuildForCurrentConfiguration();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        root.addView(header, params);
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

        pageContent.addView(text(title, 22, palette().text, true));
        TextView description = text(subtitle, 13, palette().muted, false);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(3);
        descParams.bottomMargin = dp(14);
        pageContent.addView(description, descParams);

        pages.add(pageContent);
        content = pageContent;
        pageScroll.setVisibility(View.GONE);
    }

    private void buildNavigation(Palette palette) {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(4), dp(5), dp(4), dp(5));
        navigation.setBackgroundColor(palette.surface);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setFillViewport(true);
        navScroll.addView(navigation, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < NAV_LABELS.length; i++) {
            final int page = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(2), dp(4), dp(2), dp(4));
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            tabParams.leftMargin = dp(2);
            tabParams.rightMargin = dp(2);

            TextView iconView = text(NAV_ICONS[i], 18, palette.muted, false);
            iconView.setGravity(Gravity.CENTER);

            TextView labelView = text(NAV_LABELS[i], 10, palette.muted, false);
            labelView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(1);

            tab.addView(iconView);
            tab.addView(labelView, labelParams);
            tab.setBackground(round(palette.surface, Color.TRANSPARENT, 12));
            tab.setOnClickListener(v -> selectPage(page));
            navigation.addView(tab, tabParams);
            // store label view in navigationButtons list (we reuse the Button list as TextView holders)
            // We'll use a separate list for tab containers
            navigationButtons.add(null); // placeholder — see updateNavHighlight
            // tag the tab container for later highlight
            tab.setTag(i);
            navigation.getChildAt(i).setTag(i);
        }
        // store tab containers for highlight updates
        for (int i = 0; i < navigation.getChildCount(); i++) {
            View child = navigation.getChildAt(i);
            child.setTag(i);
        }
        // keep reference to navigation for selectPage
        root.setTag(navigation);
    }

    private void selectPage(int page) {
        if (pages.isEmpty()) return;
        activePage = Math.max(0, Math.min(page, pages.size() - 1));
        for (int i = 0; i < pages.size(); i++) {
            pageHost.getChildAt(i).setVisibility(i == activePage ? View.VISIBLE : View.GONE);
        }
        updateNavHighlight();
        if (activePage == 3 && timelineScroll != null) {
            timelineScroll.post(() -> timelineScroll.fullScroll(View.FOCUS_DOWN));
        }
        if (activePage == 4) renderReport();
    }

    private void updateNavHighlight() {
        Palette palette = palette();
        // The navigation LinearLayout is tagged on root
        if (!(root.getTag() instanceof LinearLayout nav)) return;
        for (int i = 0; i < nav.getChildCount(); i++) {
            if (!(nav.getChildAt(i) instanceof LinearLayout tab)) continue;
            boolean selected = i == activePage;
            tab.setBackground(round(selected ? palette.brandSoft : palette.surface,
                    Color.TRANSPARENT, 12));
            if (tab.getChildCount() >= 2) {
                ((TextView) tab.getChildAt(0)).setTextColor(selected ? BRAND : palette.muted);
                ((TextView) tab.getChildAt(1)).setTextColor(selected ? BRAND : palette.muted);
            }
        }
    }

    // ── Page builders ─────────────────────────────────────────────────────────

    private void buildSetupCard(Palette palette) {
        LinearLayout card = card("Simulation setup",
                "Choose patient, room, and timing settings", palette);

        String[] labels = {
                "Waiting seats", "Doctors", "Patients", "Treatment rooms",
                "Min arrival (sec)", "Max arrival (sec)",
                "Min treatment (sec)", "Max treatment (sec)"
        };
        String[] prefKeys = {PREF_SEATS, PREF_DOCTORS, PREF_PATIENTS, PREF_ROOMS,
                PREF_MIN_ARR, PREF_MAX_ARR, PREF_MIN_TRT, PREF_MAX_TRT};
        String[] defaults = {"5", "3", "12", "2", "0.15", "0.7", "1", "5"};

        int columns = gridColumns();
        for (int rowStart = 0; rowStart < labels.length; rowStart += columns) {
            LinearLayout fieldsRow = new LinearLayout(this);
            fieldsRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < columns && rowStart + col < labels.length; col++) {
                int index = rowStart + col;
                LinearLayout fieldGroup = new LinearLayout(this);
                fieldGroup.setOrientation(LinearLayout.VERTICAL);
                fieldGroup.addView(text(labels[index], 12, palette.muted, true));

                EditText input = new EditText(this);
                input.setSingleLine(true);
                // restore saved value or use default
                input.setText(prefs().getString(prefKeys[index], defaults[index]));
                input.setTextSize(15);
                input.setTextColor(palette.text);
                input.setHintTextColor(palette.muted);
                input.setPadding(dp(12), dp(4), dp(12), dp(4));
                input.setBackground(round(palette.raised, palette.border, 10));
                input.setInputType(index < 4
                        ? InputType.TYPE_CLASS_NUMBER
                        : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                numberFields[index] = input;

                LinearLayout.LayoutParams inputParams =
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(47));
                inputParams.topMargin = dp(5);
                fieldGroup.addView(input, inputParams);

                LinearLayout.LayoutParams fieldParams =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col == 0) fieldParams.rightMargin = dp(8);
                else fieldParams.leftMargin = dp(8);
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

        // version info row in settings card
        TextView versionInfo = text("ERONIX v" + BuildConfig.VERSION_NAME, 12, palette.muted, false);
        LinearLayout.LayoutParams verParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verParams.topMargin = dp(8);
        card.addView(versionInfo, verParams);

        // action buttons row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        startButton = button("▶  Start", palette, true);
        stopButton  = button("⏹  Stop",  palette, false);
        resetButton = button("↺  Reset", palette, false);

        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(46), 1);
        ap.leftMargin = dp(8);
        actions.addView(stopButton, ap);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(46), 1);
        rp.leftMargin = dp(8);
        actions.addView(resetButton, rp);

        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(14);
        card.addView(actions, actionsParams);

        startButton.setOnClickListener(v -> startSimulation());
        stopButton.setOnClickListener(v -> stopSimulation());
        resetButton.setOnClickListener(v -> confirmReset());

        Button updateButton = button("🔄  Check for updates", palette, false);
        LinearLayout.LayoutParams updParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        updParams.topMargin = dp(8);
        card.addView(updateButton, updParams);
        updateButton.setOnClickListener(v -> checkForUpdatesManual(updateButton));

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
                stat.addView(text(labels[index], 11, palette.muted, true));
                TextView value = text("0", 22, palette.text, true);
                statValues[index] = value;
                stat.addView(value);
                LinearLayout.LayoutParams sp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col > 0) sp.leftMargin = dp(6);
                else sp.rightMargin = dp(6);
                line.addView(stat, sp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = rowStart == 0 ? dp(10) : dp(8);
            card.addView(line, lp);
        }
        addCard(card);
    }

    private void buildWaitingCard(Palette palette) {
        LinearLayout card = card("Waiting room", "Capacity and patient flow", palette);
        occupancyLabel = text("0 / 5 occupied seats", 13, palette.text, true);
        card.addView(occupancyLabel);
        seatsGrid = new LinearLayout(this);
        seatsGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(10);
        card.addView(seatsGrid, gp);

        TextView outsideTitle = text("Waiting outside", 12, palette.muted, true);
        LinearLayout.LayoutParams otp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        otp.topMargin = dp(12);
        card.addView(outsideTitle, otp);
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
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(timelineHeight));
        card.addView(timelineScroll, sp);
        addCard(card);
    }

    private void buildReportPage(Palette palette) {
        reportCharts.clear();
        LinearLayout overview = card("Simulation report",
                "Refreshes with the latest simulation snapshot", palette);
        reportSummary = text("Run a simulation to generate a report.", 13, palette.muted, false);
        overview.addView(reportSummary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("🔄  Refresh", palette, false);
        exportReportButton = button("📄  Export Word", palette, true);
        shareReportButton  = button("📤  Share", palette, false);

        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(44), 1);
        ep.leftMargin = dp(6);
        actions.addView(exportReportButton, ep);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, dp(44), 1);
        sp2.leftMargin = dp(6);
        actions.addView(shareReportButton, sp2);

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(12);
        overview.addView(actions, ap);

        refresh.setOnClickListener(v -> renderReport());
        exportReportButton.setOnClickListener(v -> exportReport());
        shareReportButton.setOnClickListener(v -> shareReport());
        addCard(overview);

        LinearLayout metrics = card("Key metrics", "Patient outcomes and operational activity", palette);
        String[] labels = {"Completed", "Completion rate", "Avg treatment",
                "Simulation time", "Events", "Warnings"};
        reportValues = new TextView[labels.length];
        int columns = gridColumns();
        for (int rowStart = 0; rowStart < labels.length; rowStart += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int col = 0; col < columns && rowStart + col < labels.length; col++) {
                int index = rowStart + col;
                LinearLayout metric = new LinearLayout(this);
                metric.setOrientation(LinearLayout.VERTICAL);
                metric.setPadding(dp(11), dp(10), dp(11), dp(10));
                metric.setBackground(round(palette.raised, palette.border, 12));
                metric.addView(text(labels[index], 11, palette.muted, true));
                reportValues[index] = text("—", 20, palette.text, true);
                metric.addView(reportValues[index]);
                LinearLayout.LayoutParams mp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col > 0) mp.leftMargin = dp(6);
                row.addView(metric, mp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = rowStart == 0 ? dp(10) : dp(8);
            metrics.addView(row, rp);
        }
        addCard(metrics);

        addReportChart("Patient distribution", ReportChartView.PIE, palette);
        addReportChart("Patient outcomes", ReportChartView.BAR, palette);
        addReportChart("Treatment duration histogram", ReportChartView.HISTOGRAM, palette);
        renderReport();
    }

    private void addReportChart(String title, int chartType, Palette palette) {
        LinearLayout chartCard = card(title, "Based on recorded simulation events", palette);
        ReportChartView chart = new ReportChartView(this, chartType);
        reportCharts.add(chart);
        chartCard.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        addCard(chartCard);
    }

    private void buildCopyrightFooter(Palette palette) {
        String ownerName = "Eng. Omar Abdelaziz";
        String notice = "© 2026 " + ownerName + "  ·  ERONIX v" + BuildConfig.VERSION_NAME;
        SpannableString linked = new SpannableString(notice);
        int start = notice.indexOf(ownerName);
        linked.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PORTFOLIO_URL)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,
                            "No browser available.", Toast.LENGTH_LONG).show();
                }
            }
        }, start, start + ownerName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView footer = text(linked, 11, palette.muted, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(8), dp(12), dp(8));
        footer.setBackgroundColor(palette.surface);
        footer.setMovementMethod(LinkMovementMethod.getInstance());
        footer.setLinkTextColor(BRAND);
        footer.setContentDescription("Copyright 2026 Eng. Omar Abdelaziz. Tap to open portfolio.");
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ── Simulation control ─────────────────────────────────────────────────────

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

            saveSettings();
            eventLines.clear();
            reportEvents.clear();
            lastEventLines.clear();
            lastDoctors  = null;
            lastRooms    = null;
            lastSeats    = null;
            latestSnapshot = null;
            lastReportBytes = null;

            controller = new SimulationController(config, this::onSimulationUpdate);
            controller.setConsoleEnabled(false);

            setInputsEnabled(false);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            resetButton.setEnabled(false);
            completionLabel.setVisibility(View.GONE);

            // keep screen on while simulation runs
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            controller.start();
            haptic();
            selectPage(0);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopSimulation() {
        if (controller == null || (latestSnapshot != null
                && latestSnapshot.status() != Status.RUNNING)) return;
        stopButton.setEnabled(false);
        statusLabel.setText("STOPPING");
        controller.stop();
        haptic();
    }

    private void confirmReset() {
        if (latestSnapshot != null && latestSnapshot.status() == Status.RUNNING) return;
        // only ask if there is actual data to lose
        if (latestSnapshot == null && eventLines.isEmpty()) {
            doReset();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Reset simulation?")
                .setMessage("This will clear all current results. Are you sure?")
                .setPositiveButton("Reset", (d, w) -> doReset())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doReset() {
        if (controller != null) { controller.stop(); controller = null; }
        latestSnapshot  = null;
        lastReportBytes = null;
        eventLines.clear();
        reportEvents.clear();
        lastEventLines.clear();
        lastDoctors = null;
        lastRooms   = null;
        lastSeats   = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildDashboard();
        renderIdle();
    }

    private void resetSimulation() {
        confirmReset();
    }

    // ── Simulation updates ─────────────────────────────────────────────────────

    private void onSimulationUpdate(SimulationSnapshot snapshot, SimulationEvent event) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            latestSnapshot = snapshot;
            if (event != null) {
                reportEvents.add(event);
                eventLines.add(String.format(Locale.getDefault(), "%s   %-8s  %s",
                        EVENT_TIME.format(event.timestamp()), category(event), event.message()));
                if (eventLines.size() > MAX_EVENTS) eventLines.remove(0);
            }
            renderSnapshot(snapshot, event);

            // simulation finished
            if (snapshot.status() == Status.COMPLETED || snapshot.status() == Status.STOPPED) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                postSimulationDoneNotification(snapshot.status() == Status.COMPLETED);
            }
        });
    }

    // ── Render helpers ────────────────────────────────────────────────────────

    private void renderIdle() {
        Palette palette = palette();
        statusLabel.setText("IDLE");
        simProgressBar.setVisibility(View.GONE);
        for (TextView v : statValues) v.setText("0");
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
        lastEventLines.clear();
        renderIdleRooms();
    }

    private void renderStarting() {
        statusLabel.setText("STARTING");
        simProgressBar.setVisibility(View.GONE);
        setInputsEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        resetButton.setEnabled(false);
        renderIdleRooms();
    }

    private void renderSnapshot(SimulationSnapshot snapshot, SimulationEvent event) {
        Palette palette = palette();
        statusLabel.setText(snapshot.status().name());

        // header progress bar
        int total = snapshot.config().patientCount();
        if (snapshot.status() == Status.RUNNING && total > 0) {
            simProgressBar.setVisibility(View.VISIBLE);
            simProgressBar.setProgress(snapshot.patientsCompleted() * 100 / total);
        } else {
            simProgressBar.setVisibility(View.GONE);
        }

        setInputsEnabled(snapshot.status() != Status.RUNNING);
        startButton.setEnabled(snapshot.status() == Status.STOPPED
                || snapshot.status() == Status.IDLE);
        stopButton.setEnabled(snapshot.status() == Status.RUNNING);
        resetButton.setEnabled(snapshot.status() != Status.RUNNING);

        // stats (always update — cheap TextViews)
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

        // incremental renders — only rebuild if data changed
        renderSeats(snapshot, palette);
        renderDoctors(snapshot, palette);
        renderRooms(snapshot, palette);
        if (event != null) appendEventLine(palette);
        if (activePage == 4) renderReport();
    }

    // ── Incremental renderers ─────────────────────────────────────────────────

    /** Only rebuilds seat grid if waiting list or capacity changed. */
    private void renderSeats(SimulationSnapshot snapshot, Palette palette) {
        List<String> waiting = snapshot.waitingPatients();
        int capacity = snapshot.config().waitingRoomCapacity();
        if (waiting.equals(lastSeats) && capacity == lastCapacity) return;
        lastSeats    = new ArrayList<>(waiting);
        lastCapacity = capacity;

        seatsGrid.removeAllViews();
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
                LinearLayout.LayoutParams sp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                sp.rightMargin = dp(4);
                row.addView(seat, sp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(5);
            seatsGrid.addView(row, rp);
        }
    }

    /** Rebuilds doctor cards only when list size or status changes. */
    private void renderDoctors(SimulationSnapshot snapshot, Palette palette) {
        List<SimulationSnapshot.Doctor> doctors = snapshot.doctors();
        if (doctorsUnchanged(doctors)) return;
        lastDoctors = new ArrayList<>(doctors);

        doctorCards.removeAllViews();
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 2 : 1;
        LinearLayout doctorRow = null;
        int idx = 0;
        for (SimulationSnapshot.Doctor doctor : doctors) {
            if (idx % columns == 0) {
                doctorRow = new LinearLayout(this);
                doctorRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(7);
                doctorCards.addView(doctorRow, rp);
            }
            LinearLayout doctorCard = new LinearLayout(this);
            doctorCard.setOrientation(LinearLayout.VERTICAL);
            doctorCard.setPadding(dp(12), dp(11), dp(12), dp(11));
            doctorCard.setBackground(round(palette.raised, palette.border, 12));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text("Doctor " + doctor.id(), 14, palette.text, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            int statusColor = doctor.status() == DoctorStatus.TREATING ? TEAL
                    : doctor.status() == DoctorStatus.WAITING_FOR_ROOM ? WARNING : palette.muted;
            row.addView(text(doctorStatus(doctor.status()), 12, statusColor, true));
            doctorCard.addView(row);

            String patientText = doctor.patientId() == null
                    ? "No patient assigned" : "Current patient  ·  " + doctor.patientId();
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.topMargin = dp(6);
            doctorCard.addView(text(patientText, 12, palette.muted, false), pp);

            if (doctor.status() == DoctorStatus.TREATING) {
                ProgressBar pb = new ProgressBar(this, null,
                        android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100);
                long elapsed = Math.max(0, System.currentTimeMillis()
                        - doctor.treatmentStartedAtMillis());
                pb.setProgress((int) Math.min(100,
                        elapsed * 100 / Math.max(1, doctor.treatmentDurationMillis())));
                pb.setProgressTintList(ColorStateList.valueOf(TEAL));
                LinearLayout.LayoutParams pbp =
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
                pbp.topMargin = dp(8);
                doctorCard.addView(pb, pbp);
            }

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (columns == 1) { cp.width = ViewGroup.LayoutParams.MATCH_PARENT; cp.weight = 0; }
            else if (idx % columns == 0) cp.rightMargin = dp(5);
            else cp.leftMargin = dp(5);
            if (doctorRow != null) doctorRow.addView(doctorCard, cp);
            idx++;
        }
    }

    /** Rebuilds room cards only when status changes. */
    private void renderRooms(SimulationSnapshot snapshot, Palette palette) {
        List<SimulationSnapshot.TreatmentRoom> rooms = snapshot.treatmentRooms();
        if (roomsUnchanged(rooms)) return;
        lastRooms = new ArrayList<>(rooms);

        roomCards.removeAllViews();
        for (SimulationSnapshot.TreatmentRoom room : rooms) {
            boolean occupied = room.status() == RoomStatus.OCCUPIED;
            LinearLayout roomCard = new LinearLayout(this);
            roomCard.setOrientation(LinearLayout.VERTICAL);
            roomCard.setPadding(dp(13), dp(12), dp(13), dp(12));
            roomCard.setBackground(round(occupied ? palette.brandSoft : palette.raised,
                    palette.border, 12));
            roomCard.addView(text("Treatment room " + room.number(), 13, palette.text, true));
            roomCard.addView(text(occupied ? "Occupied" : "Available", 12,
                    occupied ? BRAND : TEAL, true));
            roomCard.addView(text(occupied
                    ? "Doctor " + room.doctorId() + "  ·  " + room.patientId()
                    : "Ready for the next patient", 11, palette.muted, false));
            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(dp(205), ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(8);
            roomCards.addView(roomCard, p);
        }
    }

    /** Appends only the newest event line instead of rebuilding all. */
    private void appendEventLine(Palette palette) {
        if (eventLines.isEmpty()) return;
        // first event: clear placeholder
        if (lastEventLines.isEmpty()) timeline.removeAllViews();

        // add only new lines
        int from = lastEventLines.size();
        for (int i = from; i < eventLines.size(); i++) {
            String line = eventLines.get(i);
            TextView ev = text(line, 11, palette().text, false);
            ev.setTypeface(Typeface.MONOSPACE);
            ev.setPadding(dp(3), dp(6), dp(3), dp(6));
            timeline.addView(ev);
            lastEventLines.add(line);
        }
        timelineScroll.post(() -> timelineScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void renderIdleRooms() {
        if (roomCards == null) return;
        roomCards.removeAllViews();
        int count = 2;
        try {
            count = Math.max(1, Math.min(20,
                    Integer.parseInt(numberFields[3].getText().toString().trim())));
        } catch (NumberFormatException ignored) {}
        Palette palette = palette();
        for (int i = 1; i <= count; i++) {
            LinearLayout roomCard = new LinearLayout(this);
            roomCard.setOrientation(LinearLayout.VERTICAL);
            roomCard.setPadding(dp(13), dp(12), dp(13), dp(12));
            roomCard.setBackground(round(palette.raised, palette.border, 12));
            roomCard.addView(text("Treatment room " + i, 13, palette.text, true));
            roomCard.addView(text("Available", 12, TEAL, true));
            roomCard.addView(text("Ready for the next patient", 11, palette.muted, false));
            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(dp(205), ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(8);
            roomCards.addView(roomCard, p);
        }
        lastRooms = null;
    }

    private void renderReport() {
        if (reportValues == null || reportSummary == null) return;
        if (latestSnapshot == null) {
            reportSummary.setText("Run a simulation to generate a report.");
            for (TextView v : reportValues) v.setText("—");
            exportReportButton.setEnabled(false);
            shareReportButton.setEnabled(false);
            for (ReportChartView c : reportCharts) c.setReport(null);
            return;
        }
        SimulationReport report = SimulationReport.from(latestSnapshot, reportEvents);
        reportSummary.setText(String.format(Locale.getDefault(),
                "%s  ·  %.1f%% complete  ·  %d patients  ·  %d events",
                latestSnapshot.status().name(), report.completionPercent(),
                report.totalPatients(), report.eventCount()));
        reportValues[0].setText(report.completedPatients() + " / " + report.totalPatients());
        reportValues[1].setText(String.format(Locale.getDefault(), "%.1f%%",
                report.completionPercent()));
        reportValues[2].setText(String.format(Locale.getDefault(), "%.2f s",
                report.averageTreatmentSeconds()));
        reportValues[3].setText(String.format(Locale.getDefault(), "%.2f s",
                report.simulationSeconds()));
        reportValues[4].setText(Integer.toString(report.eventCount()));
        reportValues[5].setText(Integer.toString(report.warnings()));
        exportReportButton.setEnabled(true);
        shareReportButton.setEnabled(true);
        for (ReportChartView c : reportCharts) c.setReport(report);
        // cache bytes for share
        try {
            lastReportBytes = WordReportExporter.toDocx(report, Instant.now());
        } catch (IOException ignored) {}
    }

    // ── Report export & share ─────────────────────────────────────────────────

    private void exportReport() {
        if (latestSnapshot == null) {
            Toast.makeText(this, "Run a simulation before exporting.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            SimulationReport report = SimulationReport.from(latestSnapshot, reportEvents);
            pendingReport = WordReportExporter.toDocx(report, Instant.now());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            intent.putExtra(Intent.EXTRA_TITLE, "ERONIX-report.docx");
            startActivityForResult(intent, EXPORT_REPORT_REQUEST);
        } catch (IOException | ActivityNotFoundException ex) {
            pendingReport = null;
            Toast.makeText(this, "Could not prepare report: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareReport() {
        if (lastReportBytes == null) {
            Toast.makeText(this, "Refresh the report first.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "reports");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            java.io.File file = new java.io.File(dir, "ERONIX-report.docx");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(lastReportBytes);
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "ERONIX Simulation Report");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share report"));
        } catch (Exception ex) {
            Toast.makeText(this, "Could not share report: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "ERONIX Simulation", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Simulation completion and update alerts");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void postSimulationDoneNotification(boolean completed) {
        if (!canPostNotifications()) return;
        String title = completed ? "Simulation complete! ✓" : "Simulation stopped";
        String body = completed
                ? "All patients have been treated. Open ERONIX to view the report."
                : "The simulation was stopped early. Open ERONIX to view the report.";
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.eronix_icon)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        getSystemService(NotificationManager.class).notify(NOTIF_SIM_DONE, builder.build());
    }

    private void postUpdateNotification(String latestVersion, String releaseUrl) {
        if (!canPostNotifications()) return;
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.eronix_icon)
                .setContentTitle("ERONIX update available 🆕")
                .setContentText("Version " + latestVersion + " is ready to download.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        getSystemService(NotificationManager.class).notify(NOTIF_UPDATE, builder.build());
    }

    // ── Update checking ────────────────────────────────────────────────────────

    /** Silent background check on app start — posts notification if update available. */
    private void checkForUpdatesOnStartup() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return UpdateChecker.checkLatest(BuildConfig.VERSION_NAME);
            } catch (Exception ignored) {
                return null;
            }
        }).whenComplete((result, failure) -> {
            if (result != null && result.updateAvailable()) {
                runOnUiThread(() -> postUpdateNotification(result.latestVersion(), result.releaseUrl()));
            }
        });
    }

    /** Manual check triggered by Settings button — shows dialog result. */
    private void checkForUpdatesManual(Button button) {
        button.setEnabled(false);
        CompletableFuture.supplyAsync(() -> {
            try {
                return UpdateChecker.checkLatest(BuildConfig.VERSION_NAME);
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }).whenComplete((result, failure) -> runOnUiThread(() -> {
            button.setEnabled(true);
            if (isFinishing() || isDestroyed()) return;
            if (failure != null) {
                new AlertDialog.Builder(this)
                        .setTitle("ERONIX updates")
                        .setMessage("Could not check for updates. Check your connection.")
                        .setPositiveButton("OK", null).show();
            } else if (result.updateAvailable()) {
                new AlertDialog.Builder(this)
                        .setTitle("ERONIX updates")
                        .setMessage("Version " + result.latestVersion()
                                + " is available. Open the release page to download it.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Open releases", (d, w) -> {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(result.releaseUrl())));
                        }).show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("ERONIX updates")
                        .setMessage("You are on the latest version (" + BuildConfig.VERSION_NAME + ").")
                        .setPositiveButton("OK", null).show();
            }
        }));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void rebuildForCurrentConfiguration() {
        String[] values = new String[numberFields.length];
        for (int i = 0; i < numberFields.length; i++) {
            values[i] = numberFields[i].getText().toString();
        }
        buildDashboard();
        for (int i = 0; i < numberFields.length; i++) numberFields[i].setText(values[i]);
        if (controller == null) renderIdle();
        else if (latestSnapshot == null) renderStarting();
        else renderSnapshot(latestSnapshot, null);
    }

    private void setInputsEnabled(boolean enabled) {
        for (EditText f : numberFields) f.setEnabled(enabled);
    }

    private void saveSettings() {
        String[] keys = {PREF_SEATS, PREF_DOCTORS, PREF_PATIENTS, PREF_ROOMS,
                PREF_MIN_ARR, PREF_MAX_ARR, PREF_MIN_TRT, PREF_MAX_TRT};
        SharedPreferences.Editor ed = prefs().edit();
        for (int i = 0; i < numberFields.length; i++) {
            ed.putString(keys[i], numberFields[i].getText().toString());
        }
        ed.apply();
    }

    private SharedPreferences prefs() {
        return getPreferences(MODE_PRIVATE);
    }

    private void haptic() {
        Vibrator vib = getSystemService(Vibrator.class);
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private boolean doctorsUnchanged(List<SimulationSnapshot.Doctor> doctors) {
        if (lastDoctors == null || lastDoctors.size() != doctors.size()) return false;
        for (int i = 0; i < doctors.size(); i++) {
            SimulationSnapshot.Doctor a = lastDoctors.get(i);
            SimulationSnapshot.Doctor b = doctors.get(i);
            if (a.status() != b.status()
                    || !java.util.Objects.equals(a.patientId(), b.patientId())) return false;
        }
        return true;
    }

    private boolean roomsUnchanged(List<SimulationSnapshot.TreatmentRoom> rooms) {
        if (lastRooms == null || lastRooms.size() != rooms.size()) return false;
        for (int i = 0; i < rooms.size(); i++) {
            if (lastRooms.get(i).status() != rooms.get(i).status()) return false;
        }
        return true;
    }

    private int integerField(int index, String label, int minimum, int maximum) {
        int value;
        try {
            value = Integer.parseInt(numberFields[index].getText().toString().trim());
        } catch (NumberFormatException ex) {
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

    // ── View factories ────────────────────────────────────────────────────────

    private LinearLayout card(String title, String subtitle, Palette palette) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(round(palette.surface, palette.border, 16));
        card.addView(text(title, 17, palette.text, true));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(3);
        card.addView(text(subtitle, 12, palette.muted, false), sp);
        return card;
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        content.addView(card, p);
    }

    private Button button(String label, Palette palette, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : palette.text);
        b.setBackground(round(primary ? BRAND : palette.raised, palette.border, 12));
        return b;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView text(CharSequence value, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
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
        int sw = getResources().getConfiguration().screenWidthDp;
        int cw = sw >= 840 ? 760 : Math.max(260, sw - 24);
        return Math.max(2, Math.min(8, (cw - 42) / 88));
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private final class ReportChartView extends View {
        static final int PIE = 0;
        static final int BAR = 1;
        static final int HISTOGRAM = 2;
        private final int chartType;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SimulationReport report;

        private ReportChartView(Activity ctx, int chartType) {
            super(ctx);
            this.chartType = chartType;
            setContentDescription("Simulation report chart");
        }

        private void setReport(SimulationReport r) {
            report = r;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Palette colors = palette();
            if (report == null) {
                drawText(canvas, "Run a simulation to see chart data",
                        dp(16), dp(44), colors.muted, 13, false);
                return;
            }
            if (chartType == PIE)            drawPie(canvas, colors);
            else if (chartType == BAR)       drawOutcomeBars(canvas, colors);
            else                             drawHistogram(canvas, colors);
        }

        private void drawPie(Canvas canvas, Palette colors) {
            List<SimulationReport.PatientGroup> groups = report.patientGroups().stream()
                    .filter(g -> g.count() > 0).toList();
            if (groups.isEmpty()) {
                drawText(canvas, "No patient outcomes recorded yet",
                        dp(16), dp(44), colors.muted, 13, false);
                return;
            }
            int[] sliceColors = {BRAND, TEAL, WARNING, SUCCESS,
                    Color.rgb(115, 86, 189), colors.muted};
            float radius = Math.min(dp(78), getWidth() * 0.235f);
            float cx = dp(12) + radius, cy = getHeight() / 2f;
            RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            int total = groups.stream().mapToInt(SimulationReport.PatientGroup::count).sum();
            float start = -90;
            for (int i = 0; i < groups.size(); i++) {
                float sweep = groups.get(i).count() * 360f / total;
                paint.setColor(sliceColors[i % sliceColors.length]);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawArc(bounds, start, sweep, true, paint);
                start += sweep;
            }
            float lx = cx + radius + dp(14);
            float y  = cy - (groups.size() * dp(21)) / 2f;
            for (int i = 0; i < groups.size(); i++) {
                paint.setColor(sliceColors[i % sliceColors.length]);
                canvas.drawCircle(lx + dp(5), y - dp(4), dp(4), paint);
                drawText(canvas, groups.get(i).label() + "  " + groups.get(i).count(),
                        lx + dp(16), y, colors.text, 11, false);
                y += dp(21);
            }
        }

        private void drawOutcomeBars(Canvas canvas, Palette colors) {
            List<SimulationReport.PatientGroup> groups = report.patientGroups();
            int max = Math.max(1, groups.stream()
                    .mapToInt(SimulationReport.PatientGroup::count).max().orElse(0));
            float lw = dp(90), left = dp(16) + lw, right = getWidth() - dp(34);
            float bw = Math.max(dp(30), right - left);
            float rh = Math.min(dp(32), (getHeight() - dp(28)) / (float) groups.size());
            float y = dp(16);
            for (SimulationReport.PatientGroup group : groups) {
                drawText(canvas, group.label(), dp(12), y + dp(14), colors.muted, 10, false);
                paint.setColor(colors.raised); paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(left, y + dp(2), left + bw, y + dp(19), dp(6), dp(6), paint);
                float vw = bw * group.count() / max;
                if (group.count() > 0) {
                    paint.setColor(BRAND);
                    canvas.drawRoundRect(left, y + dp(2), left + vw, y + dp(19), dp(6), dp(6), paint);
                }
                drawText(canvas, Integer.toString(group.count()),
                        Math.min(right - dp(15), left + vw + dp(5)), y + dp(14), colors.text, 10, true);
                y += rh;
            }
        }

        private void drawHistogram(Canvas canvas, Palette colors) {
            List<SimulationReport.DurationBin> bins = report.treatmentHistogram();
            float left = dp(38), right = getWidth() - dp(14);
            float top  = dp(18), bottom = getHeight() - dp(38);
            float ph = Math.max(dp(40), bottom - top);
            int max = Math.max(1, bins.stream()
                    .mapToInt(SimulationReport.DurationBin::count).max().orElse(0));
            paint.setStrokeWidth(dp(1));
            paint.setColor(colors.border);
            for (int line = 0; line <= 4; line++) {
                float y = bottom - ph * line / 4f;
                canvas.drawLine(left, y, right, y, paint);
            }
            float slot = (right - left) / bins.size(), bw = slot * 0.52f;
            for (int i = 0; i < bins.size(); i++) {
                SimulationReport.DurationBin bin = bins.get(i);
                float h = ph * bin.count() / max;
                float x = left + slot * i + (slot - bw) / 2f;
                paint.setColor(TEAL); paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(x, bottom - h, x + bw, bottom, dp(5), dp(5), paint);
                drawText(canvas, Integer.toString(bin.count()), x, bottom - h - dp(5),
                        colors.text, 10, true);
                drawText(canvas, bin.label().replace(" s", ""),
                        left + slot * i, bottom + dp(19), colors.muted, 9, false);
            }
        }

        private void drawText(Canvas canvas, String value, float x, float baseline,
                int color, float sizeSp, boolean bold) {
            paint.setColor(color);
            paint.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,
                    bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(value, x, baseline, paint);
        }
    }

    private record Palette(
            int background, int surface, int raised,
            int text, int muted, int border, int brandSoft) {}
}
