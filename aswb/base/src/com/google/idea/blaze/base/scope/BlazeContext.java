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
package com.google.idea.blaze.base.scope;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/** Scoped operation context. */
public class BlazeContext implements Context<BlazeContext>, AutoCloseable {

  private static final Logger logger = Logger.getInstance(BlazeContext.class);

  @Nullable private BlazeContext parentContext;

  private final List<Scope<? super BlazeContext>> scopes = Lists.newArrayList();

  // List of all active child contexts.
  private final List<BlazeContext> childContexts =
      Collections.synchronizedList(Lists.newArrayList());

  private final ListMultimap<Class<? extends Output>, OutputSink<?>> outputSinks =
      ArrayListMultimap.create();

  private final List<Runnable> cancellationHandlers =
      Collections.synchronizedList(Lists.newArrayList());

  private boolean isEnding;
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private int holdCount;
  private boolean hasErrors;
  private boolean hasWarnings;
  private boolean propagatesErrors = true;

  private BlazeContext(@Nullable BlazeContext parentContext) {
    this.parentContext = parentContext;
  }

  public static BlazeContext create() {
    return new BlazeContext(null);
  }

  @MustBeClosed
  public static BlazeContext create(BlazeContext parentContext) {
    BlazeContext context = new BlazeContext(parentContext);
    if (parentContext != null) {
      parentContext.addChildContext(context);
    }
    return context;
  }

  @CanIgnoreReturnValue
  @Override
  public BlazeContext push(Scope<? super BlazeContext> scope) {
    scopes.add(scope);
    scope.onScopeBegin(this);
    return this;
  }

  /** Ends the context scope. */
  @Override
  public void close() {
    if (isEnding || holdCount > 0) {
      return;
    }
    isEnding = true;
    for (int i = scopes.size() - 1; i >= 0; i--) {
      scopes.get(i).onScopeEnd(this);
    }

    if (parentContext != null) {
      parentContext.removeChildContext(this);
      if (hasErrors && propagatesErrors) {
        parentContext.setHasError();
      }
      if (hasWarnings && propagatesErrors) {
        parentContext.setHasWarnings();
      }
    }
  }

  /**
   * Requests cancellation of the operation.
   *
   * <p>Each context holder must handle cancellation individually.
   */
  public void setCancelled() {

    synchronized (this) {
      if (isEnding || isCancelled.get()) {
        return;
      }
      isCancelled.set(true);
    }

    synchronized (cancellationHandlers) {
      for (Runnable handler : cancellationHandlers) {
        handler.run();
      }
    }

    if (parentContext != null) {
      parentContext.setCancelled();
    }

    synchronized (childContexts) {
      for (BlazeContext childContext : childContexts) {
        childContext.setCancelled();
      }
    }
  }

  public void hold() {
    ++holdCount;
  }

  public void release() {
    if (--holdCount == 0) {
      close();
    }
  }

  public boolean isEnding() {
    return isEnding;
  }

  @Override
  public boolean isCancelled() {
    return isCancelled.get();
  }

  @Nullable
  @Override
  public <T extends Context.Scope<?>> T getScope(Class<T> scopeClass) {
    return getScope(scopeClass, scopes.size());
  }

  @Nullable
  private <T extends Context.Scope<?>> T getScope(Class<T> scopeClass, int endIndex) {
    for (int i = endIndex - 1; i >= 0; i--) {
      if (scopes.get(i).getClass() == scopeClass) {
        return scopeClass.cast(scopes.get(i));
      }
    }
    if (parentContext != null) {
      return parentContext.getScope(scopeClass);
    }
    return null;
  }

  @Nullable
  public <T extends BlazeScope> T getParentScope(T scope) {
    int index = scopes.indexOf(scope);
    if (index == -1) {
      throw new IllegalArgumentException("Scope does not belong to this context.");
    }
    @SuppressWarnings("unchecked")
    Class<T> scopeClass = (Class<T>) scope.getClass();
    return getScope(scopeClass, index);
  }

  /**
   * Find all instances of {@param scopeClass} that are on the stack starting with this context.
   * That includes this context and all parent contexts recursively.
   *
   * @param scopeClass type of scopes to locate
   * @return The ordered list of all scopes of type {@param scopeClass}, ordered from {@param
   *     startingScope} to the root.
   */
  @VisibleForTesting
  <T extends Scope<?>> List<T> getScopes(Class<T> scopeClass) {
    List<T> scopesCollector = Lists.newArrayList();
    getScopes(scopesCollector, scopeClass, scopes.size());
    return scopesCollector;
  }

  /**
   * Find all instances of {@param scopeClass} that are above {@param startingScope} on the stack.
   * That includes this context and all parent contexts recursively. {@param startingScope} must be
   * in the this {@link BlazeContext}.
   *
   * @param scopeClass type of scopes to locate
   * @param startingScope scope to start our search from
   * @return If {@param startingScope} is in this context, the ordered list of all scopes of type
   *     {@param scopeClass}, ordered from {@param startingScope} to the root. Otherwise, an empty
   *     list.
   */
  @VisibleForTesting
  <T extends Scope<?>> List<T> getScopes(Class<T> scopeClass, Scope<?> startingScope) {
    List<T> scopesCollector = Lists.newArrayList();
    int index = scopes.indexOf(startingScope);
    if (index == -1) {
      return scopesCollector;
    }

    // index + 1 so we include startingScope
    getScopes(scopesCollector, scopeClass, index + 1);
    return scopesCollector;
  }

