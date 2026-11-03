package com.enguicode.spinlaunchertv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen: huge clock, date and today's weather on the left, cards with the house rooms and the next 7 days on the right, a wheel of apps, and a background that follows the hour. */
public class MainActivity extends Activity {
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final int[] BACKGROUNDS = {R.drawable.bg_mountains, R.drawable.bg_mediterranean,
            R.drawable.bg_forest, R.drawable.bg_dunes, R.drawable.bg_aurora};
    /**
     * Installed apps that declare a launcher entry but must not get a tile in the wheel:
     * this app itself; Android TV settings (they have their own gear button); Google's phone-remote service,
     * which some boxes list as an app (it is not listed on the tested Xiaomi, so this one is only a precaution);
     * and Xiaomi's Miracast receiver, which has its own button in the top bar. They stay installed and enabled;
     * only their tile is skipped.
     */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.enguicode.spinlaunchertv", "com.android.tv.settings", "com.google.android.tv.remote.service",
            "com.xiaomi.mitv.smartshare"));
    /**
     * Apps the roulette must never stop on. They still turn with the wheel and can be opened normally; they are
     * only skipped as winners, and only while at least one other app is eligible (otherwise the draw uses them).
     * Add the package names of the apps you want excluded, for example tools such as a store, a gallery
     * or a media player:
     *   new HashSet<>(Arrays.asList(
     *       "com.android.vending",   // Play Store
     *       "org.videolan.vlc",      // VLC
     *       "com.mitv.gallery"));    // Xiaomi gallery
     * Find a package name with: adb shell pm list packages | grep name
     */
    private static final Set<String> NEVER_WINS = new HashSet<>();
    private static final ComponentName ADD_ACCESSORY =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity");
    /** Xiaomi's Miracast receiver screen: the TV waits there for a Samsung Smart View or Windows mirror. */
    private static final ComponentName MIRACAST =
            new ComponentName("com.xiaomi.mitv.smartshare", "com.xiaomi.mitv.smartshare.wifidisplay.WfdActivity");
    /** SystemUI's exported "start the screensaver now" activity. */
    private static final ComponentName SCREENSAVER =
            new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator");
    private static final long REFRESH_MS = 10 * 60 * 1000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ImageView bgBack, bgFront;
    private TextView clock, date, weather, appName;
    private android.widget.GridLayout house;
    private ImageView weatherIcon;
    private LinearLayout forecast, panel;
    /** Which card the panel shows: the rooms or the next 7 days. */
    private int card = CARD_HOUSE;
    private static final int CARD_HOUSE = 0, CARD_FORECAST = 1;
    private static final long CARD_MS = 20_000;
    /** Fills the current card's marker; when it is full the panel turns to the other card. */
    private ValueAnimator cardTimer;
    private ImageButton bt;
    private DockScroll dockScroll;
    private LinearLayout dock;
    private View lastTile;
    /** The dock tile left large but unlit while focus is up in the top bar; where Down will return. */
    private View resting;
    /** Reorder mode: the last card toggles it; OK picks a tile up, the arrows move it, OK drops it. */
    private boolean editing;
    /** The dock tiles that sit in the top block while reordering. */
    private final List<View> topTiles = new ArrayList<>();
    private View picked;
    /** True while a picked tile is re-inserted, so its brief loss of focus does not put it down. */
    private boolean moving;
    private ImageButton reorder;
    /** Wheel position in tile steps; the focused tile is spun to it, and it keeps growing past the ends. */
    private float wheelPos;
    private ValueAnimator spin;
    /** The wheel draws its tiles this much larger than their natural size, and lifts its arc by WHEEL_LIFT. */
    private static final float WHEEL_SCALE = 1.15f;
    private static final float WHEEL_LIFT = 20;
    /** How much larger than the rest the wheel draws the focused tile's two neighbours. */
    private static final float NEIGHBOUR_BOOST = 0.18f;
    /** Holding Left/Right this long on the wheel lets go of it: it coasts and stops on an app by itself. */
    private static final long LUCKY_HOLD_MS = 3000;
    /** How far the wheel coasts, in seconds' worth of steps at full speed, before the random landing is added. */
    private static final float LUCKY_COAST_S = 3;
    private static final float LUCKY_RAISE = 84, LUCKY_GROW = 1.2f, LUCKY_SWEEP = 200;
    /** How long the wheel runs at the speed the held key gave it before it coasts, and how long the winner holds the stage. */
    private static final long LUCKY_RUN_MS = 320, LUCKY_HOLD_WINNER_MS = 1500, LUCKY_REOPEN_MS = 520;
    /** Steps per second below which the app name is readable, and fully shown. */
    private static final float NAME_HIDDEN_SPEED = 5.5f, NAME_SHOWN_SPEED = 2.5f;
    /** True while the wheel coasts; the remote is ignored, as is the rest of the press that started it. */
    private boolean lucky;
    private long luckyPress = -1;
    private final Random random = new Random();
    /** The most watched apps lately, read as each spin starts; each gets a second ticket in the draw. */
    private volatile List<String> favourites = new ArrayList<>();
    private static final int FAVOURITES = 5;
    /** The divider's resting opacity, as set in the layout. */
    private static final float SPLIT_ALPHA = 0.22f;
    private static final long FAVOURITES_WINDOW_MS = 30L * 24 * 60 * 60 * 1000;
    private int period = -1;
    private boolean away;
    private ValueAnimator drift;
    private float dp;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateBackground(true); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildDock(); }
    };
    private final BroadcastReceiver bluetooth = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateBluetooth(); }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { loadWeather(); loadHouse(); main.postDelayed(this, REFRESH_MS); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        bgBack = findViewById(R.id.bg_back);
        bgFront = findViewById(R.id.bg_front);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        weather = findViewById(R.id.weather);
        weatherIcon = findViewById(R.id.weather_icon);
        forecast = findViewById(R.id.forecast);
        panel = findViewById(R.id.panel);
        house = findViewById(R.id.house);
        // The panel stays vertically centred on the clock block, whatever either one's height.
        View.OnLayoutChangeListener align = (v, l, t, r, b, ol, ot, or, ob) -> {
            View block = findViewById(R.id.center);
            // +15dp: the clock line box has room above the digits that the eye does not count (measured on the TV).
            panel.setTranslationY(block.getTop() + block.getHeight() / 2f - (panel.getTop() + panel.getHeight() / 2f) + 15 * dp);
        };
        findViewById(R.id.center).addOnLayoutChangeListener(align);
        // Start where the clock will end up, so it does not jump when the first readings arrive.
        placeClock(!config().optString("token").isEmpty());
        panel.addOnLayoutChangeListener(align);
        appName = findViewById(R.id.app_name);
        dockScroll = findViewById(R.id.dock_scroll);
        dock = findViewById(R.id.dock);
        bt = findViewById(R.id.bt);

        setupPanel();
        setupTopButton(bt, () -> open(new Intent().setComponent(ADD_ACCESSORY)));
        setupTopButton(findViewById(R.id.mirror), () -> open(new Intent().setComponent(MIRACAST)));
        setupTopButton(findViewById(R.id.screensaver), () -> open(new Intent().setComponent(SCREENSAVER)));
        reorder = findViewById(R.id.reorder);
        setupTopButton(reorder, () -> { if (editing) finishEditing(); else startEditing(); });
        setupTopButton(findViewById(R.id.settings), () -> open(new Intent(Settings.ACTION_SETTINGS)));

        IntentFilter pkg = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addDataScheme("package");
        registerReceiver(packages, pkg);
        registerReceiver(bluetooth, new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED));

        buildDock();
        startDrift();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavourites();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, time);
        updateClock();
        updateBackground(period != -1);
        updateBluetooth();
        main.post(refresh);
        if (drift != null) drift.resume();
        if (away) enter();
        away = false;
        holdCardTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(tick);
        main.removeCallbacks(refresh);
        if (drift != null) drift.pause();
        away = true;
        holdCardTimer();
        // Coming back animates the clock in, which would fight the block in its place.
        if (editing) finishEditing();
        // Leaving (Home, a remote app key, the screensaver) ends the game where it is.
        if (lucky) spin.cancel();
        ((ConfettiView) findViewById(R.id.confetti)).stop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(bluetooth);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        return lucky || e.getDownTime() == luckyPress || super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; Back leaves reorder mode, or returns focus to the dock.
        if (editing) finishEditing();
        else if (lastTile != null) lastTile.requestFocus();
    }

    // ---- clock and background -------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", ES).format(now));
        date.setText(new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(now));
    }

    /** Crossfades to the background of the current hour; the old image stays underneath until the fade ends. */
    private void updateBackground(boolean animate) {
        int next = Sky.period(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        if (next == period) return;
        period = next;
        if (!animate) {
            bgFront.setImageResource(BACKGROUNDS[next]);
            bgBack.setImageDrawable(null);
            return;
        }
        bgBack.setImageDrawable(bgFront.getDrawable());
        bgFront.setAlpha(0f);
        bgFront.setImageResource(BACKGROUNDS[next]);
        bgFront.animate().alpha(1f).setDuration(2500).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> bgBack.setImageDrawable(null)).start();
    }

    /** A slow push-in on the background, so the screen is never quite still. */
    private void startDrift() {
        drift = ValueAnimator.ofFloat(1f, 1.06f);
        drift.setDuration(45_000);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            bgFront.setScaleX(s); bgFront.setScaleY(s);
            bgBack.setScaleX(s); bgBack.setScaleY(s);
        });
        drift.start();
    }

    /** Coming back from an app: the clock settles in and the dock rises tile by tile. */
    private void enter() {
        View center = findViewById(R.id.center);
        center.setAlpha(0.3f);
        center.setTranslationY(12 * dp);
        center.animate().alpha(1f).translationY(0).setDuration(520).setInterpolator(new DecelerateInterpolator(2f)).start();
        if (lastTile == null) return;
        wheelPos -= 1.5f;
        spinTo(dock.indexOfChild(lastTile), 700);
    }

    // ---- dock ------------------------------------------------------------------------------------

    private void buildDock() {
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(leanback, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (!HIDDEN.contains(p) && seen.add(p)) apps.add(r);
        }
        List<String> saved = DockOrder.parse(prefs.getString("order", null));
        List<String> top = DockOrder.parse(prefs.getString("top", null));
        apps.sort((a, b) -> {
            int ia = DockOrder.rank(a.activityInfo.packageName, top, saved);
            int ib = DockOrder.rank(b.activityInfo.packageName, top, saved);
            if (ia != ib) return Integer.compare(ia, ib);
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });

        if (editing) {
            hideTopBlock();
            showReorderDone(false);
        }
        topTiles.clear();
        editing = false;
        picked = null;
        dock.removeAllViews();
        // Starts at the screen margin; past the middle the scroll keeps the focused tile centred.
        int side = Math.round(48 * dp);
        dock.setPadding(side, 0, side, 0);
        // The app focused last time, or the first one the first time.
        String last = prefs.getString("last", null);
        View first = null, focus = null;
        for (ResolveInfo r : apps) {
            View t = tile(pm, r);
            dock.addView(t);
            if (first == null) first = t;
            if (r.activityInfo.packageName.equals(last)) focus = t;
        }
        if (focus == null) focus = first;
        View f = focus;
        dock.post(() -> {
            if (f != null) f.requestFocus();
            resetDock();
        });
    }

    /** The rounded, focusable shell shared by app tiles and the reorder card. */
    private FrameLayout shell(int color) {
        FrameLayout t = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(110 * dp), Math.round(62 * dp));
        lp.setMargins(Math.round(9 * dp), 0, Math.round(9 * dp), 0);
        t.setLayoutParams(lp);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(8 * dp);
        t.setBackground(shape);
        t.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8 * dp); }
        });
        t.setClipToOutline(true);
        t.setFocusable(true);
        t.setAlpha(0.62f);
        t.setNextFocusUpId(R.id.bt);
        t.setOnKeyListener(this::dockKey);
        return t;
    }

    /**
     * Focus look shared by the app tiles. The lit state (full opacity) lands on the frame focus moves, and the
     * grow is short and without overshoot, so a fast run of presses never leaves the highlight a tile behind.
     * Tiles grow upwards from their bottom edge; a picked-up tile grows more and floats off the row.
     */
    private void focusLook(View v, boolean has) {
        v.animate().cancel();
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight());
        if (has) {
            lastTile = v;
            resting = null;
            // During the game layoutWheel sizes every tile, the focused one included.
            if (lucky) return;
            boolean lifted = v == picked;
            v.setAlpha(1f);
            v.setTranslationZ(12 * dp);
            float scale = lifted ? 1.6f : 1.5f;
            v.animate().scaleX(scale * WHEEL_SCALE).scaleY(scale * WHEEL_SCALE).setDuration(120)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            spinTo(dock.indexOfChild(v), 260);
        } else {
            if (v == picked && !moving) drop();
            v.setTranslationZ(0);
            // The wheel sizes unfocused tiles itself, by their distance from the top.
            layoutWheel();
        }
    }

    /** Keeps a dock tile at its focused size but unlit (dimmer, no shadow) while focus is in the top bar. */
    private void rest(View v) {
        resting = v;
        v.animate().cancel();
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight());
        v.setTranslationZ(0);
        float scale = 1.5f * WHEEL_SCALE;
        v.animate().scaleX(scale).scaleY(scale).setDuration(120).setInterpolator(new DecelerateInterpolator(2f)).start();
        layoutWheel();
    }

    /**
     * Left/Right at either end wraps around the row instead of letting focus escape to the top bar.
     * While a tile is picked up, the arrows move it instead.
     */
    private boolean dockKey(View v, int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) (editing ? reorder : panel.getVisibility() == View.VISIBLE ? panel : bt).requestFocus();
            return true;
        }
        boolean right = code == KeyEvent.KEYCODE_DPAD_RIGHT, left = code == KeyEvent.KEYCODE_DPAD_LEFT;
        if (!right && !left) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int i = dock.indexOfChild(v), n = dock.getChildCount();
        if (!editing && n > 0 && e.getEventTime() - e.getDownTime() >= LUCKY_HOLD_MS) {
            startLucky(right ? 1 : -1, e);
            return true;
        }
        // The wheel moves focus by hand, so Android would not click for it as it does in the top bar.
        int click = right ? SoundEffectConstants.NAVIGATION_RIGHT : SoundEffectConstants.NAVIGATION_LEFT;
        if (v == picked) {
            int to = i + (right ? 1 : -1);
            if (to >= 0 && to <= n - 1) {
                dock.playSoundEffect(click);
                // Moving the focused view would hand focus away; block that while it is re-inserted.
                moving = true;
                dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                dock.removeView(v);
                dock.addView(v, to);
                dock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                v.requestFocus();
                moving = false;
                spinTo(to, 200);
            }
            return true;
        }
        dock.playSoundEffect(click);
        dock.getChildAt((i + (right ? 1 : -1) + n) % n).requestFocus();
        return true;
    }

    /** The reorder button: a check while reordering, «Hecho». */
    private void showReorderDone(boolean done) {
        reorder.setImageResource(done ? R.drawable.ic_check : R.drawable.ic_reorder);
        reorder.setContentDescription(getString(done ? R.string.reorder_done : R.string.reorder));
    }

    /** Lays the dock out again from clean transforms, with the wheel on the last focused tile. */
    private void resetDock() {
        if (spin != null) spin.cancel();
        dockScroll.setLocked(true);
        dockScroll.setHorizontalFadingEdgeEnabled(false);
        for (int i = 0; i < dock.getChildCount(); i++) {
            View c = dock.getChildAt(i);
            c.setTranslationX(0);
            c.setTranslationY(0);
            c.setRotation(0);
            c.setAlpha(c.hasFocus() ? 1f : 0.62f);
            if (!c.hasFocus()) {
                c.setPivotX(c.getWidth() / 2f);
                c.setPivotY(c.getHeight());
                c.setScaleX(WHEEL_SCALE);
                c.setScaleY(WHEEL_SCALE);
            }
        }
        if (lastTile == null) return;
        wheelPos = dock.indexOfChild(lastTile);
        layoutWheel();
    }

    /** Turns the wheel the short way round to put tile {@code index} on top. */
    private void spinTo(int index, long duration) {
        if (spin != null) spin.cancel();
        float to = wheelPos + Wheel.wrap(index - wheelPos, dock.getChildCount());
        spin = ValueAnimator.ofFloat(wheelPos, to);
        spin.setDuration(duration);
        spin.setInterpolator(new DecelerateInterpolator(2f));
        spin.addUpdateListener(a -> {
            wheelPos = (float) a.getAnimatedValue();
            layoutWheel();
        });
        spin.start();
    }

    /**
     * The minigame, one animator after another in {@code spin}, so cancelling it always ends the game: the wheel keeps
     * the speed the held key gave it, then friction stops it on a random app and the winner holds the stage.
     */
    private void startLucky(int direction, KeyEvent press) {
        lucky = true;
        holdCardTimer();
        luckyPress = press.getDownTime();
        showLuckyStage(true, 0);
        loadFavourites();
        if (spin != null) spin.cancel();
        // Measured, not assumed: the key repeat rate and how well the box keeps up both vary.
        float speed = Math.max(8f, press.getRepeatCount() * 1000f / (press.getEventTime() - press.getDownTime()));
        wheelPos = Wheel.wrap(wheelPos, dock.getChildCount());
        float[] last = {0};
        spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(LUCKY_RUN_MS);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            wheelPos = Wheel.wrap(wheelPos + direction * speed * LUCKY_RUN_MS / 1000f * (t - last[0]), apps());
            last[0] = t;
            layoutLucky();
        });
        after(spin, () -> coast(direction, speed));
        spin.start();
    }

    private void coast(int direction, float speed) {
        int apps = apps();
        BeamsView beams = findViewById(R.id.lucky_beams);
        int to = Wheel.spinOut(wheelPos, direction, Math.round(speed * LUCKY_COAST_S), pickWinner(apps), apps);
        spin = ValueAnimator.ofFloat(wheelPos, to);
        // This curve is constant friction and starts at twice its average speed, so the hand-off has no jump.
        spin.setInterpolator(new DecelerateInterpolator());
        spin.setDuration((long) (2000 * Math.abs(to - wheelPos) / speed));
        spin.addUpdateListener(a -> {
            wheelPos = (float) a.getAnimatedValue();
            float now = speed * (float) Math.sqrt(1 - a.getAnimatedFraction());
            // The lights sweep the stage while the wheel is fast, then close in on the card as it slows.
            float slow = now / speed;
            beams.aim(LUCKY_SWEEP * slow * (float) Math.sin(a.getCurrentPlayTime() * 0.0021), 0.55f + 0.45f * (1 - slow * slow));
            appName.setAlpha(0.9f * Math.max(0f, Math.min(1f, (NAME_HIDDEN_SPEED - now) / (NAME_HIDDEN_SPEED - NAME_SHOWN_SPEED))));
            layoutLucky();
        });
        after(spin, () -> { wheelPos = Math.floorMod(to, apps); settle(); });
        spin.start();
    }

    private int pickWinner(int apps) {
        List<Integer> tickets = new ArrayList<>();
        for (int i = 0; i < apps; i++) {
            Object pkg = dock.getChildAt(i).getTag();
            if (NEVER_WINS.contains(pkg)) continue;
            tickets.add(i);
            if (favourites.contains(pkg)) tickets.add(i);
        }
        return tickets.isEmpty() ? random.nextInt(apps) : tickets.get(random.nextInt(tickets.size()));
    }

    /** Off the main thread, so the spin never stutters on it; without the permission the map is empty and the draw even. */
    private void loadFavourites() {
        Set<String> candidates = new HashSet<>();
        for (int i = 0; i < apps(); i++) {
            Object pkg = dock.getChildAt(i).getTag();
            if (!NEVER_WINS.contains(pkg)) candidates.add((String) pkg);
        }
        io.execute(() -> {
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = ((UsageStatsManager) getSystemService(USAGE_STATS_SERVICE))
                    .queryAndAggregateUsageStats(now - FAVOURITES_WINDOW_MS, now);
            Map<String, Long> time = new HashMap<>();
            for (Map.Entry<String, UsageStats> e : stats.entrySet()) time.put(e.getKey(), e.getValue().getTotalTimeInForeground());
            favourites = Wheel.top(time, candidates, FAVOURITES);
            Log.i("Spin", "wheel favourites: " + favourites);
            main.post(() -> {
                showFavourites();
                reorderTop();
            });
        });
    }

    /** Keeps the most used apps at the start of the dock, saved so that the first build after a restart already has them. */
    private void reorderTop() {
        String now = String.join(",", favourites);
        if (lucky || editing || now.equals(prefs.getString("top", ""))) return;
        prefs.edit().putString("top", now).apply();
        buildDock();
    }

    /** Lists the double-odds apps in a line across the top, coming down one after another. */
    private void showFavourites() {
        LinearLayout list = findViewById(R.id.lucky_top);
        list.removeAllViews();
        if (!lucky || favourites.isEmpty()) return;
        list.addView(favouriteLine(getString(R.string.lucky_top), 11, 0.72f, R.font.inter_500));
        int rank = 0;
        for (String pkg : favourites) {
            View tile = dock.findViewWithTag(pkg);
            if (tile == null) continue;
            rank++;
            TextView line = favouriteLine(rank + "   " + tile.getContentDescription(), 19, 1f, R.font.inter_300);
            line.setPadding(Math.round(30 * dp), 0, 0, 0);
            list.addView(line);
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            float alpha = v.getAlpha();
            v.setAlpha(0f);
            v.setTranslationY(-16 * dp);
            v.animate().alpha(alpha).translationY(0).setStartDelay(150 + i * 60L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private TextView favouriteLine(String text, int size, float alpha, int font) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setAlpha(alpha);
        t.setTypeface(getResources().getFont(font));
        t.setFontFeatureSettings("tnum");
        t.setSingleLine(true);
        return t;
    }

    /** The winner hops once and rests; the stage then clears. */
    private void settle() {
        ((BeamsView) findViewById(R.id.lucky_beams)).aim(0, 1f);
        throwConfetti();
        showLuckyStage(false, LUCKY_HOLD_WINNER_MS);
        long total = LUCKY_HOLD_WINNER_MS + LUCKY_REOPEN_MS;
        spin = ValueAnimator.ofFloat(0f, total);
        spin.setDuration(total);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            float ms = (float) a.getAnimatedValue();
            layoutLucky();
            if (lastTile == null) return;
            float hop = 1 + Wheel.hop(ms / 600f, 0.09f);
            lastTile.setScaleX(lastTile.getScaleX() * hop);
            lastTile.setScaleY(lastTile.getScaleY() * hop);
        });
        after(spin, this::finishLucky);
        spin.start();
    }

    /** A burst from the winner, then two firecrackers going off at its sides. */
    private void throwConfetti() {
        layoutWheel();
        if (lastTile == null) return;
        int[] at = new int[2];
        lastTile.getLocationOnScreen(at);
        float w = lastTile.getWidth() * lastTile.getScaleX();
        float x = at[0] + w / 2, y = at[1];
        ((ConfettiView) findViewById(R.id.confetti)).fire(new float[][]{
                {x, y, 0f, 110, 1150},
                {x - w * 1.1f, y + 20 * dp, 0.22f, 50, 850},
                {x + w * 1.1f, y + 20 * dp, 0.38f, 50, 850}});
    }

    /** Runs {@code next} when the step completes; a cancelled step skips the rest of the game. */
    private void after(ValueAnimator step, Runnable next) {
        step.addListener(new AnimatorListenerAdapter() {
            private boolean cut;
            @Override public void onAnimationCancel(Animator a) { cut = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (cut) finishLucky(); else next.run();
            }
        });
    }

    private void finishLucky() {
        lucky = false;
        holdCardTimer();
        appName.setAlpha(0.9f);
        // Interrupted runs stop between two tiles; settle on the focused one.
        int at = dock.indexOfChild(lastTile);
        if (at >= 0) wheelPos = at;
        showLuckyStage(false, 0);
        layoutWheel();
        if (at >= 0) focusLook(lastTile, true);
    }

    /** Keeps focus on the app at the top of the ring, so the winner ends up selected, then lays the ring out. */
    private void layoutLucky() {
        int apps = apps();
        int i = Math.round(((wheelPos % apps) + apps) % apps);
        if (i >= apps) i = 0;
        if (!dock.getChildAt(i).hasFocus()) dock.getChildAt(i).requestFocus();
        layoutWheel();
    }

    private int apps() {
        return dock.getChildCount();
    }

    /** The wheel rises and grows over a spotlit, receding home; on the way in it first dips, like drawing breath. */
    private void showLuckyStage(boolean on, long delay) {
        android.view.animation.Interpolator in = new PathInterpolator(0.3f, -0.12f, 0.1f, 1f);
        android.view.animation.Interpolator out = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
        long time = on ? 700 : LUCKY_REOPEN_MS;
        float raise = on ? -LUCKY_RAISE * dp : 0, grow = on ? LUCKY_GROW : 1f;
        dockScroll.setPivotX(dockScroll.getWidth() / 2f);
        dockScroll.setPivotY(dockScroll.getHeight());
        dockScroll.animate().translationY(raise).scaleX(grow).scaleY(grow).setStartDelay(delay).setDuration(time)
                .setInterpolator(on ? in : out).start();
        appName.animate().translationY(raise).setStartDelay(delay).setDuration(time).setInterpolator(on ? in : out).start();
        // The home steps back with the wheel but stays behind it: gentler curve, no dip.
        float back = on ? 0.96f : 1f;
        for (int id : new int[]{R.id.center, R.id.panel, R.id.top}) {
            findViewById(id).animate().scaleX(back).scaleY(back).setStartDelay(delay).setDuration(time).setInterpolator(out).start();
        }
        // The clock, cards and top buttons give way to the top apps, which would be unreadable over them.
        panel.animate().alpha(on ? 0f : 1f);
        findViewById(R.id.center).animate().alpha(on ? 0f : 1f);
        findViewById(R.id.split).animate().alpha(on ? 0f : SPLIT_ALPHA).setStartDelay(delay).setDuration(time).setInterpolator(out).start();
        findViewById(R.id.top).animate().alpha(on ? 0f : 1f);
        for (int id : new int[]{R.id.lucky_scrim, R.id.lucky_beams, R.id.lucky_top}) {
            findViewById(id).animate().alpha(on ? 1f : 0f).setStartDelay(delay).setDuration(on ? 500 : LUCKY_REOPEN_MS)
                    .setInterpolator(out).start();
        }
    }

    /** Places every dock item on the ring for the current wheel position: shifted, dropped, tilted and faded. */
    private void layoutWheel() {
        int n = dock.getChildCount();
        if (n == 0) return;
        float top = dockScroll.getWidth() / 2f;
        float radius = 1000 * dp, spacing = 126 * WHEEL_SCALE * dp;
        float extra = 34 * WHEEL_SCALE * dp, extra2 = 12 * WHEEL_SCALE * dp;
        int apps = apps();
        for (int i = 0; i < n; i++) {
            View c = dock.getChildAt(i);
            float d = Wheel.wrap(i - wheelPos, apps);
            float[] p = Wheel.place(d, radius, spacing, extra, extra2);
            float natural = dock.getLeft() + c.getLeft() + c.getWidth() / 2f;
            c.setTranslationX(top + p[0] - natural);
            c.setTranslationY(p[1] - WHEEL_LIFT * dp);
            c.setRotation(p[2]);
            if (lucky) {
                // Size and light follow the distance from the top, so a fast wheel never strobes tile by tile.
                float peak = Math.max(0f, 1f - Math.abs(d));
                float size = WHEEL_SCALE * Math.max(1f + 0.5f * peak, Wheel.neighbourBoost(d, NEIGHBOUR_BOOST));
                c.setPivotX(c.getWidth() / 2f);
                c.setPivotY(c.getHeight());
                c.setScaleX(size);
                c.setScaleY(size);
                c.setAlpha((0.62f + 0.38f * peak) * p[3]);
                continue;
            }
            float base = c.hasFocus() ? 1f : (c == resting ? 0.85f : 0.62f);
            if (!c.hasFocus() && c != resting) {
                // The two beside the focused tile are a little larger than the rest, not as large as the focused one.
                float size = WHEEL_SCALE * Wheel.neighbourBoost(d, NEIGHBOUR_BOOST);
                c.setPivotX(c.getWidth() / 2f);
                c.setPivotY(c.getHeight());
                c.setScaleX(size);
                c.setScaleY(size);
            }
            c.setAlpha(base * p[3]);
        }
    }

    private void startEditing() {
        editing = true;
        showReorderDone(true);
        holdCardTimer();
        showTopBlock();
        // showTopBlock may have taken the last app out of the dock.
        View target = lastTile != null && lastTile.getParent() == dock ? lastTile : dock.getChildAt(0);
        if (target != null) target.requestFocus();
        setAppName(getString(R.string.reorder_hint));
    }

    /** The dock is not rebuilt: removing the focused card would throw focus onto some other tile. */
    private void finishEditing() {
        drop();
        for (int i = 0; i < topTiles.size(); i++) dock.addView(topTiles.get(i), i);
        topTiles.clear();
        hideTopBlock();
        editing = false;
        showReorderDone(false);
        if (lastTile != null) setAppName(String.valueOf(lastTile.getContentDescription()));
        dock.post(this::resetDock);
    }

    /** The apps that order themselves leave the dock for the clock's place, so only what can be moved stays below. */
    private void showTopBlock() {
        LinearLayout cards = findViewById(R.id.reorder_cards);
        cards.removeAllViews();
        for (String pkg : DockOrder.parse(prefs.getString("top", null))) {
            View tile = dock.findViewWithTag(pkg);
            if (tile == null) continue;
            dock.removeView(tile);
            topTiles.add(tile);
            cards.addView(topCard(tile, cards.getChildCount() + 1));
        }
        if (cards.getChildCount() == 0) return;
        dock.post(this::resetDock);
        for (int id : new int[]{R.id.center, R.id.split, R.id.panel}) findViewById(id).animate().alpha(0f).setDuration(280).start();
        findViewById(R.id.reorder_top).animate().alpha(1f).setDuration(280).start();
        for (int i = 0; i < cards.getChildCount(); i++) {
            View c = cards.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(14 * dp);
            c.animate().alpha(1f).translationY(0).setStartDelay(120 + i * 50L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private void hideTopBlock() {
        findViewById(R.id.reorder_top).animate().alpha(0f).setDuration(240).start();
        findViewById(R.id.center).animate().alpha(1f).setDuration(360).start();
        findViewById(R.id.split).animate().alpha(SPLIT_ALPHA).setDuration(360).start();
        panel.animate().alpha(1f).setDuration(360).start();
        main.post(this::holdCardTimer);
    }

    /** A still copy of a dock tile with its rank and name underneath. */
    private View topCard(View tile, int rank) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2);
        at.setMargins(Math.round(10 * dp), 0, Math.round(10 * dp), 0);
        column.setLayoutParams(at);
        FrameLayout card = shell(Color.rgb(34, 36, 42));
        card.setFocusable(false);
        card.setOnKeyListener(null);
        card.setAlpha(1f);
        ImageView from = (ImageView) ((ViewGroup) tile).getChildAt(0);
        ImageView art = new ImageView(this);
        art.setScaleType(from.getScaleType());
        art.setImageDrawable(from.getDrawable());
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));
        column.addView(card, new LinearLayout.LayoutParams(Math.round(150 * dp), Math.round(84 * dp)));
        TextView name = favouriteLine(rank + "   " + tile.getContentDescription(), 14, 0.85f, R.font.inter_300);
        name.setPadding(0, Math.round(9 * dp), 0, 0);
        column.addView(name);
        return column;
    }

    private void pick(View v) {
        picked = v;
        focusLook(v, true);
        setAppName(getString(R.string.reorder_move));
    }

    /** Puts a picked-up tile down where it is and saves the row order. */
    private void drop() {
        if (picked == null) return;
        View v = picked;
        picked = null;
        if (v.hasFocus()) {
            focusLook(v, true);
            setAppName(String.valueOf(v.getContentDescription()));
        }
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) {
            Object tag = dock.getChildAt(i).getTag();
            rest.add((String) tag);
        }
        List<String> order = DockOrder.keepingTop(rest, DockOrder.parse(prefs.getString("top", null)),
                DockOrder.parse(prefs.getString("order", null)));
        prefs.edit().putString("order", String.join(",", order)).apply();
    }

    private View tile(PackageManager pm, ResolveInfo r) {
        String pkg = r.activityInfo.packageName;
        String label = r.loadLabel(pm).toString();
        FrameLayout t = shell(Color.rgb(34, 36, 42));
        t.setTag(pkg);
        t.setContentDescription(label);

        ImageView art = new ImageView(this);
        Drawable banner = r.activityInfo.loadBanner(pm);
        if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
        if (banner != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(banner);
            t.addView(art, new FrameLayout.LayoutParams(-1, -1));
        } else {
            art.setImageDrawable(r.loadIcon(pm));
            int s = Math.round(34 * dp);
            t.addView(art, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        t.setOnFocusChangeListener((v, has) -> {
            if (has && v != picked) setAppName(label);
            focusLook(v, has);
        });
        t.setOnClickListener(v -> {
            if (!editing) launch(pkg, v);
            else if (picked == v) drop();
            else pick(v);
        });
        return t;
    }

        private void setAppName(String name) {
        // Swapped instantly: a fade here trailed behind fast presses.
        appName.setText(name);
    }

    private void launch(String pkg, View from) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        prefs.edit().putString("last", pkg).apply();
        ActivityOptions grow = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight());
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), grow.toBundle());
    }

    // ---- top buttons -----------------------------------------------------------------------------

    private void setupTopButton(ImageButton b, Runnable action) {
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, has) -> {
            if (has && lastTile != null && lastTile.getParent() == dock) rest(lastTile);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
            tintTopButton((ImageButton) v);
        });
        b.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN && lastTile != null) {
                lastTile.requestFocus();
                return true;
            }
            return false;
        });
    }

    private boolean headphones() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED;
        } catch (SecurityException e) {
            return false;
        }
    }

    private void updateBluetooth() {
        tintTopButton(bt);
    }

    /** Focused: dark icon on the white disc. Idle: white at 72%; Bluetooth turns blue while headphones are connected. */
    private void tintTopButton(ImageButton b) {
        boolean lit = b == bt && headphones();
        if (b.hasFocus()) {
            b.setColorFilter(Color.rgb(17, 17, 17));
            b.setAlpha(1f);
        } else {
            b.setColorFilter(lit ? Color.rgb(159, 203, 255) : Color.WHITE);
            b.setAlpha(lit ? 1f : 0.72f);
        }
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ---- weather and house -----------------------------------------------------------------------

    /** Optional private home.json, written through ConfigProvider: {"place":"Valencia","lat":..,"lon":..,"url":"http://ha:8123","token":"..","sensors":[{"label":"Salón","entity":"sensor.x"}]} */
    private JSONObject config() {
        try {
            File f = new File(getFilesDir(), ConfigProvider.FILE);
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONArray rooms() {
        try {
            File f = new File(getFilesDir(), ConfigProvider.ROOMS);
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).optJSONArray("sensors");
        } catch (Exception e) {
            return null;
        }
    }

    private void loadWeather() {
        io.execute(() -> {
            try {
                JSONObject cfg = config();
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=8&timezone=auto",
                        cfg.optDouble("lat", 39.47), cfg.optDouble("lon", -0.376));
                JSONObject answer = new JSONObject(get(url, null));
                JSONObject cur = answer.getJSONObject("current"), daily = answer.getJSONObject("daily");
                int code = cur.getInt("weather_code");
                boolean day = cur.optInt("is_day", 1) == 1;
                String text = cfg.optString("place", "Valencia") + " · "
                        + Math.round(cur.getDouble("temperature_2m")) + "° · " + Sky.label(code);
                int icon = Sky.icon(code) == Sky.ICON_CLEAR && !day ? R.drawable.ic_moon : skyIcon(code);
                main.post(() -> {
                    weather.setText(text);
                    weatherIcon.setImageResource(icon);
                    weather.setVisibility(View.VISIBLE);
                    weatherIcon.setVisibility(View.VISIBLE);
                    showForecast(daily);
                });
            } catch (Exception ignored) {
                // No network: the forecast keeps its last values, or stays hidden.
            }
        });
    }

    // ---- information cards -----------------------------------------------------------------------

    private void setupPanel() {
        findViewById(R.id.marker_house).setClipToOutline(true);
        findViewById(R.id.marker_forecast).setClipToOutline(true);
        panel.setOnFocusChangeListener((v, has) -> {
            if (has && lastTile != null && lastTile.getParent() == dock) rest(lastTile);
            holdCardTimer();
        });
        panel.setOnKeyListener((v, code, e) -> {
            boolean right = code == KeyEvent.KEYCODE_DPAD_RIGHT, left = code == KeyEvent.KEYCODE_DPAD_LEFT;
            boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
            if (!right && !left && !up && !down) return false;
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (up) bt.requestFocus();
            else if (down) { if (lastTile != null) lastTile.requestFocus(); }
            else switchCard(right ? 1 : -1);
            return true;
        });
    }

    /** Starts the current card's marker from empty. */
    private void startCardTimer() {
        if (cardTimer != null) cardTimer.cancel();
        View fill = findViewById(card == CARD_HOUSE ? R.id.fill_house : R.id.fill_forecast);
        fill.setScaleX(0f);
        findViewById(card == CARD_HOUSE ? R.id.fill_forecast : R.id.fill_house).setScaleX(0f);
        if (house.getChildCount() == 0 || forecast.getChildCount() == 0) return;
        cardTimer = ValueAnimator.ofFloat(0f, 1f);
        cardTimer.setDuration(CARD_MS);
        cardTimer.setInterpolator(new LinearInterpolator());
        cardTimer.addUpdateListener(a -> fill.setScaleX((float) a.getAnimatedValue()));
        cardTimer.addListener(new AnimatorListenerAdapter() {
            private boolean cut;
            @Override public void onAnimationCancel(Animator a) { cut = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (!cut) switchCard(1);
            }
        });
        cardTimer.start();
        holdCardTimer();
    }

    /** Nothing turns on its own while the remote is on the panel or the wheel has the stage: the marker waits where it is. */
    private void holdCardTimer() {
        if (cardTimer == null) return;
        if (panel.hasFocus() || lucky || away || editing) cardTimer.pause(); else cardTimer.resume();
    }

    /** Places the clock and shows the current card for what has loaded so far, without animating. */
    private void refreshPanel() {
        boolean rooms = house.getChildCount() > 0, week = forecast.getChildCount() > 0;
        int before = card;
        placeClock(rooms || week);
        panel.setVisibility(rooms || week ? View.VISIBLE : View.GONE);
        if (card == CARD_HOUSE && !rooms) card = CARD_FORECAST;
        if (card == CARD_FORECAST && !week) card = CARD_HOUSE;
        house.animate().cancel();
        forecast.animate().cancel();
        // Invisible, not gone: the panel keeps its height, so nothing jumps as the cards turn.
        house.setVisibility(!rooms ? View.GONE : card == CARD_HOUSE ? View.VISIBLE : View.INVISIBLE);
        forecast.setVisibility(!week ? View.GONE : card == CARD_FORECAST ? View.VISIBLE : View.INVISIBLE);
        house.setAlpha(1f);
        house.setTranslationX(0);
        forecast.setAlpha(1f);
        forecast.setTranslationX(0);
        findViewById(R.id.panel_dots).setVisibility(rooms && week ? View.VISIBLE : View.GONE);
        // Readings arrive every few minutes: only a new card restarts the marker, or the first time both exist.
        if (card != before || cardTimer == null || !cardTimer.isRunning()) startCardTimer();
    }

    /** Crossfades to the other card, sliding a little the way the arrow points. */
    private void switchCard(int direction) {
        if (house.getChildCount() == 0 || forecast.getChildCount() == 0) return;
        View from = card == CARD_HOUSE ? house : forecast, to = card == CARD_HOUSE ? forecast : house;
        card = 1 - card;
        startCardTimer();
        from.animate().cancel();
        to.animate().cancel();
        from.setAlpha(1f);
        from.setTranslationX(0);
        from.animate().alpha(0f).translationX(-direction * 16 * dp).setDuration(220)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f))
                .withEndAction(() -> from.setVisibility(View.INVISIBLE)).start();
        to.setVisibility(View.VISIBLE);
        to.setAlpha(0f);
        to.setTranslationX(direction * 24 * dp);
        to.animate().alpha(1f).translationX(0).setStartDelay(120).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    /** One column per day from tomorrow on: weekday, sky icon, high and low. */
    private void showForecast(JSONObject daily) {
        try {
            JSONArray days = daily.getJSONArray("time"), codes = daily.getJSONArray("weather_code");
            JSONArray highs = daily.getJSONArray("temperature_2m_max"), lows = daily.getJSONArray("temperature_2m_min");
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", ES), weekday = new SimpleDateFormat("EEE", ES);
            forecast.removeAllViews();
            for (int i = 1; i < days.length(); i++) {
                String name = weekday.format(iso.parse(days.getString(i))).replace(".", "");
                int high = (int) Math.round(highs.getDouble(i)), low = (int) Math.round(lows.getDouble(i));
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.setContentDescription(name + " " + high + "° " + low + "°");
                col.addView(forecastText(name, 14, 0.75f, R.font.inter_500));
                ImageView icon = new ImageView(this);
                icon.setImageResource(skyIcon(codes.getInt(i)));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Math.round(30 * dp), Math.round(30 * dp));
                ip.setMargins(0, Math.round(12 * dp), 0, Math.round(12 * dp));
                col.addView(icon, ip);
                col.addView(forecastText(high + "°", 22, 1f, R.font.inter_300));
                col.addView(forecastText(low + "°", 16, 0.65f, R.font.inter_300));
                forecast.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            refreshPanel();
        } catch (Exception ignored) {
            // A malformed answer: the forecast keeps what it showed.
        }
    }

    private TextView forecastText(String text, int size, float alpha, int font) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setAlpha(alpha);
        t.setTypeface(getResources().getFont(font));
        t.setFontFeatureSettings("tnum");
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, Math.round(2 * dp));
        return t;
    }

    private static int skyIcon(int code) {
        switch (Sky.icon(code)) {
            case Sky.ICON_CLEAR: return R.drawable.ic_sun;
            case Sky.ICON_RAIN: return R.drawable.ic_rain;
            case Sky.ICON_STORM: return R.drawable.ic_storm;
            default: return R.drawable.ic_cloud;
        }
    }

    private void loadHouse() {
        io.execute(() -> {
            JSONObject cfg = config();
            String base = cfg.optString("url"), token = cfg.optString("token");
            // The room list may come on its own (sensors.json, no token) so it can change without resending the token.
            JSONArray sensors = rooms();
            if (sensors == null) sensors = cfg.optJSONArray("sensors");
            if (base.isEmpty() || token.isEmpty() || sensors == null) {
                // No Home Assistant set up: no grid, and the clock goes back to the middle.
                main.post(() -> showRooms(new ArrayList<>()));
                return;
            }
            List<String[]> items = new ArrayList<>();
            for (int i = 0; i < sensors.length(); i++) {
                JSONObject s = sensors.optJSONObject(i);
                if (s == null) continue;
                try {
                    String state = new JSONObject(get(base + "/api/states/" + s.getString("entity"), token)).optString("state");
                    String deg = Sky.degrees(state);
                    if (deg == null) continue;
                    items.add(new String[]{s.optString("label"), deg, s.optString("icon")});
                } catch (Exception ignored) {
                    // One unreachable sensor drops out of the line; the others still show.
                }
            }
            // Set up but nothing answered (HA down, network blip): keep whatever shows, so the layout does not jump.
            if (!items.isEmpty()) main.post(() -> showRooms(items));
        });
    }

    private static int roomIcon(String name) {
        switch (name) {
            case "sofa": return R.drawable.room_sofa;
            case "pot": return R.drawable.room_pot;
            case "desk": return R.drawable.room_desk;
            case "bed": return R.drawable.room_bed;
            case "star": return R.drawable.room_star;
            case "bath": return R.drawable.room_bath;
            case "shower": return R.drawable.room_shower;
            case "balcony": return R.drawable.room_balcony;
            case "washer": return R.drawable.room_washer;
            default: return R.drawable.ic_home;
        }
    }

    /** One cell per room: a dim line icon, the temperature in white, the room name small underneath. */
    /** Clock block on the left beside the room grid, or centred on its own when there is no grid. */
    private void placeClock(boolean beside) {
        findViewById(R.id.split).setVisibility(beside ? View.VISIBLE : View.GONE);
        LinearLayout block = findViewById(R.id.center);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) block.getLayoutParams();
        lp.gravity = Gravity.TOP | (beside ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        lp.setMarginStart(beside ? Math.round(62 * dp) : 0);
        lp.topMargin = Math.round((beside ? 87 : 72) * dp);
        block.setLayoutParams(lp);
        block.setGravity(beside ? Gravity.START : Gravity.CENTER_HORIZONTAL);
    }

    private void showRooms(List<String[]> items) {
        // The rooms lead whenever they first appear, even if the weather answered before them.
        if (house.getChildCount() == 0) card = CARD_HOUSE;
        house.removeAllViews();
        int lastRow = (items.size() - 1) / 3;
        for (int i = 0; i < items.size(); i++) {
            String[] r = items.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = Math.round(125 * dp);
            // No gap under the last row, so the grid's centre is the centre of what shows.
            lp.setMargins(0, 0, 0, i / 3 == lastRow ? 0 : Math.round(16 * dp));
            cell.setLayoutParams(lp);

            ImageView icon = new ImageView(this);
            icon.setImageResource(roomIcon(r[2]));
            icon.setAlpha(0.7f);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int s = Math.round(22 * dp);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
            ip.setMarginEnd(Math.round(10 * dp));
            cell.addView(icon, ip);

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView temp = new TextView(this);
            temp.setText(r[1]);
            temp.setTextColor(Color.WHITE);
            temp.setTextSize(22);
            temp.setTypeface(getResources().getFont(R.font.inter_300));
            temp.setFontFeatureSettings("tnum");
            temp.setIncludeFontPadding(false);
            TextView name = new TextView(this);
            name.setText(r[0]);
            name.setTextColor(Color.argb(184, 255, 255, 255));
            name.setTextSize(12);
            name.setTypeface(getResources().getFont(R.font.inter_300));
            name.setPadding(0, Math.round(3 * dp), 0, 0);
            name.setSingleLine(true);
            text.addView(temp);
            text.addView(name);
            cell.addView(text);
            cell.setContentDescription(r[0] + " " + r[1]);
            house.addView(cell);
        }
        refreshPanel();
    }

    private static String get(String url, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
