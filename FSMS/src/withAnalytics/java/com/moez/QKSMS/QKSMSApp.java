package com.text.sms;

import com.crashlytics.android.Crashlytics;

import com.rojangames.freetextph.QKSMSAppBase;
import io.fabric.sdk.android.Fabric;

public class QKSMSApp extends QKSMSAppBase {
    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
    }
}
