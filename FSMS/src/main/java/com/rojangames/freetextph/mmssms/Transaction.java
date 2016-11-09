package com.rojangames.freetextph.mmssms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.google.android.mms.ContentType;
import com.google.android.mms.MMSPart;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.smil.SmilHelper;

import com.rojangames.freetextph.common.JSONParser;
import com.rojangames.freetextph.common.JSONParserOLD;
import com.rojangames.freetextph.common.QKPreferences;
import com.rojangames.freetextph.enums.QKPreference;

/**
 * Class to process transaction requests for sending
 */
public class Transaction {
    public static final String MMS_ERROR = "com.rojangames.freetextph.send_message.MMS_ERROR";
    public static final String REFRESH = "com.rojangames.freetextph.send_message.REFRESH";
    public static final String MMS_PROGRESS = "com.rojangames.freetextph.send_message.MMS_PROGRESS";
    public static final String VOICE_FAILED = "com.rojangames.freetextph.send_message.VOICE_FAILED";
    public static final String VOICE_TOKEN = "com.rojangames.freetextph.send_message.RNRSE";
    public static final String NOTIFY_OF_DELIVERY = "com.rojangames.freetextph.send_message.NOTIFY_DELIVERY";
    public static final String NOTIFY_OF_MMS = "com.rojangames.freetextph.messaging.NEW_MMS_DOWNLOADED";
    public static final long NO_THREAD_ID = 0;
    public static final int NUM_RETRIES = 2;
    private static final String TAG = "Transaction";
    private static final boolean LOCAL_LOGV = false;
    public static Settings settings;
    public static String NOTIFY_SMS_FAILURE = ".NOTIFY_SMS_FAILURE";
    public String SMS_SENT = ".SMS_SENT";
    public String SMS_DELIVERED = ".SMS_DELIVERED";


    JSONParser jsonParser = new JSONParser();
    JSONParserOLD jsonParserOLD = new JSONParserOLD();


    private Context context;
    private ConnectivityManager mConnMgr;
    private boolean saveMessage = true;
    private boolean alreadySending = false;

    /**
     * Sets context and initializes settings to default values
     *
     * @param context is the context of the activity or service
     */
    public Transaction(Context context) {
        this(context, new Settings());
    }

    /**
     * Sets context and settings
     *
     * @param context  is the context of the activity or service
     * @param settings is the settings object to process send requests through
     */
    public Transaction(Context context, Settings settings) {
        this.settings = settings;
        this.context = context;
        SMS_SENT = context.getPackageName() + SMS_SENT;
        SMS_DELIVERED = context.getPackageName() + SMS_DELIVERED;

        if (NOTIFY_SMS_FAILURE.equals(".NOTIFY_SMS_FAILURE")) {
            NOTIFY_SMS_FAILURE = context.getPackageName() + NOTIFY_SMS_FAILURE;
        }
    }

    public static MessageInfo getBytes(Context context, boolean saveMessage, String[] recipients, MMSPart[] parts, String subject)
            throws MmsException {
        final SendReq sendRequest = new SendReq();

        // create send request addresses
        for (int i = 0; i < recipients.length; i++) {
            final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);

            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }

        if (subject != null) {
            sendRequest.setSubject(new EncodedStringValue(subject));
        }

        sendRequest.setDate(Calendar.getInstance().getTimeInMillis() / 1000L);

        try {
            sendRequest.setFrom(new EncodedStringValue(Utils.getMyPhoneNumber(context)));
        } catch (Exception e) {
            // my number is nothing
        }

        final PduBody pduBody = new PduBody();

