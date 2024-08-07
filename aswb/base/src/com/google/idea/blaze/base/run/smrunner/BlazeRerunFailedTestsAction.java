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

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Re-run failed tests. */
public class BlazeRerunFailedTestsAction extends AbstractRerunFailedTestsAction {

  private final BlazeTestEventsHandler eventsHandler;

  BlazeRerunFailedTestsAction(
      BlazeTestEventsHandler eventsHandler, ComponentContainer componentContainer) {
    super(componentContainer);
    this.eventsHandler = eventsHandler;
  }

  @Override
  @Nullable
  protected MyRunProfile getRunProfile(ExecutionEnvironment environment) {
    final TestFrameworkRunningModel model = getModel();
    if (model == null) {
      return null;
    }
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) model.getProperties().getConfiguration();
    return new BlazeRerunTestRunProfile(config.clone());
  }

  class BlazeRerunTestRunProfile extends MyRunProfile {

    private final BlazeCommandRunConfiguration configuration;

    BlazeRerunTestRunProfile(BlazeCommandRunConfiguration configuration) {
      super(configuration);
      this.configuration = configuration;
    }

    @Override
    public Module[] getModules() {
      return Module.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public RunProfileState getState(Executor executor, ExecutionEnvironment environment)
        throws ExecutionException {
      BlazeCommandRunConfigurationCommonState handlerState =
          configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
      if (handlerState == null
          || !BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
        return null;
      }
      Project project = getProject();
      List<Location<?>> locations =
          getFailedTests(project)
              .stream()
              .filter(AbstractTestProxy::isLeaf)
              .map((test) -> toLocation(project, test))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      String testFilter = eventsHandler.getTestFilter(getProject(), locations);
      if (testFilter == null) {
        return null;
      }
      List<String> blazeFlags =
          setTestFilter(handlerState.getBlazeFlagsState().getRawFlags(), testFilter);
      handlerState.getBlazeFlagsState().setRawFlags(blazeFlags);
      return configuration.getState(executor, environment);
    }

    @Nullable
    private Location<?> toLocation(Project project, AbstractTestProxy test) {
      return test.getLocation(project, GlobalSearchScope.allScope(project));
    }

    /** Replaces existing test_filter flag, or appends if none exists. */
    private List<String> setTestFilter(List<String> flags, String testFilter) {
      List<String> copy = new ArrayList<>(flags);
      for (int i = 0; i < copy.size(); i++) {
        String flag = copy.get(i);
        if (flag.startsWith(BlazeFlags.TEST_FILTER)) {
          copy.set(i, testFilter);
          return copy;
        }
      }
      copy.add(testFilter);
      return copy;
    }
  }
}
