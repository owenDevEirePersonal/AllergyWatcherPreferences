package com.deveire.dev.allergywatcherpreferences;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by owenryan on 25/04/2017.
 */

public class NetworkFragment extends Fragment
{
    public static final String TAG = "NetworkFragment";

    private static final String URL_KEY = "UrlKey";

    private DownloadCallback mCallback;
    private DownloadTask mDownloadTask;
    private String mUrlString;

    /**
     * Static initializer for NetworkFragment that sets the URL of the host it will be downloading
     * from.
     */
    public static NetworkFragment getInstance(FragmentManager fragmentManager, String url) {
        NetworkFragment networkFragment = (NetworkFragment) fragmentManager.findFragmentByTag(NetworkFragment.TAG);
        //if (networkFragment == null)
        //TODO re-implement state saving
        {
            networkFragment = new NetworkFragment();
            Bundle args = new Bundle();
            args.putString(URL_KEY, url);
            networkFragment.setArguments(args);
            fragmentManager.beginTransaction().add(networkFragment, TAG).commit();
        }
        return networkFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(mUrlString == null)
        {
            mUrlString = getArguments().getString(URL_KEY);
            Log.i("Network Update", "onCreate url is " + mUrlString);
        }
        //
        // setRetainInstance(true);
        startDownload();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Host Activity will handle callbacks from task.
        mCallback = (DownloadCallback) context;
        Log.i("Network Update", "Callback attached");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Clear reference to host Activity to avoid memory leak.
        mCallback = null;
    }

    @Override
    public void onDestroy() {
        // Cancel task when Fragment is destroyed.
        cancelDownload();
        super.onDestroy();
    }

    /**
     * Start non-blocking execution of DownloadTask.
     */
    public void startDownload() {
        cancelDownload();
        Log.i("Network Update", "started Downloading");
        mDownloadTask = new DownloadTask(mCallback);
        if(mUrlString == null)
        {
            mUrlString = getArguments().getString(URL_KEY);
            Log.i("Network Update", "onCreate has not yet run, so setting url to " + mUrlString);
        }
        Log.i("Network Update", "startdownload url is " + mUrlString);
        mDownloadTask.execute(mUrlString);
    }

    /**
     * Cancel (and interrupt if necessary) any ongoing DownloadTask execution.
     */
    public void cancelDownload() {
        if (mDownloadTask != null) {
            Log.i("Network Update", "download canceled");
            mDownloadTask.cancel(true);
        }
    }

    /**
     * Implementation of AsyncTask designed to fetch data from the network.
     */
    private class DownloadTask extends AsyncTask<String, Void, DownloadTask.Result>
    {

        private DownloadCallback<String> mCallback;

        DownloadTask(DownloadCallback<String> callback)
        {
            Log.i("Network Update", "Creating Download Task");
            setCallback(callback);
        }

        void setCallback(DownloadCallback<String> callback) {
            mCallback = callback;
            Log.i("Network Update", "Callback set");
        }

        /**
         * Wrapper class that serves as a union of a result value and an exception. When the download
         * task has completed, either the result value or exception can be a non-null value.
         * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
         */
        class Result {
            public String mResultValue;
            public Exception mException;
            public Result(String resultValue) {
                mResultValue = resultValue;
            }
            public Result(Exception exception) {
                mException = exception;
            }
        }

        /**
         * Cancel background network operation if we do not have network connectivity.
         */
        @Override
        protected void onPreExecute() {
            Log.i("Network Update", "running PreExecute");
            if (mCallback != null)
            {
                onAttach((Context) getActivity());
            }

            NetworkInfo networkInfo = mCallback.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected() ||
                    (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                            && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE))
            {
                // If no connectivity, cancel task and update Callback with null data.
                Log.e("Network Update", "Network Unavaiable, aborting");
                mCallback.updateFromDownload(null);
                cancel(true);
            }

        }

