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
package com.google.idea.blaze.base.async;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Utilities operating on futures. */
public class FutureUtil {
  /** The result of the future operation */
  public static class FutureResult<T> {
    @Nullable private final T result;
    private final boolean success;
    private final Exception exception;

    FutureResult(T result) {
      this.result = result;
      this.success = true;
      this.exception = null;
    }

    FutureResult(Exception e) {
      this.result = null;
      this.success = false;
      this.exception = e;
    }

    @Nullable
    public T result() {
      return result;
    }

    public boolean success() {
      return success;
    }

    public Exception exception() {
      return exception;
    }
  }

  /** Builder for the future */
  public static class Builder<T> {
    private static final Logger logger = Logger.getInstance(FutureUtil.class);
    private final BlazeContext context;
    private final Future<T> future;
    private String timingCategory;
    private EventType eventType;
    private String errorMessage;
    private String progressMessage;

    Builder(BlazeContext context, Future<T> future) {
      this.context = context;
      this.future = future;
    }

    @CanIgnoreReturnValue
    public Builder<T> timed(String timingCategory, EventType eventType) {
      this.timingCategory = timingCategory;
      this.eventType = eventType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder<T> withProgressMessage(String message) {
      this.progressMessage = message;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder<T> onError(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public FutureResult<T> run() {
      return Scope.push(
          context,
          (childContext) -> {
            if (timingCategory != null) {
              childContext.push(new TimingScope(timingCategory, eventType));
            }
            if (progressMessage != null) {
              childContext.output(new StatusOutput(progressMessage));
            }
            try {
              return new FutureResult<>(future.get());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              context.setCancelled();
              return new FutureResult<>(e);
            } catch (ExecutionException e) {
              logger.error(e);
              if (errorMessage != null) {
                IssueOutput.error(errorMessage).submit(childContext);
              }
              context.setHasError();
              return new FutureResult<>(e);
            }
          });
    }
  }

  public static <T> Builder<T> waitForFuture(BlazeContext context, Future<T> future) {
    return new Builder<>(context, future);
  }
}
