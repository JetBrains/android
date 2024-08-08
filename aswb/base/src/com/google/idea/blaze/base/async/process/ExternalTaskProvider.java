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
package com.google.idea.blaze.base.async.process;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.async.process.ExternalTask.Builder;
import com.google.idea.blaze.base.async.process.ExternalTask.ExternalTaskImpl;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Constructs an {@link ExternalTask} from a builder instance. This indirection exists to allow easy
 * redirection in blaze-invoking integration tests.
 */
@VisibleForTesting
public interface ExternalTaskProvider {

  static ExternalTaskProvider getInstance() {
    return ApplicationManager.getApplication().getService(ExternalTaskProvider.class);
  }

  ExternalTask build(ExternalTask.Builder builder);

  /** Default implementation returning an {@link ExternalTaskImpl}. */
  class Impl implements ExternalTaskProvider {
    @Override
    public ExternalTask build(Builder builder) {
      return new ExternalTaskImpl(
          builder.context,
          builder.workingDirectory,
          builder.command.build(),
          builder.environmentVariables,
          builder.stdout,
          builder.stderr,
          builder.redirectErrorStream,
          builder.ignoreExitCode);
    }
  }
}
