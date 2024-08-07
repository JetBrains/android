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
package com.google.idea.blaze.base.run.state;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Shared state common to several {@link
 * com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} types.
 */
public class BlazeCommandRunConfigurationCommonState extends RunConfigurationCompositeState {
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";
  private static final String TEST_FILTER_FLAG_PREFIX = BlazeFlags.TEST_FILTER + '=';

  protected final BlazeCommandState command;
  protected final RunConfigurationFlagsState blazeFlags;
  protected final RunConfigurationFlagsState exeFlags;
  protected final BlazeBinaryState blazeBinary;

  public BlazeCommandRunConfigurationCommonState(BuildSystemName buildSystemName) {
    command = new BlazeCommandState();
    blazeFlags = new RunConfigurationFlagsState(USER_BLAZE_FLAG_TAG, buildSystemName + " flags:");
    exeFlags = new RunConfigurationFlagsState(USER_EXE_FLAG_TAG, "Executable flags:");
    blazeBinary = new BlazeBinaryState();
  }

  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(command, blazeFlags, exeFlags, blazeBinary);
  }

  /** @return The list of blaze flags that the user specified manually. */
  public RunConfigurationFlagsState getBlazeFlagsState() {
    return blazeFlags;
  }

  /** @return The list of executable flags the user specified manually. */
  public RunConfigurationFlagsState getExeFlagsState() {
    return exeFlags;
  }

  public BlazeBinaryState getBlazeBinaryState() {
    return blazeBinary;
  }

  public BlazeCommandState getCommandState() {
    return command;
  }

  /** Searches through all blaze flags for the first one beginning with '--test_filter' */
  @Nullable
  public String getTestFilterFlag() {
    for (String flag : getBlazeFlagsState().getRawFlags()) {
      if (flag.startsWith(BlazeFlags.TEST_FILTER)) {
        return flag;
      }
    }
    return null;
  }

  /**
   * The actual test filter value intended to be passed directly to external processes or
   * environment variables.
   *
   * <p>Unlike {@link #getTestFilterFlag()}, this is not a flag intended to be used on the command
   * line, so the shell-escaping/quoting has been removed.
   */
  @Nullable
  public String getTestFilterForExternalProcesses() {
    String testFilterFlag =
        getBlazeFlagsState().getFlagsForExternalProcesses().stream()
            .filter(flag -> flag.startsWith(BlazeFlags.TEST_FILTER))
            .findFirst()
            .orElse(null);
    if (testFilterFlag == null) {
      return null;
    }

    checkState(testFilterFlag.startsWith(TEST_FILTER_FLAG_PREFIX));
    return testFilterFlag.substring(TEST_FILTER_FLAG_PREFIX.length());
  }

  public ImmutableList<String> getTestArgs() {
    return getBlazeFlagsState().getRawFlags().stream()
        .filter(f -> f.startsWith(BlazeFlags.TEST_ARG))
        .map(f -> f.substring(BlazeFlags.TEST_ARG.length()))
        .collect(toImmutableList());
  }

  public void validate(BuildSystemName buildSystemName) throws RuntimeConfigurationException {
    if (getCommandState().getCommand() == null) {
      throw new RuntimeConfigurationError("You must specify a command.");
    }
    String blazeBinaryString = getBlazeBinaryState().getBlazeBinary();
    if (blazeBinaryString != null && !(new File(blazeBinaryString).exists())) {
      throw new RuntimeConfigurationError(buildSystemName.getName() + " binary does not exist");
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new RunConfigurationCompositeStateEditor(project, getStates());
  }
}