        // assign parts to the pdu body which contains sending data
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                MMSPart part = parts[i];
                if (part != null) {
                    try {
                        PduPart partPdu = new PduPart();
                        partPdu.setName(part.Name.getBytes());
                        partPdu.setContentType(part.MimeType.getBytes());

                        if (part.MimeType.startsWith("text")) {
                            partPdu.setCharset(CharacterSets.UTF_8);
                        }

                        partPdu.setData(part.Data);

                        pduBody.addPart(partPdu);
                    } catch (Exception e) {

                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        pduBody.addPart(0, smilPart);

        sendRequest.setBody(pduBody);

        // create byte array which will actually be sent
        final PduComposer composer = new PduComposer(context, sendRequest);
        final byte[] bytesToSend;

        try {
            bytesToSend = composer.make();
        } catch (OutOfMemoryError e) {
            throw new MmsException("Out of memory!");
        }

        MessageInfo info = new MessageInfo();
        info.bytes = bytesToSend;

        if (saveMessage) {
            try {
                PduPersister persister = PduPersister.getPduPersister(context);
                info.location = persister.persist(sendRequest, Uri.parse("content://mms/outbox"), true, settings.getGroup(), null);
            } catch (Exception e) {
                if (LOCAL_LOGV) Log.v(TAG, "error saving mms message");
                Log.e(TAG, "exception thrown", e);

                // use the old way if something goes wrong with the persister
                insert(context, recipients, parts, subject);
            }
        }

        try {
            Cursor query = context.getContentResolver().query(info.location, new String[]{"thread_id"}, null, null, null);
            if (query != null && query.moveToFirst()) {
                info.token = query.getLong(query.getColumnIndex("thread_id"));
            } else {
                // just default sending token for what I had before
                info.token = 4444L;
            }
        } catch (Exception e) {
            Log.e(TAG, "exception thrown", e);
            info.token = 4444L;
        }

        return info;
    }

    private static Uri insert(Context context, String[] to, MMSPart[] parts, String subject) {
        try {
            Uri destUri = Uri.parse("content://mms");

            Set<String> recipients = new HashSet<>();
            recipients.addAll(Arrays.asList(to));
            long thread_id = Utils.getOrCreateThreadId(context, recipients);

            // Create a dummy sms
            ContentValues dummyValues = new ContentValues();
            dummyValues.put("thread_id", thread_id);
            dummyValues.put("body", " ");
            Uri dummySms = context.getContentResolver().insert(Uri.parse("content://sms/sent"), dummyValues);

            // Create a new message entry
            long now = System.currentTimeMillis();
            ContentValues mmsValues = new ContentValues();
            mmsValues.put("thread_id", thread_id);
            mmsValues.put("date", now / 1000L);
            mmsValues.put("msg_box", 4);
            //mmsValues.put("m_id", System.currentTimeMillis());
            mmsValues.put("read", true);
            mmsValues.put("sub", subject != null ? subject : "");
            mmsValues.put("sub_cs", 106);
            mmsValues.put("ct_t", "application/vnd.wap.multipart.related");

            long imageBytes = 0;

            for (MMSPart part : parts) {
                imageBytes += part.Data.length;
            }

            mmsValues.put("exp", imageBytes);

            mmsValues.put("m_cls", "personal");
            mmsValues.put("m_type", 128); // 132 (RETRIEVE CONF) 130 (NOTIF IND) 128 (SEND REQ)
            mmsValues.put("v", 19);
            mmsValues.put("pri", 129);
            mmsValues.put("tr_id", "T" + Long.toHexString(now));
            mmsValues.put("resp_st", 128);

            // Insert message
            Uri res = context.getContentResolver().insert(destUri, mmsValues);
            String messageId = res.getLastPathSegment().trim();

            // Create part
            for (MMSPart part : parts) {
                if (part.MimeType.startsWith("image")) {
                    createPartImage(context, messageId, part.Data, part.MimeType);
                } else if (part.MimeType.startsWith("text")) {
                    createPartText(context, messageId, new String(part.Data, "UTF-8"));
                }
            }

            // Create addresses
            for (String addr : to) {
                createAddr(context, messageId, addr);
            }

            //res = Uri.parse(destUri + "/" + messageId);

            // Delete dummy sms
            context.getContentResolver().delete(dummySms, null, null);

            return res;
        } catch (Exception e) {
            if (LOCAL_LOGV) Log.v(TAG, "still an error saving... :(");
            Log.e(TAG, "exception thrown", e);
        }

        return null;
    }

    // create the image part to be stored in database
    private static Uri createPartImage(Context context, String id, byte[] imageBytes, String mimeType) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", mimeType);
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        // Add data to part
        OutputStream os = context.getContentResolver().openOutputStream(res);
        ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
        byte[] buffer = new byte[256];

        for (int len = 0; (len = is.read(buffer)) != -1; ) {
            os.write(buffer, 0, len);
        }

        os.close();
        is.close();

        return res;
    }

