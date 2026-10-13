package ru.vasys.keycursor.service;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import ru.vasys.keycursor.ui.MainActivity;

/**
 * Quick Settings tile that shows or hides the touchpad. The tile is ACTIVE while the pad is up.
 * If the accessibility service is not running, tapping the tile opens KeyCursor so it can be
 * enabled.
 */
public final class PadTileService extends TileService {

    /** Asks the system to re-run {@link #onStartListening()} so the tile reflects the pad state. */
    public static void requestRefresh(Context context) {
        try {
            TileService.requestListeningState(context.getApplicationContext(),
                    new ComponentName(context.getApplicationContext(), PadTileService.class));
        } catch (RuntimeException ignored) {
            // The tile may not be added by the user; nothing to refresh then.
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        if (service == null) {
            openApp();
            return;
        }
        // The service refreshes the tile itself once the pad has actually changed state.
        service.togglePadFromTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        MouseAccessibilityService service = MouseAccessibilityService.getRunningInstance();
        tile.setState(service != null && service.isPadVisible() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
