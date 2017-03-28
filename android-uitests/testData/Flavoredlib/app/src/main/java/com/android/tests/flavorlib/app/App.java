package com.android.tests.flavorlib.app;

import android.app.Activity;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class App {

    public static void handleTextView(Activity a) {
        TextView tv = (TextView) a.findViewById(R.id.app_text2);
        if (tv != null) {
            tv.setText(getContent());
        }
    }

    private static String getContent() {
        InputStream input = App.class.getResourceAsStream("App.txt");
        if (input == null) {
            return "FAILED TO FIND App.txt";
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));

            return reader.readLine();
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        return "FAILED TO READ CONTENT";
    }
}
