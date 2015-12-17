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
import com.android.tools.idea.run.AndroidRunningState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.openapi.util.Comparing;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestListener implements ITestRunListener {
  private final AndroidRunningState myRunningState;
  private long myTestStartingTime;
  private long myTestSuiteStartingTime;
  private String myTestClassName = null;
  private ProcessHandler myProcessHandler;

  public ProcessHandler getProcessHandler() {
    if (myProcessHandler == null) {
      myProcessHandler = myRunningState.getProcessHandler();
    }
    return myProcessHandler;
  }

  public AndroidTestListener(AndroidRunningState runningState) {
    myRunningState = runningState;
  }

  @Override
  public void testRunStopped(long elapsedTime) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running stopped\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  @Override
  public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
    if (myTestClassName != null) {
      testSuiteFinished();
    }
    final ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Finish\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  @Override
  public void testRunStarted(String runName, int testCount) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running started\n", ProcessOutputTypes.STDOUT);

    final ServiceMessageBuilder builder = new ServiceMessageBuilder("enteredTheMatrix");
    handler.notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
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
    builder
      .addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" + myRunningState.getModule().getName() +
                                    ':' + test.getClassName() + '.' + test.getTestName() + "()");
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
    myTestStartingTime = System.currentTimeMillis();
  }

  private void testSuiteStarted() {
    myTestSuiteStartingTime = System.currentTimeMillis();
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteStarted");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" +
                                         myRunningState.getModule().getName() + ':' + myTestClassName);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  private void testSuiteFinished() {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteFinished");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestSuiteStartingTime));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
    myTestClassName = null;
  }

  @Override
  public void testFailed(TestIdentifier test, String stackTrace) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFailed");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("message", "");
    builder.addAttribute("details", stackTrace);
    builder.addAttribute("error", "true");
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testAssumptionFailure(TestIdentifier test, String trace) {
    ServiceMessageBuilder builder = ServiceMessageBuilder.testIgnored(test.getTestName());
    builder.addAttribute("message", "Test ignored. Assumption Failed:");
    builder.addAttribute("details", trace);
    getProcessHandler().notifyTextAvailable(builder.toString() + "\n", ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testIgnored(TestIdentifier test) {
    ServiceMessageBuilder builder = ServiceMessageBuilder.testIgnored(test.getTestName());
    getProcessHandler().notifyTextAvailable(builder.toString() + "\n", ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFinished");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestStartingTime));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testRunFailed(String errorMessage) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running failed: " + errorMessage + "\n", ProcessOutputTypes.STDERR);
    handler.destroyProcess();
  }
}
