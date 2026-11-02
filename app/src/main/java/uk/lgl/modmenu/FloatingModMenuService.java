package uk.lgl.modmenu;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import uk.lgl.R;

/** Native Android implementation of the approved white MinhQuan Mod menu. */
public class FloatingModMenuService extends Service {
    public static final String TAG = "Mod_Menu";
    private static final int INK = Color.rgb(35, 54, 59);
    private static final int ACCENT = Color.rgb(20, 131, 115);
    private static final android.util.SparseBooleanArray menuStates = new android.util.SparseBooleanArray();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.List<ValueAnimator> decorations = new java.util.ArrayList<ValueAnimator>();
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private FrameLayout root;
    private LinearLayout panel;
    private ImageView bubble;
    private ImageView logo;
    private TextView title;
    private boolean motionPaused;
    private boolean destroyed;
    private boolean closing;

    // MQUAN_DIAGNOSTIC_V2: diagnostics for this app only, not a game connection.
    private TextView featureStatus;
    private static final String GAME_DISCONNECTED = "Game: Chưa kết nối";

    private void reportFeatureDiagnostic(int id, String name, boolean requested) {
        final int expected = (id << 1) | (requested ? 1 : 0);
        try {
            final int received = Preferences.diagnosticEcho(id, requested);
            if (received != expected) {
                String message = "JNI phản hồi sai • " + name;
                if (featureStatus != null) featureStatus.setText(message);
                android.util.Log.e(TAG, "FEATURE_DIAG bad echo id=" + id
                        + " expected=" + expected + " received=" + received);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            String message = name + ": Đã nhận lệnh • Chưa thể thực thi";
            if (featureStatus != null) featureStatus.setText(message);
            android.util.Log.i(TAG, "FEATURE_DIAG id=" + id + " requested=" + requested
                    + " jni=OK game=DISCONNECTED applied=false");
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            String message = "JNI lỗi • " + name;
            if (featureStatus != null) featureStatus.setText(message);
            android.util.Log.e(TAG, "FEATURE_DIAG failed id=" + id, error);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }


    // Keep the native interface compatible. UI construction does not call the
    // legacy native feature list, which contained an advert and startup toast.
    native void setTitleText(TextView view);
    native void setHeadingText(TextView view);
    native String Icon();
    native String IconWebViewData();
    native String[] getFeatureList();
    native String[] settingsList();
    native boolean isGameLibLoaded();

    static { System.loadLibrary("MyLibName"); }

    private final Runnable visibilityCheck = new Runnable() {
        @Override public void run() {
            if (destroyed || root == null) return;
            // Preserve existing background-visibility behaviour in this UI-only update.
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(FloatingModMenuService.this)) {
              stopSelf();
              return;
            }
            root.setVisibility(View.VISIBLE);
            updateMotion();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        Preferences.context = this;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        buildMenu();
        handler.post(visibilityCheck);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable surface(int color, float radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private ImageView image(int resource, int size) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return view;
    }

    private void buildMenu() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = new FrameLayout(this);
        root.setPadding(dp(5), dp(5), dp(5), dp(5));
        int menuWidth = Math.min(dp(260), getResources().getDisplayMetrics().widthPixels - dp(24));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(9), 0, dp(9), dp(6));
        panel.setBackground(surface(Color.WHITE, 26, Color.rgb(229, 235, 234)));
        panel.setElevation(dp(10));
        root.addView(panel, new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(5), dp(8), dp(5), dp(8));
        logo = image(R.drawable.mq_logo, 38);
        logo.setPadding(dp(2), dp(2), dp(2), dp(2));
        logo.setBackground(surface(Color.rgb(243, 250, 247), 19, Color.rgb(226, 238, 234)));
        logo.setElevation(dp(2));
        logo.setContentDescription("Tạm dừng hoạt ảnh");
        logo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                motionPaused = !motionPaused;
                logo.setContentDescription(motionPaused ? "Tiếp tục hoạt ảnh" : "Tạm dừng hoạt ảnh");
                updateMotion();
            }
        });
        header.addView(logo);
        title = new TextView(this);
        title.setText("MinhQuan Mod");
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setTextColor(INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(-0.025f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        titleParams.leftMargin = dp(12);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, titleParams);
        header.setOnTouchListener(dragListener(false));
        title.setOnTouchListener(dragListener(false));
        panel.addView(header);
        // Native bridge diagnostics, not a claim of connection to the game.
        TextView bridgeStatus = new TextView(this);
        bridgeStatus.setTextSize(11);
        bridgeStatus.setTextColor(Color.rgb(145, 87, 36));
        bridgeStatus.setPadding(dp(7), 0, dp(7), dp(6));
        try {
            int echo = Preferences.diagnosticEcho(3, true);
            bridgeStatus.setText(echo == 7
                    ? "JNI: OK  |  Game: Chưa kết nối"
                    : "JNI: Phản hồi sai  |  Game: Chưa kết nối");
        } catch (Throwable e) {
            bridgeStatus.setText("JNI: Lỗi  |  Game: Chưa kết nối");
            android.util.Log.e(TAG, "JNI diagnostic failed", e);
        }
        panel.addView(bridgeStatus);
        featureStatus = new TextView(this);
        featureStatus.setText("Tính năng: Chưa thực thi • " + GAME_DISCONNECTED);
        featureStatus.setTextSize(10);
        featureStatus.setTextColor(Color.rgb(113, 103, 94));
        featureStatus.setPadding(dp(7), 0, dp(7), dp(5));
        featureStatus.setMaxLines(2);
        panel.addView(featureStatus);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        // Feature IDs and dispatch are unchanged; a switch is not runtime verification.
        rows.addView(feature(1, "Hiện bản đồ", R.drawable.mq_map));
        rows.addView(feature(3, "Góc nhìn cao", R.drawable.mq_camera));
        rows.addView(feature(2, "Thông tin tướng", R.drawable.mq_hero));
        rows.addView(feature(6, "Lịch sử đấu", R.drawable.mq_history));
        scroll.addView(rows);
        int available = getResources().getDisplayMetrics().heightPixels - dp(210);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(180), Math.max(dp(90), available))));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(233, 238, 238));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(5);
        panel.addView(divider, dividerParams);
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(action("Ẩn", true), new LinearLayout.LayoutParams(0, dp(36), 1f));
        footer.addView(action("Thu gọn", false), new LinearLayout.LayoutParams(0, dp(36), 1f));
        panel.addView(footer);

        bubble = new ImageView(this);
        bubble.setImageResource(R.drawable.mq_logo);
        bubble.setPadding(dp(5), dp(5), dp(5), dp(5));
        bubble.setBackground(surface(Color.WHITE, 23, Color.rgb(224, 234, 230)));
        bubble.setElevation(dp(8));
        bubble.setContentDescription("Mở menu MinhQuan Mod");
        bubble.setVisibility(View.GONE);
        bubble.setOnTouchListener(dragListener(true));
        bubble.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { expand(); }
        });
        root.addView(bubble, new FrameLayout.LayoutParams(dp(48), dp(48)));

        params = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dp(70);
        windowManager.addView(root, params);
        title.post(new Runnable() {
            @Override public void run() { if (!destroyed) setupMotion(); }
        });
    }

    private View feature(final int id, final String name, int iconResource) {
        final LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.setBackground(surface(Color.rgb(246, 248, 248), 15, Color.rgb(238, 241, 240)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rowParams.bottomMargin = dp(5);
        row.setLayoutParams(rowParams);
        final ImageView icon = image(iconResource, 28);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(surface(Color.WHITE, 11, 0));
        icon.setElevation(dp(1));
        row.addView(icon);
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(INK);
        label.setTextSize(12);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(10);
        row.addView(label, labelParams);
        final Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
        toggle.setContentDescription(name);
        toggle.setButtonTintList(ColorStateList.valueOf(ACCENT));
        toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        toggle.setTrackTintList(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ACCENT, Color.rgb(205, 217, 214)}));
        // Match native defaults in a fresh process and retain choices if this
        // service is reopened in the same process. No saved state is auto-applied.
        toggle.setChecked(menuStates.get(id, false));
        if (toggle.isChecked()) row.setBackground(surface(Color.rgb(237, 248, 244), 15, Color.rgb(211, 235, 226)));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(48)));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                // Diagnostic only: do not call MemoryPatch against another app.
                reportFeatureDiagnostic(id, name, checked);
                menuStates.put(id, checked);
                row.setBackground(surface(checked ? Color.rgb(237, 248, 244) : Color.rgb(246, 248, 248),
                        15, checked ? Color.rgb(211, 235, 226) : Color.rgb(238, 241, 240)));
                if (motionAllowed()) {
                    icon.animate().cancel();
                    icon.setScaleX(.93f);
                    icon.setScaleY(.93f);
                    icon.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                }
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggle.setChecked(!toggle.isChecked()); }
        });
        return row;
    }

    private Button action(String name, final boolean dim) {
        Button button = new Button(this);
        button.setText(name);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(dim ? Color.rgb(99, 119, 125) : ACCENT);
        button.setBackground(surface(Color.WHITE, 11, 0));
        button.setStateListAnimator(null);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { collapse(dim); }
        });
        return button;
    }

    private boolean motionAllowed() {
        if (motionPaused) return false;
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    private void setupMotion() {
        ObjectAnimator floating = ObjectAnimator.ofFloat(logo, "translationY", 0f, -dp(2), 0f);
        floating.setDuration(6000);
        floating.setRepeatCount(ValueAnimator.INFINITE);
        decorations.add(floating);
        final float width = Math.max(title.getWidth(), dp(170));
        final LinearGradient sheen = new LinearGradient(0, 0, width, 0,
                new int[] {INK, INK, ACCENT, INK, INK},
                new float[] {0f, .35f, .5f, .65f, 1f}, Shader.TileMode.CLAMP);
        final Matrix matrix = new Matrix();
        title.getPaint().setShader(sheen);
        ValueAnimator light = ValueAnimator.ofFloat(-width, width * 2f);
        light.setDuration(6500);
        light.setRepeatCount(ValueAnimator.INFINITE);
        light.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                matrix.setTranslate((Float) animation.getAnimatedValue(), 0);
                sheen.setLocalMatrix(matrix);
                title.invalidate();
            }
        });
        decorations.add(light);
        updateMotion();
    }

    private void updateMotion() {
        boolean run = !destroyed && motionAllowed() && root != null
                && root.getVisibility() == View.VISIBLE && panel.getVisibility() == View.VISIBLE;
        for (ValueAnimator animation : decorations) {
            if (run && !animation.isStarted()) animation.start();
            else if (run && animation.isPaused()) animation.resume();
            else if (!run && animation.isStarted() && !animation.isPaused()) animation.pause();
        }
    }

    private void collapse(final boolean dim) {
        if (closing) return;
        closing = true;
        Runnable finish = new Runnable() {
            @Override public void run() {
                if (destroyed) return;
                panel.setVisibility(View.GONE);
                panel.setAlpha(1f);
                panel.setScaleX(1f);
                panel.setScaleY(1f);
                bubble.setAlpha(dim ? .35f : 1f);
                bubble.setVisibility(View.VISIBLE);
                closing = false;
                updateMotion();
            }
        };
        if (motionAllowed()) panel.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(160).withEndAction(finish).start();
        else finish.run();
    }

    private void expand() {
        if (closing) return;
        bubble.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        params.x = Math.min(params.x, Math.max(0, getResources().getDisplayMetrics().widthPixels
                - panel.getLayoutParams().width - root.getPaddingLeft() - root.getPaddingRight()));
        windowManager.updateViewLayout(root, params);
        if (motionAllowed()) {
            panel.setAlpha(0f);
            panel.setScaleX(.94f);
            panel.setScaleY(.94f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        updateMotion();
    }

    private View.OnTouchListener dragListener(final boolean clickable) {
        return new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (params == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = params.x; startY = params.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        int slop = ViewConfiguration.get(FloatingModMenuService.this).getScaledTouchSlop();
                        if (!moved && Math.hypot(dx, dy) < slop) return true;
                        moved = true;
                        int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - root.getWidth());
                        int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - root.getHeight() - dp(24));
                        params.x = Math.max(0, Math.min(maxX, startX + Math.round(dx)));
                        params.y = Math.max(0, Math.min(maxY, startY + Math.round(dy)));
                        windowManager.updateViewLayout(root, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved && clickable) view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return false;
                }
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onTaskRemoved(Intent intent) { super.onTaskRemoved(intent); }
    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        for (ValueAnimator animation : decorations) animation.cancel();
        decorations.clear();
        if (panel != null) panel.animate().cancel();
        if (root != null && root.isAttachedToWindow()) windowManager.removeView(root);
        root = null;
        if (Preferences.context == this) Preferences.context = getApplicationContext();
        super.onDestroy();
    }
}
