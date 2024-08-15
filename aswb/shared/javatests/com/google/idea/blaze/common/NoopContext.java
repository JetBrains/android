/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

/** A basic, non-functional {@link Context} implementation for use in tests. */
public class NoopContext implements Context<NoopContext> {

  @Override
  public NoopContext push(Scope<? super NoopContext> scope) {
    return null;
  }

  @Override
  public <T extends Scope<?>> T getScope(Class<T> scopeClass) {
    return null;
  }

  @Override
  public <T extends Output> void output(T output) {}

  @Override
  public void setHasError() {}

  @Override
  public void setHasWarnings() {}

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public void addCancellationHandler(Runnable runOnCancel) {
  }
}
