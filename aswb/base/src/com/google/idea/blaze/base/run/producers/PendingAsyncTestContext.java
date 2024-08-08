/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.PendingRunConfigurationContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.UIUtil;
import javax.annotation.Nullable;

/**
 * For situations where we appear to be in a recognized test context, but can't efficiently resolve
 * the psi elements and/or relevant blaze target.
 *
 * <p>A {@link BlazeCommandRunConfiguration} will be produced synchronously, then filled in later
 * when the full context is known.
 */
class PendingAsyncTestContext extends TestContext implements PendingRunConfigurationContext {
  private static final Logger logger = Logger.getInstance(PendingAsyncTestContext.class);

  static PendingAsyncTestContext fromTargetFuture(
      ImmutableSet<ExecutorType> supportedExecutors,
      ListenableFuture<TargetInfo> target,
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    Project project = sourceElement.getProject();
    String buildSystem = Blaze.buildSystemName(project);
    String progressMessage = String.format("Searching for %s target", buildSystem);
    ListenableFuture<RunConfigurationContext> future =
        Futures.transform(
            target,
            t -> {
              if (t == null) {
                return new FailedPendingRunConfiguration(
                    sourceElement, String.format("No %s target found.", buildSystem));
              }
              RunConfigurationContext context =
                  PendingWebTestContext.findWebTestContext(
                      project, supportedExecutors, t, sourceElement, blazeFlags, description);
              return context != null
                  ? context
                  : new KnownTargetTestContext(t, sourceElement, blazeFlags, description);
            },
            MoreExecutors.directExecutor());
    return new PendingAsyncTestContext(
        supportedExecutors, future, progressMessage, sourceElement, blazeFlags, description);
  }

  private final ImmutableSet<ExecutorType> supportedExecutors;
  private final ListenableFuture<RunConfigurationContext> future;
  private final String progressMessage;

  PendingAsyncTestContext(
      ImmutableSet<ExecutorType> supportedExecutors,
      ListenableFuture<RunConfigurationContext> future,
      String progressMessage,
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    super(sourceElement, blazeFlags, description);
    this.supportedExecutors = supportedExecutors;
    this.future = recursivelyResolveContext(future);
    this.progressMessage = progressMessage;
  }

  @Override
  public ImmutableSet<ExecutorType> supportedExecutors() {
    return supportedExecutors;
  }

  @Override
  public boolean isDone() {
    return future.isDone();
  }

  @Override
  public void resolve(ExecutionEnvironment env, BlazeCommandRunConfiguration config, Runnable rerun)
      throws com.intellij.execution.ExecutionException {
    waitForFutureUnderProgressDialog(env.getProject());
    rerun.run();
  }

  @Override
  boolean setupTarget(BlazeCommandRunConfiguration config) {
    config.setPendingContext(this);
    if (future.isDone()) {
      // set it up synchronously, and return the result
      return doSetupPendingContext(config);
    } else {
      future.addListener(() -> doSetupPendingContext(config), MoreExecutors.directExecutor());
      return true;
    }
  }

  private boolean doSetupPendingContext(BlazeCommandRunConfiguration config) {
    try {
      RunConfigurationContext context = getFutureHandlingErrors();
      boolean success = context.setupRunConfiguration(config);
      if (success) {
        if (config.getPendingContext() == this) {
          // remove this pending context from the config since it is done
          // however, if context became the new pending context, leave it alone
          config.clearPendingContext();
        }
        return true;
      }
    } catch (RunCanceledByUserException | NoRunConfigurationFoundException e) {
      // silently ignore
    } catch (com.intellij.execution.ExecutionException e) {
      logger.warn(e);
    }
    return false;
  }

  @Override
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    if (!future.isDone()) {
      return super.matchesRunConfiguration(config);
    }
    try {
      RunConfigurationContext context = future.get();
      return context.matchesRunConfiguration(config);
    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
      return false;
    }
  }

  @Override
  boolean matchesTarget(BlazeCommandRunConfiguration config) {
    return getSourceElementString().equals(config.getContextElementString());
  }

  /**
   * Returns a future with all currently-unknown details of this configuration context resolved.
   *
   * <p>Handles the case where there are nested {@link PendingAsyncTestContext}s.
   */
  private static ListenableFuture<RunConfigurationContext> recursivelyResolveContext(
      ListenableFuture<RunConfigurationContext> future) {
    return Futures.transformAsync(
        future,
        c ->
            c instanceof PendingAsyncTestContext
                ? recursivelyResolveContext(((PendingAsyncTestContext) c).future)
                : Futures.immediateFuture(c),
        MoreExecutors.directExecutor());
  }

  /**
   * Waits for the run configuration to be configured, displaying a progress dialog if necessary.
   *
   * @throws com.intellij.execution.ExecutionException if the run configuration is not successfully
   *     configured
   */
  private void waitForFutureUnderProgressDialog(Project project)
      throws com.intellij.execution.ExecutionException {
    if (future.isDone()) {
      getFutureHandlingErrors();
    }
    // The progress indicator must be created on the UI thread.
    ProgressWindow indicator =
        UIUtil.invokeAndWaitIfNeeded(
            () ->
                new BackgroundableProcessIndicator(
                    project,
                    progressMessage,
                    PerformInBackgroundOption.ALWAYS_BACKGROUND,
                    "Cancel",
                    "Cancel",
                    /* cancellable= */ true));

    indicator.setIndeterminate(true);
    indicator.start();
    indicator.addStateDelegate(
        new AbstractProgressIndicatorExBase() {
          @Override
          public void cancel() {
            super.cancel();
            future.cancel(true);
          }
        });
    try {
      getFutureHandlingErrors();
    } finally {
      if (indicator.isRunning()) {
        indicator.stop();
        indicator.processFinish();
      }
    }
  }

  private RunConfigurationContext getFutureHandlingErrors()
      throws com.intellij.execution.ExecutionException {
    try {
      RunConfigurationContext result = future.get();
      if (result == null) {
        throw new NoRunConfigurationFoundException("Run configuration setup failed.");
      }
      if (result instanceof FailedPendingRunConfiguration) {
        throw new NoRunConfigurationFoundException(
            ((FailedPendingRunConfiguration) result).errorMessage);
      }
      return result;
    } catch (InterruptedException e) {
      throw new RunCanceledByUserException();
    } catch (java.util.concurrent.ExecutionException e) {
      throw new com.intellij.execution.ExecutionException(e);
    }
  }
}