    // create the text part to be stored in database
    private static Uri createPartText(Context context, String id, String text) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", "text/plain");
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        mmsPartValue.put("text", text);
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        return res;
    }

    // add address to the request
    private static Uri createAddr(Context context, String id, String addr) throws Exception {
        ContentValues addrValues = new ContentValues();
        addrValues.put("address", addr);
        addrValues.put("charset", "106");
        addrValues.put("type", 151); // TO
        Uri addrUri = Uri.parse("content://mms/" + id + "/addr");
        Uri res = context.getContentResolver().insert(addrUri, addrValues);

        return res;
    }

    /**
     * Called to send a new message depending on settings and provided Message object
     * If you want to send message as mms, call this from the UI thread
     *
     * @param message  is the message that you want to send
     * @param threadId is the thread id of who to send the message to (can also be set to Transaction.NO_THREAD_ID)
     */
    public void sendNewMessage(Message message, long threadId) {
        this.saveMessage = message.getSave();

        if (message.getType() == Message.TYPE_SMSMMS) {
            if (LOCAL_LOGV) Log.v(TAG, "sending sms");
            sendSmsMessage(message.getText(), message.getAddresses(), threadId, message.getDelay());
        } else if (message.getType() == Message.TYPE_HTTP) {
            sendViaHttp(message.getText(), message.getAddresses(), threadId);
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "error with message type, aborting...");
        }

    }


    private void sendViaHttp(String text, String[] addresses, long threadId) {
        Uri messageUri = null;
        int messageId = 0;
        // save the message for each of the addresses
        for (int i = 0; i < addresses.length; i++) {
            Calendar cal = Calendar.getInstance();
            ContentValues values = new ContentValues();
            values.put("address", addresses[i]);
            values.put("body", settings.getStripUnicode() ? StripAccents.stripAccents(text) : text);
            values.put("date", cal.getTimeInMillis() + "");
            values.put("date_sent", cal.getTimeInMillis() + "");
            values.put("read", 1);
            values.put("type", 4); //sending
            values.put("status", 0);

            // attempt to create correct thread id if one is not supplied
            if (threadId == NO_THREAD_ID || addresses.length > 1) {
                threadId = Utils.getOrCreateThreadId(context, addresses[i]);
            }

            values.put("thread_id", threadId);
            messageUri = context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);

            Params params = new Params(text, addresses[i], messageUri);
            new HttpSmsTask().execute(params);
        }
    }

    String reformatNumber(String number) {
        try {
            if (number.startsWith("09")) {
                number = number.substring(1, 11).trim();
                number = "+63" + number;
            } else if (number.startsWith("639")) {
                number = number.replaceAll("[^0-9+]", "").trim();
                number = "+" + number.replaceAll("[^0-9+]", "");
            } else if (number.startsWith("08")) {
                number = number.substring(1, 11).trim();
                number = "+63" + number;
            } else if (number.startsWith("638")) {
                number = number.replaceAll("[^0-9+]", "").trim();
                number = "+" + number.replaceAll("[^0-9+]", "");
            }
        } catch (Exception x) {
            Toast.makeText(context, "ERROR Detected: Make sure to use PH format numbers only", Toast.LENGTH_LONG).show();
        }
        return number;
    }

    private void sendSmsMessage(String text, String[] addresses, long threadId, int delay) {
        if (LOCAL_LOGV) Log.v(TAG, "message text: " + text);
        Uri messageUri = null;
        int messageId = 0;
        if (saveMessage) {
            if (LOCAL_LOGV) Log.v(TAG, "saving message");
            // add signature to original text to be saved in database (does not strip unicode for saving though)
            if (!settings.getSignature().equals("")) {
                text += "\n" + settings.getSignature();
            }

            // save the message for each of the addresses
            for (int i = 0; i < addresses.length; i++) {
                Calendar cal = Calendar.getInstance();
                ContentValues values = new ContentValues();
                values.put("address", addresses[i]);
                values.put("body", settings.getStripUnicode() ? StripAccents.stripAccents(text) : text);
                values.put("date", cal.getTimeInMillis() + "");
                values.put("read", 1);
                values.put("type", 4);

                // attempt to create correct thread id if one is not supplied
                if (threadId == NO_THREAD_ID || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(context, addresses[i]);
                }

                if (LOCAL_LOGV) Log.v(TAG, "saving message with thread id: " + threadId);

                values.put("thread_id", threadId);
                messageUri = context.getContentResolver().insert(Uri.parse("content://sms/"), values);

                if (LOCAL_LOGV) Log.v(TAG, "inserted to uri: " + messageUri);

                Cursor query = context.getContentResolver().query(messageUri, new String[]{"_id"}, null, null, null);
                if (query != null && query.moveToFirst()) {
                    messageId = query.getInt(0);
                }

                if (LOCAL_LOGV) Log.v(TAG, "message id: " + messageId);

                // set up sent and delivered pending intents to be used with message request
                PendingIntent sentPI = PendingIntent.getBroadcast(context, messageId, new Intent(SMS_SENT)
                        .putExtra("message_uri", messageUri == null ? "" : messageUri.toString()), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context, messageId, new Intent(SMS_DELIVERED)
                        .putExtra("message_uri", messageUri == null ? "" : messageUri.toString()), PendingIntent.FLAG_UPDATE_CURRENT);

                ArrayList<PendingIntent> sPI = new ArrayList<>();
                ArrayList<PendingIntent> dPI = new ArrayList<>();

                String body = text;

                // edit the body of the text if unicode needs to be stripped
                if (settings.getStripUnicode()) {
                    body = StripAccents.stripAccents(body);
                }

                if (!settings.getPreText().equals("")) {
                    body = settings.getPreText() + " " + body;
                }

                SmsManager smsManager = SmsManager.getDefault();
                if (LOCAL_LOGV) Log.v(TAG, "found sms manager");

                if (QKPreferences.getBoolean(QKPreference.SPLIT_SMS)){
                    if (LOCAL_LOGV) Log.v(TAG, "splitting message");
                    // figure out the length of supported message
                    int[] splitData = SmsMessage.calculateLength(body, false);

                    // we take the current length + the remaining length to get the total number of characters
                    // that message set can support, and then divide by the number of message that will require
                    // to get the length supported by a single message
                    int length = (body.length() + splitData[2]) / splitData[0];
                    if (LOCAL_LOGV) Log.v(TAG, "length: " + length);

                    boolean counter = false;
                    if (settings.getSplitCounter() && body.length() > length) {
                        counter = true;
                        length -= 6;
                    }

                    // get the split messages
                    String[] textToSend = splitByLength(body, length, counter);

                    // send each message part to each recipient attached to message
                    for (int j = 0; j < textToSend.length; j++) {
                        ArrayList<String> parts = smsManager.divideMessage(textToSend[j]);

                        for (int k = 0; k < parts.size(); k++) {
                            sPI.add(saveMessage ? sentPI : null);
                            dPI.add(settings.getDeliveryReports() && saveMessage ? deliveredPI : null);
                        }

                        if (LOCAL_LOGV) Log.v(TAG, "sending split message");
                        sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                    }
                } else {
                    if (LOCAL_LOGV) Log.v(TAG, "sending without splitting");
                    // send the message normally without forcing anything to be split
                    ArrayList<String> parts = smsManager.divideMessage(body);

                    for (int j = 0; j < parts.size(); j++) {
                        sPI.add(saveMessage ? sentPI : null);
                        dPI.add(settings.getDeliveryReports() && saveMessage ? deliveredPI : null);
                    }

                    try {
                        if (LOCAL_LOGV) Log.v(TAG, "sent message");
                        sendDelayedSms(smsManager, addresses[i], parts, sPI, dPI, delay, messageUri);
                    } catch (Exception e) {
                        // whoops...
                        if (LOCAL_LOGV) Log.v(TAG, "error sending message");
                        Log.e(TAG, "exception thrown", e);

                        try {
                            ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                @Override
                                public void run() {
                                    Toast.makeText(context, "Message could not be sent", Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (Exception f) {
                        }
                    }
                }
            }
        }
    }

    String getIME() {
        TelephonyManager mngr = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        return mngr.getDeviceId();
    }

    private void sendDelayedSms(final SmsManager smsManager, final String address,
                                final ArrayList<String> parts, final ArrayList<PendingIntent> sPI,
                                final ArrayList<PendingIntent> dPI, final int delay, final Uri messageUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (Exception e) {
                }

                if (checkIfMessageExistsAfterDelay(messageUri)) {
                    if (LOCAL_LOGV) Log.v(TAG, "message sent after delay");
                    try {
                        smsManager.sendMultipartTextMessage(address, null, parts, sPI, dPI);
                    } catch (Exception e) {
                        Log.e(TAG, "exception thrown", e);
                    }
                } else {
                    if (LOCAL_LOGV)
                        Log.v(TAG, "message not sent after delay, no longer exists");
                }
            }
        }).start();
    }

    private boolean checkIfMessageExistsAfterDelay(Uri messageUti) {
        Cursor query = context.getContentResolver().query(messageUti, new String[]{"_id"}, null, null, null);
        return query != null && query.moveToFirst();
    }

    // splits text and adds split counter when applicable
    private String[] splitByLength(String s, int chunkSize, boolean counter) {
        int arraySize = (int) Math.ceil((double) s.length() / chunkSize);

        String[] returnArray = new String[arraySize];

        int index = 0;
        for (int i = 0; i < s.length(); i = i + chunkSize) {
            if (s.length() - i < chunkSize) {
                returnArray[index++] = s.substring(i);
            } else {
                returnArray[index++] = s.substring(i, i + chunkSize);
            }
        }

        if (counter && returnArray.length > 1) {
            for (int i = 0; i < returnArray.length; i++) {
                returnArray[i] = "(" + (i + 1) + "/" + returnArray.length + ") " + returnArray[i];
            }
        }

        return returnArray;
    }

    public static class MessageInfo {
        public long token;
        public Uri location;
        public byte[] bytes;
    }

    class Params {
        private String _text;
        private String _address;
        private Uri _messageUri;

        Params(String text, String address, Uri messageUri) {
            this._text = text;
            this._address = address;
            this._messageUri = messageUri;

        }
    }

    class HttpSmsTask extends AsyncTask<Params, String, String> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Params... params) {
            
            /*
            
             Do something here to forward messages to server
            */

            return null;
        }

        protected void onPostExecute(String message) {

            //Do something after sending
        }
    }
}
