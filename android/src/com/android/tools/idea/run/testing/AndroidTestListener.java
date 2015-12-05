/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run.testing;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestListener implements ITestRunListener {
  @NotNull private final LaunchStatus myLaunchStatus;
  @NotNull private final ConsolePrinter myPrinter;

  private long myTestStartingTime;
  private long myTestSuiteStartingTime;
  private String myTestClassName = null;

  public AndroidTestListener(@NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    myLaunchStatus = launchStatus;
    myPrinter = printer;
  }

  @Override
  public void testRunStopped(long elapsedTime) {
    myLaunchStatus.terminateLaunch("Test run stopped.\n");
  }

  @Override
  public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
    if (myTestClassName != null) {
      testSuiteFinished();
    }
    myLaunchStatus.terminateLaunch("Tests ran to completion.\n");
  }

  @Override
  public void testRunFailed(String errorMessage) {
    myLaunchStatus.terminateLaunch("Test running failed: " + errorMessage);
  }

  @Override
  public void testRunStarted(String runName, int testCount) {
    myPrinter.stdout("Test running started\n");

    final ServiceMessageBuilder builder = new ServiceMessageBuilder("enteredTheMatrix");
    myPrinter.stdout(builder.toString());
  }

  @Override
  public void testStarted(TestIdentifier test) {
    if (!Comparing.equal(test.getClassName(), myTestClassName)) {
      if (myTestClassName != null) {
        testSuiteFinished();
      }
      myTestClassName = test.getClassName();
      testSuiteStarted();
    }
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testStarted");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" + test.getClassName() + '.' + test.getTestName() + "()");
    myPrinter.stdout(builder.toString());
    myTestStartingTime = System.currentTimeMillis();
  }

  private void testSuiteStarted() {
    myTestSuiteStartingTime = System.currentTimeMillis();
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteStarted");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" + myTestClassName);
    myPrinter.stdout(builder.toString());
  }

  private void testSuiteFinished() {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteFinished");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestSuiteStartingTime));
    myPrinter.stdout(builder.toString());
    myTestClassName = null;
  }

  @Override
  public void testFailed(TestIdentifier test, String stackTrace) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFailed");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("message", "");
    builder.addAttribute("details", stackTrace);
    builder.addAttribute("error", "true");
    myPrinter.stdout(builder.toString());
  }

  @Override
  public void testAssumptionFailure(TestIdentifier test, String trace) {
    ServiceMessageBuilder builder = ServiceMessageBuilder.testIgnored(test.getTestName());
    builder.addAttribute("message", "Test ignored. Assumption Failed:");
    builder.addAttribute("details", trace);
    myPrinter.stdout(builder.toString());
  }

  @Override
  public void testIgnored(TestIdentifier test) {
    ServiceMessageBuilder builder = ServiceMessageBuilder.testIgnored(test.getTestName());
    myPrinter.stdout(builder.toString());
  }

  @Override
  public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFinished");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestStartingTime));
    myPrinter.stdout(builder.toString());
  }
}
