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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import static com.android.tools.idea.tests.gui.framework.IdeTestApplication.getFailedTestScreenshotDirPath;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;

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
    System.out.println(String.format("Executing test '%1$s'", getTestFqn()));

    runTest();
    failIfIdeHasFatalErrors();
  }

  /** Calls {@link GuiTests#failIfIdeHasFatalErrors} reflectively and on {@link AssertionError}, re-throws. */
  private static void failIfIdeHasFatalErrors() throws ClassNotFoundException, IllegalAccessException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> guiTestsType = Class.forName(GuiTests.class.getCanonicalName(), true, classLoader);
    Method method = method("failIfIdeHasFatalErrors").in(guiTestsType).target();
    try {
      method.invoke(null);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof AssertionError) {
        throw (AssertionError)cause;  // This is the intended behavior of GuiTests.failIfIdeHasFatalErrors.
      }
      else {
        throw new RuntimeException(cause);
      }
    }
  }

  private void runTest() throws Throwable {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> guiTestCaseType = Class.forName(GuiTestCase.class.getCanonicalName(), true, classLoader);

    if (guiTestCaseType.isInstance(myTest)) {
      field("myTestName").ofType(String.class).in(myTest).set(myTestMethod.getName());
    }
    try {
      myTestMethod.invokeExplosively(myTest);
    }
    catch (Throwable e) {
      e.printStackTrace();
      takeScreenshot();
      throw e;
    }
  }

  @NotNull
  private String getTestFqn() {
    return myTestMethod.getMethod().getDeclaringClass() + "#" + myTestMethod.getName();
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
