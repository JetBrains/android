/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
import com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties;
import com.google.common.base.Preconditions;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

/**
 * Console provider for {@code android_instrumentation_test} aka AIT, when the tests are run
 * directly by the IDE.
 */
class AitIdeTestConsoleProvider implements ConsoleProvider {
  private final BlazeCommandRunConfiguration runConfiguration;

  AitIdeTestConsoleProvider(
      BlazeCommandRunConfiguration runConfiguration,
      BlazeAndroidTestRunConfigurationState configState) {
    Preconditions.checkArgument(
        configState.getLaunchMethod() != AndroidTestLaunchMethod.BLAZE_TEST);
    this.runConfiguration = runConfiguration;
  }

  @Override
  public ConsoleView createAndAttach(Disposable parent, ProcessHandler handler, Executor executor)
      throws ExecutionException {
    AndroidTestConsoleProperties properties =
        new AndroidTestConsoleProperties(runConfiguration, executor);
    ConsoleView consoleView = SMTestRunnerConnectionUtil.createConsole("Android", properties);
    Disposer.register(parent, consoleView);
    return consoleView;
  }
}
