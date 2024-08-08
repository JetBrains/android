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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableSet;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;

/** Run configuration executor type */
public enum ExecutorType {
  RUN,
  DEBUG,
  COVERAGE,
  UNKNOWN;

  public static ExecutorType fromExecutor(Executor executor) {
    return fromExecutorId(executor.getId());
  }

  public static ExecutorType fromExecutorId(String executorId) {
    if (executorId.equals(DefaultRunExecutor.EXECUTOR_ID)) {
      return RUN;
    }
    if (executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return DEBUG;
    }
    // hard-code string to avoid plugin dependency (coverage plugin not yet available in CLion)
    if (executorId.equals("Coverage")) {
      return COVERAGE;
    }
    return UNKNOWN;
  }

  public boolean isDebugType() {
    return this.equals(DEBUG);
  }

  /** Executor types supported for debuggable targets. */
  public static final ImmutableSet<ExecutorType> DEBUG_SUPPORTED_TYPES =
      ImmutableSet.of(RUN, DEBUG, COVERAGE);

  /** Executor types supported for non-debuggable targets. */
  public static final ImmutableSet<ExecutorType> DEBUG_UNSUPPORTED_TYPES =
      ImmutableSet.of(RUN, COVERAGE);

  /** Executor types supported for targets supporting fast run/debug. */
  public static final ImmutableSet<ExecutorType> FAST_DEBUG_SUPPORTED_TYPES =
      ImmutableSet.of(RUN, DEBUG, COVERAGE);
}
