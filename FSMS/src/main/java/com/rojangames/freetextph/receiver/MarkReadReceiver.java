package com.rojangames.freetextph.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.rojangames.freetextph.service.MarkReadService;
import com.rojangames.freetextph.ui.popup.QKReplyActivity;

public class MarkReadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        long threadId = extras.getLong("thread_id");

        Intent readIntent = new Intent(context, MarkReadService.class);
        readIntent.putExtra("thread_id", threadId);
        context.startService(readIntent);

        QKReplyActivity.dismiss(threadId);
    }
}
