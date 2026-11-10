package local.ridewatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StopReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { AppConfig.stop(context); }
}
