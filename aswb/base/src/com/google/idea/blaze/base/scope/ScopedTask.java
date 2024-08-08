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

import com.google.idea.blaze.base.async.executor.ProgressiveWithResult;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.intellij.openapi.progress.ProgressIndicator;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Wrapper between an IntelliJ Task and a BlazeContext */
public abstract class ScopedTask<T> implements ProgressiveWithResult<T> {
  @Nullable final BlazeContext parentContext;

  public ScopedTask() {
    this(/* parentContext= */ null);
  }

  public ScopedTask(@Nullable BlazeContext parentContext) {
    this.parentContext = parentContext;
  }

  @Override
  public T compute(@NotNull final ProgressIndicator indicator) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new ProgressIndicatorScope(indicator));
          return ScopedTask.this.execute(context);
        });
  }

  protected abstract T execute(@NotNull BlazeContext context);
}
