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

import com.google.common.collect.Streams;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.OptionalInt;

/** An extension point for limiting the number of targets per blaze build shard. */
public interface TargetShardSizeLimit {
  ExtensionPointName<TargetShardSizeLimit> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TargetShardSizeLimit");

  /**
   * Returns the maximum number of blaze targets per shard for this limit.
   *
   * <p>If {@link OptionalInt#empty()}, this limit is not enforced.
   */
  OptionalInt getShardSizeLimit();

  /**
   * Returns the maximum number of blaze targets allowed per shard.
   *
   * <p>This is simply the smallest applicable shard size limit, or {@link OptionalInt#empty()} if
   * there are no limits.
   */
  static OptionalInt getMaxTargetsPerShard() {
    return EP_NAME
        .extensions()
        .map(TargetShardSizeLimit::getShardSizeLimit)
        .flatMapToInt(Streams::stream)
        .min();
  }
}
