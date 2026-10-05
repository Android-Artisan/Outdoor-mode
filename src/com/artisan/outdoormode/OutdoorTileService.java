package com.artisan.outdoormode;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * Quick Settings tile that turns Samsung's Outdoor mode on or off.
 *
 * It flips the Settings.System key "display_outdoor_mode", the same value
 * Settings -> Display writes, so the framework applies the change on its own.
 *
 * Writing that key from an app needs two things: the "Modify system settings" access for
 * this app, and the provider hook from {@link OutdoorHook} that lifts the restriction on
 * unknown system settings. Without the hook the provider refuses the write.
 */
public class OutdoorTileService extends TileService {

    private static final String SETTING_KEY = "display_outdoor_mode";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!Settings.System.canWrite(this)) {
            // No "Modify system settings" access yet, ask for it before trying anything.
            openWriteSettingsScreen();
            return;
        }

        try {
            Settings.System.putInt(getContentResolver(), SETTING_KEY, isOutdoorModeOn() ? 0 : 1);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_SHORT).show();
        }

        updateTile();
    }

    private boolean isOutdoorModeOn() {
        return Settings.System.getInt(getContentResolver(), SETTING_KEY, 0) == 1;
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(isOutdoorModeOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_outdoor));
        tile.updateTile();
    }

    private void openWriteSettingsScreen() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
