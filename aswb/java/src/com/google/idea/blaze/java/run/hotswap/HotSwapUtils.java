/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.hotswap;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;

/** Helper methods for HotSwapping */
public final class HotSwapUtils {

  private HotSwapUtils() {}

  static final BoolExperiment enableHotSwapping =
      new BoolExperiment("java.hotswapping.enabled", true);

  private static final ImmutableSet<Kind> SUPPORTED_BINARIES = getSupportedBinaries();

  private static ImmutableSet<Kind> getSupportedBinaries() {
    return JavaLikeLanguage.getAllDebuggableKinds().stream()
        .filter(kind -> kind.getRuleType().equals(RuleType.BINARY))
        .collect(toImmutableSet());
  }

  public static boolean canHotSwap(ExecutionEnvironment env) {
    if (!isDebugging(env) || !enableHotSwapping.getValue()) {
      return false;
    }
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    Kind kind = configuration.getTargetKind();
    return BlazeCommandName.RUN.equals(
            Preconditions.checkNotNull(handlerState).getCommandState().getCommand())
        && kind != null
        && SUPPORTED_BINARIES.contains(kind);
  }

  private static boolean isDebugging(ExecutionEnvironment environment) {
    Executor executor = environment.getExecutor();
    return executor instanceof DefaultDebugExecutor;
  }
}
