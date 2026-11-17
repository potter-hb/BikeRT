package com.potteryu.bikerideguard;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class RideTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        int state = RideState.getState(this);
        if (state == RideState.RIDING || state == RideState.ENDING) {
            RideTimerService.stop(this);
        } else {
            RideState.clear(this);
            RideTimerService.start(this);
        }
        refreshTile();
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        int state = RideState.getState(this);
        boolean active = state == RideState.RIDING || state == RideState.ENDING;
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.setLabel(active ? "骑行计时中" : "50分钟骑行");
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    static void requestTileRefresh(android.content.Context context) {
        TileService.requestListeningState(
                context,
                new android.content.ComponentName(context, RideTileService.class)
        );
    }
}
