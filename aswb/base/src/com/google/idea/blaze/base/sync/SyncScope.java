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
package com.google.idea.blaze.base.sync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Helper methods to run scoped sync operations throwing checked exceptions. */
public final class SyncScope {

  private static final Logger logger = Logger.getInstance(SyncScope.class);

  private SyncScope() {}

  /** Checked exception representing a failed sync action. */
  public static class SyncFailedException extends Exception {
    public SyncFailedException() {}

    public SyncFailedException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Checked exception representing a cancelled sync action. */
  public static class SyncCanceledException extends Exception {}

  interface ScopedSyncOperation {
    void execute(BlazeContext context) throws SyncFailedException, SyncCanceledException;
  }

  interface ScopedSyncFunction<T> {
    T execute(BlazeContext context) throws SyncFailedException, SyncCanceledException;
  }

  /**
   * Runs a scoped operation with the given {@link TimingScope} attached, returning the
   * corresponding list of timed events.
   */
  static List<TimedEvent> runWithTiming(
      @Nullable BlazeContext parentContext,
      ScopedSyncOperation operation,
      TimingScope timingScope) {
    List<TimedEvent> timedEvents = new ArrayList<>();
    ScopedSyncOperation withTiming =
        context -> {
          timingScope.addScopeListener((events, totalTime) -> timedEvents.addAll(events));
          context.push(timingScope);
          operation.execute(context);
        };
    push(parentContext, withTiming);
    return timedEvents;
  }

  /**
   * Runs a scoped operation in a new nested scope, handling checked sync exceptions appropriately.
   */
  static void push(@Nullable BlazeContext parentContext, ScopedSyncOperation scopedOperation) {
    push(
        parentContext,
        context -> {
          scopedOperation.execute(context);
          return null;
        });
  }

  /**
   * Runs a scoped operation in a new nested scope, handling checked sync exceptions appropriately.
   *
   * <p>Returns null if the {@link ScopedSyncFunction} throws a {@link SyncFailedException} or
   * {@link SyncCanceledException}.
   */
  @Nullable
  static <T> T push(@Nullable BlazeContext parentContext, ScopedSyncFunction<T> scopedOperation) {
    BlazeContext context = BlazeContext.create(parentContext);
    try {
      return scopedOperation.execute(context);
    } catch (SyncCanceledException e) {
      context.setCancelled();
      return null;
    } catch (SyncFailedException e) {
      context.setHasError();
      return null;
    } catch (ProcessCanceledException e) {
      context.setCancelled();
      throw e;
    } catch (Throwable e) {
      context.setHasError();
      logger.error(e);
      throw e;
    } finally {
      context.close();
    }
  }
}
