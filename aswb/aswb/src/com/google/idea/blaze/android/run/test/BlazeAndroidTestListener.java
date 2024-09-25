/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.configuration.execution.ExecutionUtils;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestLocationProvider;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.execution.ui.ConsoleView;
import java.util.Map;
import java.util.Objects;

/**
 * Copy of {@link com.android.tools.idea.testartifacts.instrumented.AndroidTestListener}
 * encapsulating a {@link ProcessHandler}, which is notified on any {@link ServiceMessageBuilder}
 * the listener creates.
 */
public class BlazeAndroidTestListener implements ITestRunListener {

  private static final String DISPLAY_PREFIX = "android.studio.display.";

  private final ConsoleView consoleView;
  private final ProcessHandler processHandler;

  private long testStartingTime;
  private long testSuiteStartingTime;
  private String testClassName = null;

  public BlazeAndroidTestListener(ConsoleView consoleView, ProcessHandler processHandler) {
    this.consoleView = consoleView;
    this.processHandler = processHandler;
  }

  @Override
  public void testRunStopped(long elapsedTime) {
    ExecutionUtils.printlnError(consoleView, "Test run stopped.\n");
  }

  @Override
  public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
    if (testClassName != null) {
      testSuiteFinished();
    }
    ExecutionUtils.println(consoleView, "Tests ran to completion.");
  }

  @Override
  public void testRunFailed(String errorMessage) {
    ExecutionUtils.printlnError(consoleView, "Test running failed: " + errorMessage);
  }

  @Override
  public void testRunStarted(String runName, int testCount) {
    ExecutionUtils.println(consoleView, "Started running tests");

    final ServiceMessageBuilder builder = new ServiceMessageBuilder("enteredTheMatrix");
    notifyProcessHandler(builder);
  }

  @Override
  public void testStarted(TestIdentifier test) {
    if (!Objects.equals(test.getClassName(), testClassName)) {
      if (testClassName != null) {
        testSuiteFinished();
      }
      testClassName = test.getClassName();
      testSuiteStarted();
    }
    ServiceMessageBuilder builder =
        new ServiceMessageBuilder("testStarted")
            .addAttribute("name", test.getTestName())
            .addAttribute(
                "locationHint",
                AndroidTestLocationProvider.PROTOCOL_ID
                    + "://"
                    + test.getClassName()
                    + '.'
                    + test.getTestName()
                    + "()");
    notifyProcessHandler(builder);
    testStartingTime = System.currentTimeMillis();
  }

  private void testSuiteStarted() {
    testSuiteStartingTime = System.currentTimeMillis();
    ServiceMessageBuilder builder =
        new ServiceMessageBuilder("testSuiteStarted")
            .addAttribute("name", testClassName)
            .addAttribute(
                "locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" + testClassName);
    notifyProcessHandler(builder);
  }

  private void testSuiteFinished() {
    ServiceMessageBuilder builder =
        new ServiceMessageBuilder("testSuiteFinished")
            .addAttribute("name", testClassName)
            .addAttribute(
                "duration", Long.toString(System.currentTimeMillis() - testSuiteStartingTime));
    notifyProcessHandler(builder);
    testClassName = null;
  }

  @Override
  public void testFailed(TestIdentifier test, String stackTrace) {
    ServiceMessageBuilder builder =
        new ServiceMessageBuilder("testFailed")
            .addAttribute("name", test.getTestName())
            .addAttribute("message", "")
            .addAttribute("details", stackTrace)
            .addAttribute("error", "true");
    notifyProcessHandler(builder);
  }

  @Override
  public void testAssumptionFailure(TestIdentifier test, String trace) {
    ServiceMessageBuilder builder =
        ServiceMessageBuilder.testIgnored(test.getTestName())
            .addAttribute("message", "Test ignored. Assumption Failed:")
            .addAttribute("details", trace);
    notifyProcessHandler(builder);
  }

  @Override
  public void testIgnored(TestIdentifier test) {
    ServiceMessageBuilder builder = ServiceMessageBuilder.testIgnored(test.getTestName());
    notifyProcessHandler(builder);
  }

  @Override
  public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
    if (StudioFlags.PRINT_INSTRUMENTATION_STATUS.get()) {

      for (Map.Entry<String, String> entry : testMetrics.entrySet()) {
        String key = entry.getKey();
        if (key.startsWith(DISPLAY_PREFIX)) {
          ExecutionUtils.println(
              consoleView, key.substring(DISPLAY_PREFIX.length()) + ": " + entry.getValue());
        }
      }
    }

    ServiceMessageBuilder builder =
        new ServiceMessageBuilder("testFinished")
            .addAttribute("name", test.getTestName())
            .addAttribute("duration", Long.toString(System.currentTimeMillis() - testStartingTime));
    notifyProcessHandler(builder);
  }

  private void notifyProcessHandler(ServiceMessageBuilder serviceMessageBuilder) {
    processHandler.notifyTextAvailable(serviceMessageBuilder.toString(), ProcessOutputType.STDOUT);
  }
}
