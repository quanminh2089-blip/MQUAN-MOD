package uk.lgl;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import uk.lgl.modmenu.FloatingModMenuService;

public class MainActivity extends Activity {
    private static final String GAME_PACKAGE = "com.garena.game.kgvn";
    private final Handler handler = new Handler();
    private boolean launchScheduled;

    static { System.loadLibrary("MyLibName"); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openGame = findViewById(R.id.open_game);
        openGame.setOnClickListener(v -> launchGame());
        startMenuOrRequestOverlay();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasOverlayPermission() && !launchScheduled) {
            startMenuOrRequestOverlay();
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
    }

    private void startMenuOrRequestOverlay() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this,
                    "Hãy cấp quyền hiển thị menu trên Liên Quân",
                    Toast.LENGTH_LONG).show();

            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        try {
            startService(new Intent(this, FloatingModMenuService.class));
        } catch (RuntimeException error) {
            Toast.makeText(this,
                    "Không thể khởi động menu nổi",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!launchScheduled) {
            launchScheduled = true;
            handler.postDelayed(() -> {
                launchScheduled = false;
                launchGame();
            }, 700);
        }
    }

    private void launchGame() {
        PackageManager pm = getPackageManager();
        Intent gameIntent = pm.getLaunchIntentForPackage(GAME_PACKAGE);

        if (gameIntent == null) {
            Toast.makeText(this,
                    "Chưa tìm thấy Liên Quân Mobile. Hãy cài đủ bản XAPK trước.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        gameIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            startActivity(gameIntent);
        } catch (RuntimeException error) {
            Toast.makeText(this,
                    "Không thể mở Liên Quân Mobile",
                    Toast.LENGTH_LONG).show();
        }
    }
}
