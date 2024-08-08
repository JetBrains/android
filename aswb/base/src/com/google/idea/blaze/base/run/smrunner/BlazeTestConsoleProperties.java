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
package com.google.idea.blaze.base.run.smrunner;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.Executor;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Integrates blaze test results with the SM-runner test UI. */
public class BlazeTestConsoleProperties extends SMTRunnerConsoleProperties
    implements SMCustomMessagesParsing {

  private final BlazeCommandRunConfiguration runConfiguration;
  private final BlazeTestUiSession testUiSession;

  public BlazeTestConsoleProperties(
      BlazeCommandRunConfiguration runConfiguration,
      Executor executor,
      BlazeTestUiSession testUiSession) {
    super(runConfiguration, SmRunnerUtils.BLAZE_FRAMEWORK, executor);
    this.runConfiguration = runConfiguration;
    this.testUiSession = testUiSession;
  }

  @Override
  public OutputToGeneralTestEventsConverter createTestEventsConverter(
      String framework, TestConsoleProperties consoleProperties) {
    return new BlazeXmlToTestEventsConverter(
        framework, consoleProperties, testUiSession.getTestResultFinderStrategy());
  }

  @Override
  public SMTestLocator getTestLocator() {
    return new CompositeSMTestLocator(
        ImmutableList.copyOf(
            Arrays.stream(BlazeTestEventsHandler.EP_NAME.getExtensions())
                .map(BlazeTestEventsHandler::getTestLocator)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())));
  }

  @Nullable
  @Override
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return BlazeTestEventsHandler.getHandlerForTargets(
            runConfiguration.getProject(), runConfiguration.getTargets())
        .map(handler -> handler.createRerunFailedTestsAction(consoleView))
        .orElse(null);
  }
}
