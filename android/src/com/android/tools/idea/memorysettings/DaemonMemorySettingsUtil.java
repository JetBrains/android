/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Locale;

public class DaemonMemorySettingsUtil {
  private static final String XMX_PROPERTY = "-Xmx";

  public static int getGradleDaemonXmx(GradleProperties properties) {
    return properties == null ? -1 : getXmxFromVmArgs(properties.getJvmArgs());
  }

  public static void setGradleDaemonXmx(GradleProperties properties, int value) throws IOException {
    if (value > 0) {
      properties.setJvmArgs(setXmxInVmArgs(properties.getJvmArgs(), value));
      properties.save();
    }
  }

  @VisibleForTesting
  static String setXmxInVmArgs(String vmArgs, int value) {
    if (Strings.isNullOrEmpty(vmArgs)) {
      return String.format(Locale.US, "%s%sM", XMX_PROPERTY, Integer.toString(value));
    }
    int i = vmArgs.lastIndexOf(XMX_PROPERTY);
    if (i < 0) {
      // No Xmx, append -Xmx to vmArgs
     return String.format(Locale.US, "%s %s%sM",
                          vmArgs, XMX_PROPERTY, Integer.toString(value));
    }
    int replaceStart = i + XMX_PROPERTY.length();
    int next = vmArgs.indexOf(' ', replaceStart);
    return String.format(Locale.US, "%s%sM%s",
                         vmArgs.substring(0, replaceStart), Integer.toString(value),
                         next < 0 ? "" : vmArgs.substring(next));
  }

  // Returns Xmx value in the vm args, and NO_XMX_IN_VM_ARGS if
  // no "-Xmx", or -1 if invalid value.
  private static int getXmxFromVmArgs(String vmArgs) {
    if (Strings.isNullOrEmpty(vmArgs)) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    int i = vmArgs.lastIndexOf(XMX_PROPERTY);
    if (i < 0) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    return parseMemorySizeInMB(vmArgs.substring(i + XMX_PROPERTY.length()));
  }

  private static int parseMemorySizeInMB(String size) {
    try {
      for (int i = 0; i < size.length(); i++) {
        char c = size.charAt(i);

        if (!Character.isDigit(c)) {
          if (i == 0) {
            return -1;
          }
          String digits = size.substring(0, i);
          long value = Long.parseLong(digits);
          switch (c) {
            case 't':
            case 'T':
              return Math.toIntExact(value * 1024 * 1024);
            case 'g':
            case 'G':
              return Math.toIntExact(value * 1024);
            case 'm':
            case 'M':
              return Math.toIntExact(value);
            case 'k':
            case 'K':
              return Math.toIntExact(value / 1024);
            default:
              return -1;
          }
        }
      }
    }
    catch (NumberFormatException e) {
      return -1;
    }
    return -1;
  }
}
