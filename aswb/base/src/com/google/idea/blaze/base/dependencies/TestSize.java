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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.Nullable;

/** The "size" attribute from test rules */
public enum TestSize implements ProtoWrapper<String> {
  SMALL("small"),
  MEDIUM("medium"),
  LARGE("large"),
  ENORMOUS("enormous");

  // Rules are "medium" test size by default
  public static final TestSize DEFAULT_RULE_TEST_SIZE = TestSize.MEDIUM;
  // Non-annotated methods and classes are "small" by default
  public static final TestSize DEFAULT_NON_ANNOTATED_TEST_SIZE = TestSize.SMALL;

  private static final ImmutableMap<String, TestSize> STRING_TO_SIZE = makeStringToSizeMap();

  private final String name;

  TestSize(String name) {
    this.name = name;
  }

  @Nullable
  public static TestSize fromString(String string) {
    return STRING_TO_SIZE.get(string);
  }

  private static ImmutableMap<String, TestSize> makeStringToSizeMap() {
    ImmutableMap.Builder<String, TestSize> result = ImmutableMap.builder();
    for (TestSize size : TestSize.values()) {
      result.put(size.name, size);
    }
    return result.build();
  }

  @Override
  public String toProto() {
    return name;
  }
}
