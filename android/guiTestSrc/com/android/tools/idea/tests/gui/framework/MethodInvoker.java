/*
 * Copyright (C) 2014 The Android Open Source Project
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
import org.jetbrains.annotations.NotNull;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import static com.android.tools.idea.tests.gui.framework.IdeTestApplication.getFailedTestScreenshotDirPath;

public class MethodInvoker extends Statement {
  @NotNull private final FrameworkMethod myTestMethod;
  @NotNull private final Object myTest;
  @NotNull private final ScreenshotTaker myScreenshotTaker;

  MethodInvoker(@NotNull FrameworkMethod testMethod, @NotNull Object test, @NotNull ScreenshotTaker screenshotTaker) {
    myTestMethod = testMethod;
    myTest = test;
    myScreenshotTaker = screenshotTaker;
  }

  @Override
  public void evaluate() throws Throwable {
    String testName = myTestMethod.getMethod().getDeclaringClass().getName() + "#" + myTestMethod.getName();
    System.out.println("Running " + testName);

    runTest();
  }

  private void runTest() throws Throwable {
    try {
      myTestMethod.invokeExplosively(myTest);
    }
    catch (Throwable e) {
      e.printStackTrace();
      takeScreenshot();
      throw e;
    }
  }

  private void takeScreenshot() {
    Method method = myTestMethod.getMethod();
    String fileNamePrefix = method.getDeclaringClass().getSimpleName() + "." + method.getName();
    String extension = ".png";

    try {
      File rootDir = getFailedTestScreenshotDirPath();

      File screenshotFilePath = new File(rootDir, fileNamePrefix + extension);
      if (screenshotFilePath.isFile()) {
        SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy.HH:mm:ss");
        String now = format.format(new GregorianCalendar().getTime());
        screenshotFilePath = new File(rootDir, fileNamePrefix + "." + now + extension);
      }
      myScreenshotTaker.saveDesktopAsPng(screenshotFilePath.getPath());
      System.out.println("Screenshot of failed test taken and stored at " + screenshotFilePath.getPath());
    }
    catch (Throwable ignored) {
      System.out.println("Failed to take screenshot. Cause: " + ignored.getMessage());
    }
  }
}
