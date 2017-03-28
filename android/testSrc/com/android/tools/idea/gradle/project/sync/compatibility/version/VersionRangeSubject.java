/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.truth.Truth.assertThat;

public class VersionRangeSubject extends Subject<VersionRangeSubject, VersionRange> {
  @NotNull
  public static SubjectFactory<VersionRangeSubject, VersionRange> versionRange() {
    return new SubjectFactory<VersionRangeSubject, VersionRange>() {
      @Override
      public VersionRangeSubject getSubject(FailureStrategy failureStrategy, VersionRange versionRange) {
        return new VersionRangeSubject(failureStrategy, versionRange);
      }
    };
  }

  private VersionRangeSubject(FailureStrategy failureStrategy,
                                @Nullable VersionRange versionRange) {
    super(failureStrategy, versionRange);
  }

  @NotNull
  public VersionRangeSubject hasMinVersion(@NotNull String expected) {
    assertThat(getSubject().getMinVersion()).named("min version").isEqualTo(expected);
    return this;
  }

  @NotNull
  public VersionRangeSubject hasMaxVersion(@Nullable String expected) {
    assertThat(getSubject().getMaxVersion()).named("max version").isEqualTo(expected);
    return this;
  }

  @NotNull
  public VersionRangeSubject isMinVersionInclusive(boolean expected) {
    assertThat(getSubject().isMinVersionInclusive()).named("min version inclusive").isEqualTo(expected);
    return this;
  }

  @NotNull
  public VersionRangeSubject isMaxVersionInclusive(boolean expected) {
    assertThat(getSubject().isMaxVersionInclusive()).named("max version inclusive").isEqualTo(expected);
    return this;
  }

  @NotNull
  public VersionRangeSubject contains(@NotNull String... versions) {
    for (String version : versions) {
      assertThat(getSubject().contains(version)).named("contains " + version).isEqualTo(true);
    }
    return this;
  }

  @NotNull
  public VersionRangeSubject doesNotContain(@NotNull String... versions) {
    for (String version : versions) {
      assertThat(getSubject().contains(version)).named("does not contain " + version).isEqualTo(false);
    }
    return this;
  }

  @NotNull
  public VersionRangeSubject hasDescription(@Nullable String expected) {
    assertThat(getSubject().getDescription()).named("description").isEqualTo(expected);
    return this;
  }
}
