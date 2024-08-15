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

import com.google.auto.value.AutoValue;

/**
 * Represents a build target.
 *
 * <p>This interface exists for compatibility with the legacy (aspect) sync only.
 */
public interface BuildTarget {
  Label label();

  String kind();

  static BuildTarget create(Label label, String kind) {
    return new AutoValue_BuildTarget_Impl(label, kind);
  }

  /** Simple autovalue implementation of {@link BuildTarget}. */
  @AutoValue
  abstract class Impl implements BuildTarget {
    @Override
    public abstract Label label();

    @Override
    public abstract String kind();
  }
}
