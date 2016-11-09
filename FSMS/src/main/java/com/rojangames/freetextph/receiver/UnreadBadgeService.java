package com.rojangames.freetextph.receiver;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.rojangames.freetextph.transaction.SmsHelper;
import com.rojangames.freetextph.ui.widget.WidgetProvider;
import me.leolin.shortcutbadger.ShortcutBadger;

public class UnreadBadgeService extends IntentService {

    public static final String UNREAD_COUNT_UPDATED = "com.rojangames.freetextph.intent.action.UNREAD_COUNT_UPDATED";

    public UnreadBadgeService() {
        super("UnreadBadgeService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            if (UNREAD_COUNT_UPDATED.equals(intent.getAction())) {

                ShortcutBadger.with(getApplicationContext()).count(SmsHelper.getUnreadMessageCount(this));
                WidgetProvider.notifyDatasetChanged(this);
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    public static void update(Context context) {
        Intent intent = new Intent(context, UnreadBadgeService.class);
        intent.setAction(UNREAD_COUNT_UPDATED);
        context.startService(intent);
    }
}
