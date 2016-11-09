package com.rojangames.freetextph.service;

import android.app.IntentService;
import android.content.Intent;
import com.rojangames.freetextph.transaction.NotificationManager;
import com.rojangames.freetextph.transaction.SmsHelper;

public class MarkSeenService extends IntentService {
    private final String TAG = "MarkSeenService";

    public MarkSeenService() {
        super("MarkSeenService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SmsHelper.markSmsSeen(this);
        SmsHelper.markMmsSeen(this);
        NotificationManager.update(this);
    }
}
