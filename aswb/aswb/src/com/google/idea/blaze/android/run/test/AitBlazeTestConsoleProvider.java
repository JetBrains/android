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

import com.android.tools.idea.run.ConsoleProvider;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultsProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

/**
 * Console provider for {@code android_instrumentation_test}, when the tests are run using Blaze
 * {@link AndroidTestLaunchMethod#BLAZE_TEST}.
 */
class AitBlazeTestConsoleProvider implements ConsoleProvider {
  private final Project project;
  private final BlazeCommandRunConfiguration runConfiguration;
  public final BlazeTestResultsProvider testResultFinderStrategy;

  AitBlazeTestConsoleProvider(
    Project project,
    BlazeCommandRunConfiguration runConfiguration, BlazeTestResultsProvider testResultFinderStrategy) {
    this.project = project;
    this.runConfiguration = runConfiguration;
    this.testResultFinderStrategy = testResultFinderStrategy;
  }

  @Override
  public ConsoleView createAndAttach(Disposable parent, ProcessHandler handler, Executor executor)
      throws ExecutionException {
    ConsoleView console = createBlazeTestConsole(executor);
    console.attachToProcess(handler);
    return console;
  }

  private ConsoleView createBlazeTestConsole(Executor executor) {
    if (testResultFinderStrategy == null || isDebugging(executor)) {
      // SM runner console not yet supported when debugging, because we're calling this once per
      // test case (see ConnectBlazeTestDebuggerTask::setUpForReattachingDebugger)
      return TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    }
    return SmRunnerUtils.getConsoleView(project, runConfiguration, executor, testResultFinderStrategy);
  }

  private static boolean isDebugging(Executor executor) {
    return executor instanceof DefaultDebugExecutor;
  }
}
