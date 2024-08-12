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
package com.google.idea.blaze.java.sync.jdeps;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Mock {@link JdepsMap} for testing */
@VisibleForTesting
public final class MockJdepsMap implements JdepsMap {
  Map<TargetKey, List<String>> jdeps = new HashMap<>();

  @Nullable
  @Override
  public List<String> getDependenciesForTarget(TargetKey targetKey) {
    return jdeps.get(targetKey);
  }

  @CanIgnoreReturnValue
  public MockJdepsMap put(TargetKey targetKey, List<String> values) {
    jdeps.put(targetKey, values);
    return this;
  }
}