        /**
         * Defines work to perform on the background thread.
         */
        @Override
        protected DownloadTask.Result doInBackground(String... urls) {
            Log.i("Network Update", "running doInBackground");
            Result result = null;
            if (!isCancelled() && urls != null && urls.length > 0) {
                Log.i("Network Update", "doInBackground urls is not null");
                String urlString = urls[0];
                try {
                    Log.i("Network Update", "doInBackground trying");
                    URL url = new URL(urlString);
                    String resultString = downloadUrl(url);
                    if (resultString != null) {
                        result = new Result(resultString);
                    } else {
                        throw new IOException("No response received.");
                    }
                }
                catch(MalformedURLException e)
                {
                    Log.i("Network Update", "doInBackground except caught " + e.toString() + " " + urls[0]);

                    result = new Result(e);
                }
                catch(Exception e) {
                    Log.i("Network Update", "doInBackground except caught " + e.toString() + " " + e);

                    result = new Result(e);
                }
            }
            Log.i("Network Update", "result retrived, returning" + result.mResultValue);
            return result;
        }

        /**
         * Updates the DownloadCallback with the result.
         */
        @Override
        protected void onPostExecute(Result result) {
            Log.i("Network Update", "running PostExecute with result: " + result.mResultValue + " \nwith callback: " + mCallback);
            if (result != null && mCallback != null) {
                if (result.mException != null) {
                    Log.i("Network Update", "sending exception to ui thread");
                    mCallback.updateFromDownload(result.mException.getMessage());
                } else if (result.mResultValue != null) {
                    Log.i("Network Update", "sending result to ui thread");
                    mCallback.updateFromDownload(result.mResultValue);
                }
                mCallback.finishDownloading();
            }
            Log.i("Network Update", "end of postexecute");
        }

        /**
         * Override to add special behavior for cancelled AsyncTask.
         */
        @Override
        protected void onCancelled(Result result)
        {
            Log.i("Network Update", "async canceled");
        }

        /**
         * Given a URL, sets up a connection and gets the HTTP response body from the server.
         * If the network request is successful, it returns the response body in String form. Otherwise,
         * it will throw an IOException.
         */
        private String downloadUrl(URL url) throws IOException
        {
            InputStream stream = null;
            HttpURLConnection connection = null;
            String result = null;
            Log.i("Network Update", "Entering try of downloadUrl");
            try {
                connection = (HttpURLConnection) url.openConnection();
                Log.i("Network Update", "Connection opened");
                // Timeout for reading InputStream arbitrarily set to 3000ms.
                connection.setReadTimeout(3000);
                // Timeout for connection.connect() arbitrarily set to 3000ms.
                connection.setConnectTimeout(3000);
                // For this use case, set HTTP method to GET.
                connection.setRequestMethod("GET");
                // Already true by default but setting just in case; needs to be true since this request
                // is carrying an input (response) body.
                connection.setDoInput(true);
                // Open communications link (network traffic occurs here).
                connection.connect();
                Log.i("Network Update", "Preparing to publish progress");
                //publishProgress(DownloadCallback.Progress.CONNECT_SUCCESS);
                Log.i("Network Update", "Progress Published");
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
                // Retrieve the response body as an InputStream.
                stream = connection.getInputStream();
                //publishProgress(DownloadCallback.Progress.GET_INPUT_STREAM_SUCCESS, 0);
                if (stream != null) {
                    // Converts Stream to String with max length of 500.
                    result = readStream(stream, 500);
                }
            } finally {
                // Close Stream and disconnect HTTPS connection.
                if (stream != null) {
                    stream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return result;
        }

        /**
         * Converts the contents of an InputStream to a String.
         */
        private String readStream(InputStream stream, int maxLength) throws IOException
        {
            /*String result = null;
            // Read InputStream using the UTF-8 charset.
            InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
            // Create temporary buffer to hold Stream data with specified max length.
            char[] buffer = new char[maxLength];
            // Populate temporary buffer with Stream data.
            int numChars = 0;
            int readSize = 0;
            while (numChars < maxLength && readSize != -1) {
                numChars += readSize;
                int pct = (100 * numChars) / maxLength;

                publishProgress(DownloadCallback.Progress.PROCESS_INPUT_STREAM_IN_PROGRESS, pct);
                readSize = reader.read(buffer, numChars, buffer.length - numChars);
            }
            if (numChars != -1) {
                // The stream was not empty.
                // Create String that is actual length of response body if actual length was less than
                // max length.
                numChars = Math.min(numChars, maxLength);
                result = new String(buffer, 0, numChars);
            }
            return result;*/
            return readJson(stream, maxLength);
        }

        private String readJson(InputStream stream, int maxLength) throws IOException
        {
            String result = null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line + "\n");
            }
            return result = sb.toString();
        }

    }

}
