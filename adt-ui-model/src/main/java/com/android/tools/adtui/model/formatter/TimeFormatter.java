/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.model.formatter;

import com.intellij.util.text.DateFormatUtil;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public final class TimeFormatter {

  /**
   * Return a formatted time String in the form of "hh:mm:ss.sss".
   * Default format for Range description.
   *
   * examples:
   * 01:02:11.000
   * 00:02:11.125
   * 00:00:11.125
   */
  public static String getFullClockString(long micro) {
    micro = Math.max(0, micro);
    long milli = TimeUnit.MICROSECONDS.toMillis(micro) % TimeUnit.SECONDS.toMillis(1);
    long sec = TimeUnit.MICROSECONDS.toSeconds(micro) % TimeUnit.MINUTES.toSeconds(1);
    long min = TimeUnit.MICROSECONDS.toMinutes(micro) % TimeUnit.HOURS.toMinutes(1);
    long hour = TimeUnit.MICROSECONDS.toHours(micro);

    return String.format("%02d:%02d:%02d.%03d", hour, min, sec, milli);
  }

  /**
   * Return a formatted time String in the form of "hh:mm:ss.sss"".
   * Hide hours value if both hours and minutes value are zero.
   * Default format for Tooltips.
   *
   * examples:
   * 01:02:11.000
   * 00:02:11.125
   * 00:11.125
   * + 123 μs
   */
  public static String getSemiSimplifiedClockString(long micro) {
    micro = Math.max(0, micro);
    String result = getFullClockString(micro);
    return result.startsWith("00:00:") ? result.substring(3) : result;
  }

  /**
   * Return a formatted time String in the form of "hh:mm:ss.sss".
   * Hide zero hours and minutes value.
   * Default format for timeline.
   *
   * examples:
   * 01:02:11.000
   * 01:00:11.000
   * 02:11.125
   * 11.125
   */
  public static String getSimplifiedClockString(long micro) {
    String result = getFullClockString(micro);
    int index = result.startsWith("00:") ? (result.startsWith("00:", 3) ? 6 : 3) : 0;
    return result.substring(index);
  }

  /**
   * Return a formatted time String in the form of "[h] h, [mm] m, [ss] s, [sss] ms or [μμμ] μs".
   *
   * examples:
   * 1.22 h
   * 3.40 m
   * 28.34 s
   * 400 ms
   * 21.654 μs
   */
  public static String getSingleUnitDurationString(long micro) {
    micro = Math.max(0, micro);
    String[] units = new String[]{"μs", "ms", "s", "m", "h"};

    float[] multipliers = new float[]{
      1,
      TimeUnit.MILLISECONDS.toMicros(1),
      TimeUnit.SECONDS.toMicros(1),
      TimeUnit.MINUTES.toMicros(1),
      TimeUnit.HOURS.toMicros(1)
    };

    double value = micro;
    String unit = units[0];
    for (int i = units.length - 1; i >= 0; --i) {
      if (micro / multipliers[i] >= 1) {
        value = micro / multipliers[i];
        unit = units[i];
        break;
      }
    }
    return new DecimalFormat("###.##").format(value) + " " + unit;
  }

  /**
   * Return a formatted time String in the form of "[h] hrs [mm] min [ss] sec".
   *
   * examples:
   * 32 sec
   * 15 min
   * 1 hr 22 min
   * 2 hrs 42 min
   */
  public static String getMultiUnitDurationString(long micro) {
    micro = Math.max(0, micro);
    long sec = TimeUnit.MICROSECONDS.toSeconds(micro) % TimeUnit.MINUTES.toSeconds(1);
    long min = TimeUnit.MICROSECONDS.toMinutes(micro) % TimeUnit.HOURS.toMinutes(1);
    long hour = TimeUnit.MICROSECONDS.toHours(micro);

    StringBuilder builder = new StringBuilder();
    if (hour > 0) {
      builder.append(hour);
      builder.append(" ");
      builder.append(hour == 1 ? "hr " : "hrs ");
    }

    if (min > 0) {
      builder.append(min);
      builder.append(" min ");
    }

    if (sec > 0 || (hour == 0 && min == 0)) {
      builder.append(sec);
      builder.append(" sec");
    }
    return builder.toString().trim();
  }

  public static String getLocalizedTime(long milli) {
    return DateFormatUtil.formatTime(milli);
  }

  public static String getLocalizedDateTime(long milli) {
    return DateFormatUtil.formatDateTime(milli);
  }
}
