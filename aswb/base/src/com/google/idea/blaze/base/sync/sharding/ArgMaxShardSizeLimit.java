/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import com.google.common.base.Suppliers;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayOutputStream;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A {@link TargetShardSizeLimit} using heuristics to keep blaze invocations below system ARG_MAX.
 */
final class ArgMaxShardSizeLimit implements TargetShardSizeLimit {
  private static final Logger logger = Logger.getInstance(ArgMaxShardSizeLimit.class);

  private static final BoolExperiment enabled =
      new BoolExperiment("blaze.shard.use.arg.max.heuristic", true);

  private final Supplier<Integer> systemArgMax =
      Suppliers.memoize(ArgMaxShardSizeLimit::queryArgMax);

  @Override
  public OptionalInt getShardSizeLimit() {
    if (!enabled.getValue()) {
      return OptionalInt.empty();
    }
    Integer argMax = systemArgMax.get();
    if (argMax == null || argMax <= 0) {
      return OptionalInt.empty();
    }
    // a very rough heuristic with fixed, somewhat arbitrary env size and average target size
    int envSizeBytes = 20000;
    int targetStringSizeBytes = 150;
    return OptionalInt.of((argMax - envSizeBytes) / targetStringSizeBytes);
  }

  /** Synchronously runs 'getconf ARG_MAX', returning null if unsuccessful. */
  @Nullable
  private static Integer queryArgMax() {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    int retVal =
        ExternalTask.builder()
            .args("getconf", "ARG_MAX")
            .stdout(stdout)
            .stderr(stderr)
            .build()
            .run();
    if (retVal != 0) {
      logger.warn("Failed to query ARG_MAX: " + stderr);
      return null;
    }
    String out = stdout.toString().trim();
    try {
      return Integer.parseInt(out);
    } catch (NumberFormatException e) {
      logger.warn("Failed to parse ARG_MAX. Stdout: " + out);
      return null;
    }
  }
}
