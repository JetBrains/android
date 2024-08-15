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
package com.google.idea.blaze.android.run;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.Nullable;

/** Common interface for Blaze Android run configuration handlers. */
public interface BlazeAndroidRunConfigurationHandler extends BlazeCommandRunConfigurationHandler {
  /**
   * A convenience method for getting a {@link BlazeAndroidRunConfigurationHandler} from a {@link
   * RunProfile}, without having to do repetitive casts. Returns null if the given profile is not a
   * {@link BlazeCommandRunConfiguration} with a {@link BlazeAndroidRunConfigurationHandler} for its
   * handler.
   */
  @Nullable
  static BlazeAndroidRunConfigurationHandler getHandlerFrom(RunProfile profile) {
    if (!(profile instanceof BlazeCommandRunConfiguration)) {
      return null;
    }
    BlazeCommandRunConfigurationHandler handler =
        ((BlazeCommandRunConfiguration) profile).getHandler();
    if (!(handler instanceof BlazeAndroidRunConfigurationHandler)) {
      return null;
    }
    return (BlazeAndroidRunConfigurationHandler) handler;
  }

  /** @return This handler's common state. */
  BlazeAndroidRunConfigurationCommonState getCommonState();

  /** Extract {@link BlazeCommandRunConfiguration} from the execution environment. */
  static BlazeCommandRunConfiguration getCommandConfig(ExecutionEnvironment env)
      throws ExecutionException {
    RunProfile runProfile = env.getRunProfile();
    if (runProfile instanceof BlazeCommandRunConfiguration) {
      return (BlazeCommandRunConfiguration) runProfile;
    }
    throw new ExecutionException(
        "Cannot cast "
            + runProfile
            + " of type "
            + runProfile.getClass().getName()
            + " to BlazeCommandRunConfiguration.");
  }
}