  /** Add matching scopes to {@param scopesCollector}. Search from {@param maxIndex} - 1 to 0. */
  @VisibleForTesting
  <T extends Scope<?>> void getScopes(List<T> scopesCollector, Class<T> scopeClass, int maxIndex) {
    for (int i = maxIndex - 1; i >= 0; --i) {
      Scope<?> scope = scopes.get(i);
      if (scopeClass.isInstance(scope)) {
        scopesCollector.add(scopeClass.cast(scope));
      }
    }
    if (parentContext != null) {
      parentContext.getScopes(scopesCollector, scopeClass, parentContext.scopes.size());
    }
  }

  @CanIgnoreReturnValue
  public <T extends Output> BlazeContext addOutputSink(
      Class<T> outputClass, OutputSink<T> outputSink) {
    outputSinks.put(outputClass, outputSink);
    return this;
  }

  /** Produces output by sending it to any registered sinks. */
  @SuppressWarnings("unchecked")
  @Override
  public synchronized <T extends Output> void output(T output) {
    Class<? extends Output> outputClass = output.getClass();
    List<OutputSink<?>> outputSinks = this.outputSinks.get(outputClass);

    boolean continuePropagation = true;
    for (int i = outputSinks.size() - 1; i >= 0; --i) {
      OutputSink<?> outputSink = outputSinks.get(i);
      OutputSink.Propagation propagation = ((OutputSink<T>) outputSink).onOutput(output);
      continuePropagation = propagation == OutputSink.Propagation.Continue;
      if (!continuePropagation) {
        break;
      }
    }
    if (continuePropagation && parentContext != null) {
      parentContext.output(output);
    }
  }

  /**
   * Sets the error state.
   *
   * <p>The error state will be propagated to any parents.
   */
  @Override
  public void setHasError() {
    this.hasErrors = true;
  }

  @Override
  public void setHasWarnings() {
    hasWarnings = true;
  }

  /** Returns true if there were errors */
  public boolean hasErrors() {
    return hasErrors;
  }

  public boolean hasWarnings() {
    return hasWarnings;
  }

  public boolean isRoot() {
    return parentContext == null;
  }

  /** Returns true if no errors and isn't cancelled. */
  public boolean shouldContinue() {
    return !hasErrors() && !isCancelled();
  }

  /** Sets whether errors are propagated to the parent context. */
  public void setPropagatesErrors(boolean propagatesErrors) {
    this.propagatesErrors = propagatesErrors;
  }

  /** Registers a function to be called if the context is cancelled */
  @Override
  public void addCancellationHandler(Runnable handler) {
    this.cancellationHandlers.add(handler);
  }

  private void addChildContext(BlazeContext childContext) {
    this.childContexts.add(childContext);
  }

  private void removeChildContext(BlazeContext childContext) {
    this.childContexts.remove(childContext);
  }

  public SyncResult getSyncResult() {
    if (shouldContinue()) {
      throw new IllegalStateException("Sync result requested for ongoing context");
    }
    if (isCancelled()) {
      return SyncResult.CANCELLED;
    }
    if (hasErrors()) {
      return SyncResult.FAILURE;
    }
    return SyncResult.SUCCESS;
  }

  /**
   * Log & display a message to the user when a user-initiated action fails.
   *
   * @param description A user readable failure message, including the high level IDE operation that
   *     failed.
   * @param t The exception that caused the failure.
   */
  public void handleException(String description, Throwable t) {
    if (handleExceptionInternal(description, t)) {
      setHasError();
    }
  }

  public void handleExceptionAsWarning(String description, Throwable t) {
    if (handleExceptionInternal(description, t)) {
      setHasWarnings();
    }
  }

  private static Optional<String> getCauseString(Throwable t) {
    if (t instanceof ExecutionException) {
      // Since we're showing a cause summary to the user, exclude ExecutionException since they
      // don't add any useful information.
      return Optional.empty();
    }
    if (t.getMessage() != null) {
      return Optional.of(t.getMessage() + " (" + t.getClass().getName() + ")");
    } else {
      return Optional.of(t.getClass().getName());
    }
  }

  private boolean handleExceptionInternal(String description, Throwable t) {
    if (t instanceof CancellationException
        || t instanceof ProcessCanceledException
        || isUserCancelledBuild(t)) {
      logger.info(description + ": cancelled.", t);
      output(PrintOutput.error("Operation cancelled by user."));
      setCancelled();
      return false;
    } else if (isExceptionError(t)) {
      logger.error(description, t);
      output(PrintOutput.error(description));
      for (Throwable cause = t; cause != null; cause = cause.getCause()) {
        getCauseString(cause)
            .ifPresent(message -> output(PrintOutput.error("  because: " + message)));
      }

    } else {
      logger.info(description, t);
      if (t.getMessage() != null) {
        output(PrintOutput.error(t.getMessage()));
      }
    }
    return true;
  }

  private boolean isExceptionError(Throwable e) {
    if (e instanceof BuildException) {
      return ((BuildException) e).isIdeError();
    }
    return true;
  }

  private boolean isUserCancelledBuild(Throwable e) {
    return this.isCancelled() && (e instanceof BuildException);
  }
}
