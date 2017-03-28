package com.android.tests.flavorlib.lib.flavor1;

import com.android.tests.flavorlib.lib.R;

import android.app.Activity;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Lib {

    public static void handleTextView(Activity a) {
        TextView tv = (TextView) a.findViewById(R.id.lib_text2);
        if (tv != null) {
            tv.setText(getContent());
        }
    }

    private static String getContent() {
        InputStream input = Lib.class.getResourceAsStream("Lib.txt");
        if (input == null) {
            return "FAILED TO FIND Lib.txt";
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
