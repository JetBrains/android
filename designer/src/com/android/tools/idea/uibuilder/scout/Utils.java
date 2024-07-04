/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.uibuilder.scout;

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * Simple Utilities used by the Inference system
 */
public class Utils {
    private static DecimalFormat df = new DecimalFormat("0.0#####");
    /**
     * Calculate the maximum of an array
     * @param array
     * @return the index of the maximum
     */
    static int max(float[] array) {
        int max = 0;
        float val = array[0];
        for (int i = 1; i < array.length; i++) {
            if (val < array[i]) {
                max = i;
                val = array[i];
            }
        }
        return max;
    }

    /**
     * Calculate the maximum of a 2D array
     *
     * @param array
     * @param result the index of the maximum filled by the function
     * @return the value of the maximum probabilities
     */
    static float max(float[][] array, int[] result) {
        int max1 = 0;
        int max2 = 0;
        float val = array[max1][max2];
        for (int i = 0; i < array.length; i++) {
            for (int j = 0; j < array[0].length; j++) {
                if (val < array[i][j]) {
                    max1 = i;
                    max2 = j;
                    val = array[max1][max2];
                }
            }
        }
        result[0] = max1;
        result[1] = max2;
        return val;
    }

    /**
     * convert an array of floats to fixed length strings
     * @param a
     * @return
     */
    static String toS(float[] a) {
        String s = "[";
        if (a == null) {
            return "[null]";
        }
        for (int i = 0; i < a.length; i++) {
            if (i != 0) {
                s += " , ";
            }
            String t = df.format(a[i]) + "       ";
            s += t.substring(0, 7);

        }
        s += "]";
        return s;
    }

    /**
     * Left trim a string to a fixed length
     *
     * @param str String to trim
     * @param len length to trim to
     * @return the trimed string
     */
    static String leftTrim(String str, int len) {
        return str.substring(str.length() - len);
    }

    /**
     * Fill a 2D array of floats with 0.0
     * @param array
     */
    static void zero(float[][] array) {
        for (float[] aFloat : array) {
            Arrays.fill(aFloat, -1);
        }
    }
}
