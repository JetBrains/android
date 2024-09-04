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
package com.google.idea.blaze.qsync.project;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Label;
import java.util.Optional;

/**
 * A build target that's included in the project.
 *
 * <p>This class augments {@link BuildTarget} which is shared with legacy sync, adding extra data
 * required by query sync.
 */
@AutoValue
public abstract class ProjectTarget implements BuildTarget {

  /** The type of a source file within {@link #sourceLabels()}. */
  public enum SourceType {
    /**
     * Regular sources, i.e. a java or kotlin file for a java target, a c or c++ file for a cc
     * target, etc.
     */
    REGULAR,
    ANDROID_RESOURCES,
    ANDROID_MANIFEST,
    CC_HEADERS;

    /** Helper method to improve code readability when passing an array of source types to match. */
    public static SourceType[] all() {
      return values();
    }
  }

  @Override
  public abstract Label label();

  @Override
  public abstract String kind();

  /** All the dependencies of a rule. */
  public abstract ImmutableSet<Label> deps();

  /** All the runtime dependencies of a java rule. */
  public abstract ImmutableSet<Label> runtimeDeps();

  public abstract ImmutableSetMultimap<SourceType, Label> sourceLabels();

  public abstract Optional<Label> testApp();

  public abstract Optional<Label> instruments();

  public abstract Optional<String> customPackage();

  public abstract ImmutableList<String> copts();

  public abstract ImmutableSet<QuerySyncLanguage> languages();

  public abstract ImmutableList<String> tags();

  public abstract Optional<String> mainClass();

  public static Builder builder() {
    return new AutoValue_ProjectTarget.Builder();
  }

  /** Builder for {@link ProjectTarget}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder label(Label label);

    public abstract Builder kind(String kind);

    public abstract ImmutableSet.Builder<Label> depsBuilder();

    public abstract ImmutableSet.Builder<Label> runtimeDepsBuilder();

    public abstract ImmutableSetMultimap.Builder<SourceType, Label> sourceLabelsBuilder();

    public abstract ImmutableList.Builder<String> coptsBuilder();

    public abstract Builder testApp(Label testApp);

    public abstract Builder instruments(Label instruments);

    public abstract Builder customPackage(String customPackage);

    public abstract ImmutableSet.Builder<QuerySyncLanguage> languagesBuilder();

    public abstract Builder tags(Iterable<String> tags);

    public abstract Builder mainClass(String mainClass);

    public abstract ProjectTarget build();
  }
}
