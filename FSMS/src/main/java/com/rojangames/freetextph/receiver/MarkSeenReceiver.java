package com.rojangames.freetextph.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.rojangames.freetextph.service.MarkSeenService;

public class MarkSeenReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent seenIntent = new Intent(context, MarkSeenService.class);
        context.startService(seenIntent);
    }
}
