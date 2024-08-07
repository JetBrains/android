/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.psi.PsiElement;

/**
 * Used when we don't yet know all the configuration details, but want to provide a 'run/debug'
 * context action anyway.
 *
 * <p>This is necessary whenever details are expensive to calculate (e.g. involve searching for a
 * blaze target, or resolving PSI elements), because run configurations are set up on the EDT.
 */
public interface PendingRunConfigurationContext extends RunConfigurationContext {

  /** Used to indicate that a pending run configuration couldn't be successfully set up. */
  class NoRunConfigurationFoundException extends ExecutionException {

    public NoRunConfigurationFoundException(String s) {
      super(s);
    }
  }

  /**
   * A result from a {@link PendingRunConfigurationContext}, indicating that no run configuration
   * was found for this context.
   */
  class FailedPendingRunConfiguration implements RunConfigurationContext {
    private final PsiElement psi;
    public final String errorMessage;

    public FailedPendingRunConfiguration(PsiElement psi, String errorMessage) {
      this.psi = psi;
      this.errorMessage = errorMessage;
    }

    @Override
    public PsiElement getSourceElement() {
      return psi;
    }

    @Override
    public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
      return false;
    }

    @Override
    public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
      return false;
    }
  }

  ImmutableSet<ExecutorType> supportedExecutors();

  /**
   * Returns true if this is an asynchronous {@link PendingRunConfigurationContext} that had been
   * resolved in the background. A {@link PendingRunConfigurationContext} that requires user action
   * will always return false until {@link #resolve}d.
   */
  boolean isDone();

  /**
   * Finish resolving the {@link PendingRunConfigurationContext}. Called when the user actually
   * tries to run the configuration. Block with a progress message if necessary.
   *
   * @param config will be updated if {@link PendingRunConfigurationContext} is resolved
   *     successfully.
   * @param rerun will be called after resolving is finished to continue running the real
   *     configuration.
   */
  void resolve(ExecutionEnvironment env, BlazeCommandRunConfiguration config, Runnable rerun)
      throws ExecutionException;
}
