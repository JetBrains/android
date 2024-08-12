/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tools.idea.execution.common.RunConfigurationNotifier;
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.blaze.BlazeLaunchContext;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.configuration.execution.ExecutionUtils;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

class StockAndroidTestLaunchTask implements BlazeLaunchTask {
  private static final String ID = "STOCK_ANDROID_TEST";
  private static final Logger LOG = Logger.getInstance(StockAndroidTestLaunchTask.class);
  private final BlazeAndroidTestRunConfigurationState configState;
  private final String instrumentationTestRunner;
  private final String testApplicationId;
  private final boolean waitForDebugger;

  StockAndroidTestLaunchTask(
      BlazeAndroidTestRunConfigurationState configState,
      String runner,
      String testPackage,
      boolean waitForDebugger) {
    this.configState = configState;
    this.instrumentationTestRunner = runner;
    this.waitForDebugger = waitForDebugger;
    this.testApplicationId = testPackage;
  }

  @Nullable
  public static BlazeLaunchTask getStockTestLaunchTask(
      BlazeAndroidTestRunConfigurationState configState,
      ApplicationIdProvider applicationIdProvider,
      boolean waitForDebugger,
      BlazeAndroidDeployInfo deployInfo,
      Project project)
      throws ExecutionException {
    String testPackage;
    try {
      testPackage = applicationIdProvider.getTestPackageName();
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to determine test package name. " + e.getMessage());
    }
    if (testPackage == null) {
      throw new ExecutionException("Unable to determine test package name.");
    }
    List<String> availableRunners = getRunnersFromManifest(deployInfo);
    if (availableRunners.isEmpty()) {
      RunConfigurationNotifier.INSTANCE.notifyError(
          project,
          "",
          String.format(
              "No instrumentation test runner is defined in the manifest.\n"
                  + "At least one instrumentation tag must be defined for the\n"
                  + "\"%1$s\" package in the AndroidManifest.xml, e.g.:\n"
                  + "\n"
                  + "<manifest\n"
                  + "    package=\"%1$s\"\n"
                  + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                  + "\n"
                  + "    <instrumentation\n"
                  + "        android:name=\"androidx.test.runner.AndroidJUnitRunner\"\n"
                  + "        android:targetPackage=\"%1$s\">\n"
                  + "    </instrumentation>\n"
                  + "\n"
                  + "</manifest>",
              testPackage));
      // Note: Gradle users will never see the above message, so don't mention Gradle here.
      // Even if no runners are defined in build.gradle, Gradle will add a default to the manifest.
      throw new ExecutionException("No instrumentation test runner is defined in the manifest.");
    }
    String runner = configState.getInstrumentationRunnerClass();
    if (StringUtil.isEmpty(runner)) {
      // Default to the first available runner.
      runner = availableRunners.get(0);
    }
    if (!availableRunners.contains(runner)) {
      RunConfigurationNotifier.INSTANCE.notifyError(
          project,
          "",
          String.format(
              "Instrumentation test runner \"%2$s\"\n"
                  + "is not defined for the \"%1$s\" package in the manifest.\n"
                  + "Clear the 'Specific instrumentation runner' field in your configuration\n"
                  + "to default to \"%3$s\",\n"
                  + "or add the runner to your AndroidManifest.xml:\n"
                  + "\n"
                  + "<manifest\n"
                  + "    package=\"%1$s\"\n"
                  + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                  + "\n"
                  + "    <instrumentation\n"
                  + "        android:name=\"%2$s\"\n"
                  + "        android:targetPackage=\"%1$s\">\n"
                  + "    </instrumentation>\n"
                  + "\n"
                  + "</manifest>",
              testPackage, runner, availableRunners.get(0)));
      throw new ExecutionException(
          String.format(
              "Instrumentation test runner \"%2$s\" is not defined for the \"%1$s\" package in the"
                  + " manifest.",
              testPackage, runner));
    }

    return new StockAndroidTestLaunchTask(configState, runner, testPackage, waitForDebugger);
  }

  private static ImmutableList<String> getRunnersFromManifest(
      final BlazeAndroidDeployInfo deployInfo) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication()
          .runReadAction(
              (Computable<ImmutableList<String>>) () -> getRunnersFromManifest(deployInfo));
    }
    ManifestParser.ParsedManifest parsedManifest = deployInfo.getMergedManifest();
    if (parsedManifest != null) {
      return ImmutableList.copyOf(parsedManifest.instrumentationClassNames);
    }
    return ImmutableList.of();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void run(@NotNull BlazeLaunchContext launchContext) {
    ConsoleView console = launchContext.getConsoleView();
    IDevice device = launchContext.getDevice();
    ExecutionUtils.println(console, "Running tests\n");
    final RemoteAndroidTestRunner runner =
        new RemoteAndroidTestRunner(testApplicationId, instrumentationTestRunner, device);
    switch (configState.getTestingType()) {
      case BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE:
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_PACKAGE:
        runner.setTestPackageName(configState.getPackageName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_CLASS:
        runner.setClassName(configState.getClassName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_METHOD:
        runner.setMethodName(configState.getClassName(), configState.getMethodName());
        break;
      default:
        throw new RuntimeException(
            String.format("Unrecognized testing type: %d", configState.getTestingType()));
    }
    runner.setDebug(waitForDebugger);
    runner.setRunOptions(configState.getExtraOptions());
    ExecutionUtils.printShellCommand(console, runner.getAmInstrumentCommand());
    // run in a separate thread as this will block until the tests complete
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              try {
                // This issues "am instrument" command and blocks execution.
                runner.run(
                    new BlazeAndroidTestListener(console, launchContext.getProcessHandler()));
                // Detach the device from the android process handler manually as soon as "am
                // instrument" command finishes. This is required because the android process
                // handler may overlook target process especially when the test
                // runs really fast (~10ms). Because the android process handler discovers new
                // processes by polling, this race condition happens easily. By detaching the device
                // manually, we can avoid the android process handler waiting for (already finished)
                // process to show up until it times out (10 secs).
                // Note: this is a copy of ag/9593981, but it is worth figuring out a better
                // strategy here if the behavior of AndroidTestListener is not guaranteed.
                ProcessHandler processHandler = launchContext.getProcessHandler();
                if (processHandler instanceof AndroidProcessHandler) {
                  ((AndroidProcessHandler) processHandler).detachDevice(launchContext.getDevice());
                }
              } catch (Exception e) {
                ExecutionUtils.printlnError(
                    console, "Error: Unexpected exception while running tests: " + e);
              }
            });
  }
}
