/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestLogger {

  private static String addDateStamp(String inputString) {
    // Get the current date and time
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    String formattedDateTime = now.format(formatter);
    // Add the formatted date and time to the beginning of the input string
    return String.format("%s - %s", formattedDateTime, inputString);
  }

  public static void log(String inputString, Object... args) {
    System.out.println(addDateStamp(String.format(inputString, args)));
  }
}
