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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import org.jdom.Element;

/** A mock {@link BlazeCommandRunConfigurationHandlerProvider}. */
public class MockBlazeCommandRunConfigurationHandlerProvider
    implements BlazeCommandRunConfigurationHandlerProvider {
  @Override
  public boolean canHandleKind(TargetState state, @Nullable Kind kind) {
    return true;
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new MockBlazeCommandRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "MockBlazeCommandRunConfigurationHandlerProvider";
  }

  /** A mock {@link RunConfigurationState}. */
  private static class MockRunConfigurationState implements RunConfigurationState {

    @Override
    public void readExternal(Element element) {
      // Don't read anything.
    }

    @Override
    public void writeExternal(Element element) {
      // Don't write anything.
    }

    @Override
    public RunConfigurationStateEditor getEditor(Project project) {
      return new RunConfigurationStateEditor() {
        @Override
        public void resetEditorFrom(RunConfigurationState state) {
          // Do nothing.
        }

        @Override
        public void applyEditorTo(RunConfigurationState state) {
          // Do nothing.
        }

        @Override
        public JComponent createComponent() {
          return null;
        }

        @Override
        public void setComponentEnabled(boolean enabled) {}
      };
    }
  }

  /** A mock {@link MockBlazeCommandRunConfigurationRunner}. */
  private static class MockBlazeCommandRunConfigurationRunner
      implements BlazeCommandRunConfigurationRunner {

    @Override
    public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment)
        throws ExecutionException {
      return null;
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
      return true;
    }
  }

  /** A mock {@link BlazeCommandRunConfigurationHandler}. */
  private static class MockBlazeCommandRunConfigurationHandler
      implements BlazeCommandRunConfigurationHandler {

    final BlazeCommandRunConfiguration configuration;
    final MockRunConfigurationState state;

    MockBlazeCommandRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
      this.configuration = configuration;
      this.state = new MockRunConfigurationState();
    }

    @Override
    public MockRunConfigurationState getState() {
      return state;
    }

    @Override
    public BlazeCommandRunConfigurationRunner createRunner(
        Executor executor, ExecutionEnvironment environment) {
      return new MockBlazeCommandRunConfigurationRunner();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      // Don't throw anything.
    }

    @Nullable
    @Override
    public String suggestedName(BlazeCommandRunConfiguration configuration) {
      return null;
    }

    @Nullable
    @Override
    public BlazeCommandName getCommandName() {
      return null;
    }

    @Override
    public String getHandlerName() {
      return "Mock Handler";
    }
  }
}
