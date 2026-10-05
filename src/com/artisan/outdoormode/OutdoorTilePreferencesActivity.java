package com.artisan.outdoormode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Launched when the Outdoor mode tile is long pressed.
 *
 * A tile has no long-press callback: the system looks for an activity of the tile's package
 * that handles {@code ACTION_QS_TILE_PREFERENCES} and shows the app info screen when there
 * isn't one. This activity claims that action and forwards straight to Settings -> Display,
 * so a long press lands on the full Outdoor mode screen.
 *
 * It uses a no-display theme and finishes immediately, so it is only a trampoline.
 */
public class OutdoorTilePreferencesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Throwable ignored) {
            // Nothing else to try; finishing leaves the user on the previous screen.
        }

        finish();
    }
}
