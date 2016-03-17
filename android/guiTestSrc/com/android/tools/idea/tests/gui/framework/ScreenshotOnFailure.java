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
package com.android.tools.idea.tests.gui.framework;

import org.fest.swing.image.ScreenshotTaker;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/** Rule that takes a screenshot when the test fails. */
class ScreenshotOnFailure extends TestWatcher {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss");
  private static final GregorianCalendar CALENDAR = new GregorianCalendar();

  private final ScreenshotTaker myScreenshotTaker = new ScreenshotTaker();

  @Override
  protected void failed(Throwable throwable, Description description) {
    String fileNamePrefix = description.getTestClass().getSimpleName() + "." + description.getMethodName();
    String now = DATE_FORMAT.format(CALENDAR.getTime());

    try {
      String filePath = new File(IdeTestApplication.getFailedTestScreenshotDirPath(), fileNamePrefix + "." + now + ".png").getPath();
      myScreenshotTaker.saveDesktopAsPng(filePath);
      System.out.println("Screenshot: " + filePath);
    }
    catch (Throwable t) {
      System.out.println("Screenshot failed. " + t.getMessage());
    }
  }
}
