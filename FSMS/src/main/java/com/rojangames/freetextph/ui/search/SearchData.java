package com.rojangames.freetextph.ui.search;

import android.database.Cursor;

import com.rojangames.freetextph.data.Contact;

public class SearchData {

    protected long threadId;
    protected Contact contact;
    protected String body;
    protected long date;
    protected long rowId;

    public SearchData(Cursor cursor) {
        try {
            final int threadIdPos = cursor.getColumnIndex("thread_id");
            final int addressPos = cursor.getColumnIndex("address");
            final int bodyPos = cursor.getColumnIndex("body");
            final int datePos = cursor.getColumnIndex("date");
            final int rowidPos = cursor.getColumnIndex("_id");

            threadId = cursor.getLong(threadIdPos);

            String address = cursor.getString(addressPos);
            contact = address != null ? Contact.get(address, false) : null;

            body = cursor.getString(bodyPos);

            date = cursor.getLong(datePos);

            rowId = cursor.getLong(rowidPos);
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

}
