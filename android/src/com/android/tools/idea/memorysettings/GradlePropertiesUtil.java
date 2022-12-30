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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradlePropertiesUtil {
  private static final String XMX_PROPERTY = "-Xmx";
  private static final String TOP_SPLIT_REGEX = "([^ \"']|\"[^\"]*\"|'[^']*')+";
  private static final String INNER_SPLIT_REGEX = "([^, \"']|\"[^\"]*\"|'[^']*')+";
  private static final String TOP_DELIMITER = " ";
  private static final String INNER_DELIMITER = ",";
  private static final String KOTLIN_JVM_OPTIONS_PROPERTY = "-Dkotlin.daemon.jvm.options";

  static boolean hasJvmArgs(GradleProperties properties) {
    return properties != null && properties.getJvmArgs() != null;
  }
  static int getGradleDaemonXmx(GradleProperties properties) {
    return properties == null ? -1 : getXmxFromVmArgs(properties.getJvmArgs(), TOP_SPLIT_REGEX);
  }

  static int getKotlinDaemonXmx(GradleProperties properties) {
    return properties == null ? -1 : getKotlinXmxFromVMArgs(properties.getJvmArgs());
  }

  static void setDaemonXmx(GradleProperties properties, int gradleValue, int kotlinValue) throws IOException {
    if (gradleValue > 0 || kotlinValue > 0) {
      properties.setJvmArgs(setDaemonXmx(properties.getJvmArgs(), gradleValue, kotlinValue));
      properties.save();
    }
  }

  @VisibleForTesting
  static String setDaemonXmx(String vmArgs, int gradleValue, int kotlinValue) {
    String result = vmArgs;
    if (gradleValue > 0) {
      result = setXmxInVmArgs(result, TOP_SPLIT_REGEX, TOP_DELIMITER, gradleValue);
    }
    if (kotlinValue > 0) {
      if (Strings.isNullOrEmpty(result)) {
        return kotlinXmxJvmOption(kotlinValue);
      }
      List<String> properties = splitProperties(result, TOP_SPLIT_REGEX);
      int i = getLastIndexOfProperty(properties, KOTLIN_JVM_OPTIONS_PROPERTY);
      if (i == -1) {
        properties.add(1, kotlinXmxJvmOption(kotlinValue));
      }
      else {
        String kotlinDaemonJvmOptions = getKotlinDaemonJvmOptions(properties.get(i));
        String newKotlinDaemonJvmOptions = setXmxInVmArgs(kotlinDaemonJvmOptions, INNER_SPLIT_REGEX, INNER_DELIMITER, kotlinValue);
        properties.set(i, String.format(Locale.US, "%s=\"%s\"", KOTLIN_JVM_OPTIONS_PROPERTY, newKotlinDaemonJvmOptions));
      }
      result = String.join(TOP_DELIMITER, properties);
    }
    return result;
  }

  private static String setXmxInVmArgs(String vmArgs, String regex, String delimiter, int value) {
    if (Strings.isNullOrEmpty(vmArgs)) {
      return getXmxString(value);
    }
    List<String> properties = splitProperties(vmArgs, regex);
    int i = getLastIndexOfProperty(properties, XMX_PROPERTY);
    if (i == -1) {
      properties.add(1, getXmxString(value));
    }
    else {
      properties.set(i, getXmxString(value));
    }
    return String.join(delimiter, properties);
  }

  private static String getXmxString(int value) {
    return String.format(Locale.US, "%s%sM", XMX_PROPERTY, Integer.toString(value));
  }

  private static String kotlinXmxJvmOption(int value) {
    return String.format(Locale.US, String.format("%s=\"%s%sM\"", KOTLIN_JVM_OPTIONS_PROPERTY, XMX_PROPERTY, value));
  }

  private static List<String> splitProperties(String vmArgs, String regex) {
    List<String> matchList = new ArrayList();
    Pattern pattern = Pattern.compile(regex);
    Matcher regexMatcher = pattern.matcher(vmArgs);
    while (regexMatcher.find()) {
      matchList.add(regexMatcher.group());
    }
    return matchList;
  }

  private static int getLastIndexOfProperty(List<String> properties, String prefix) {
    for (int i = properties.size() - 1; i >= 0; i--) {
      String property = properties.get(i);
      if (property.startsWith(prefix)) {
        return i;
      }
    }
    return -1;
  }

  private static String getProperty(String vmArgs, String regex, String prefix) {
    List<String> properties = splitProperties(vmArgs, regex);
    int i = getLastIndexOfProperty(properties, prefix);
    return i == -1 ? null : properties.get(i);
  }

  private static int getKotlinXmxFromVMArgs(String vmArgs) {
    if (Strings.isNullOrEmpty(vmArgs)) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    String kotlinProperty = getProperty(vmArgs, TOP_SPLIT_REGEX, KOTLIN_JVM_OPTIONS_PROPERTY);
    if (kotlinProperty == null) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    String kotlinJvmOptions = getKotlinDaemonJvmOptions(kotlinProperty);
    return kotlinJvmOptions == null ? -1 : getXmxFromVmArgs(kotlinJvmOptions, INNER_SPLIT_REGEX);
  }

  // The input property has format "-Dkotlin.daemon.jvm.options=\"\"".
  // Returns the string inside the quote.
  private static String getKotlinDaemonJvmOptions(String property) {
    int i = property.indexOf('=');
    if (i < 0) {
      return null;
    }
    String kotlinJvmOptions = property.substring(i + 1);
    return kotlinJvmOptions.replaceAll("^['|\"]|['|\"]$", "");
  }

  // Returns Xmx value in the vm args, and NO_XMX_IN_VM_ARGS if
  // no "-Xmx", or -1 if invalid value.
  private static int getXmxFromVmArgs(String vmArgs, String regex) {
    if (Strings.isNullOrEmpty(vmArgs)) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    String xmxProperty = getProperty(vmArgs, regex, XMX_PROPERTY);
    if (xmxProperty == null) {
      return MemorySettingsUtil.NO_XMX_IN_VM_ARGS;
    }
    return parseMemorySizeInMB(xmxProperty.substring(XMX_PROPERTY.length()));
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
