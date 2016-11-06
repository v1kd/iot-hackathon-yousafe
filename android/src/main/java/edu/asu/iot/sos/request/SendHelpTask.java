package edu.asu.iot.sos.request;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import edu.asu.iot.sos.MainActivity;
import edu.asu.iot.sos.StreamUtil;

/**
 * Created by Vikranth.
 */

public class SendHelpTask extends AsyncTask<String, Void, String> {

    private static final String TAG = SendHelpTask.class.getSimpleName();
    private MainActivity context;

    public SendHelpTask(MainActivity context) {
        this.context = context;
    }

    @Override
    protected String doInBackground(String... urls) {

        String url = urls[0];
        String body = "";
        Log.d(TAG, "Making request for this url: " + url);

        try {
            HttpRequest request = HttpRequest.get(url);
            request.trustAllCerts();
            request.trustAllHosts();
            body = request.body();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return body;
    }

    @Override
    protected void onPostExecute(String s) {
        context.onRequestComplete(s);
    }
}
