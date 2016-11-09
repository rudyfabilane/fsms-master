package com.rojangames.freetextph.common;

/**
 * Created by Romel L. Emperado on 2/18/2016.
 */
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class JSONParser {

    // constructor
    public JSONParser() {

    }

    public JSONObject makeHttpRequest(String url, HashMap<String, String> params) throws JSONException {

        // Making HTTP request
        StringBuilder sbParams = new StringBuilder();
        StringBuilder result = new StringBuilder();
        String charset = "UTF-8";
        HttpURLConnection conn = null;
        JSONObject jObj = null;
        URL urlObj = null;
        DataOutputStream wr = null;

        try {

        int i = 0;
        for (String key : params.keySet()) {
            try {
                if (i != 0){
                    sbParams.append("&");
                }
                sbParams.append(key).append("=")
                        .append(URLEncoder.encode(params.get(key), charset));

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            i++;
        }

       // Log.d("HTTP Request", "params: " + sbParams.toString());


            urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept-Charset", charset);
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.connect();

            String paramsString = sbParams.toString();

            wr = new DataOutputStream(conn.getOutputStream());
            wr.writeBytes(paramsString);
            wr.flush();
            wr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            //Receive the response from the server
            InputStream in = new BufferedInputStream(conn.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

          //  Log.d("HTTP Request", "result: " + result.toString());
        } catch (IOException e) {
            Log.e("IO Error", "Error converting result " + e.toString());
        }
        conn.disconnect();
        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(result.toString());
        } catch (JSONException e) {
            Log.e("JSON Parser", "Error parsing data " + e.toString());
            return new JSONObject("{\"success\" : \"0\", \"message\" : \"parsing error\"}");
        }

        return jObj;

    }
}