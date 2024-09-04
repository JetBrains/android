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
package com.google.idea.blaze.qsync;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.EMPTY_PACKAGE_READER;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;

import com.google.auto.value.AutoValue;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Test utility for building {@link GraphToProjectConverter} instances */
@AutoValue
abstract class GraphToProjectConverters {
  public abstract PackageReader packageReader();

  public abstract Predicate<Path> fileExistenceCheck();

  public abstract ImmutableSet<Path> projectIncludes();

  public abstract ImmutableSet<Path> projectExcludes();

  public abstract ImmutableSet<QuerySyncLanguage> languageClasses();

  public abstract ImmutableSet<String> testSources();

  static Builder builder() {
    return new AutoValue_GraphToProjectConverters.Builder()
        .setPackageReader(EMPTY_PACKAGE_READER)
        .setFileExistenceCheck(Predicates.alwaysTrue())
        .setLanguageClasses(ImmutableSet.of())
        .setProjectIncludes(ImmutableSet.of())
        .setProjectExcludes(ImmutableSet.of())
        .setTestSources(ImmutableSet.of());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setPackageReader(PackageReader value);

    abstract Builder setFileExistenceCheck(Predicate<Path> value);

    abstract Builder setProjectIncludes(ImmutableSet<Path> value);

    public abstract Builder setProjectExcludes(ImmutableSet<Path> value);

    abstract Builder setLanguageClasses(ImmutableSet<QuerySyncLanguage> languages);

    abstract Builder setTestSources(ImmutableSet<String> value);

    abstract GraphToProjectConverters autoBuild();

    public GraphToProjectConverter build() {
      GraphToProjectConverters info = autoBuild();
      return new GraphToProjectConverter(
          info.packageReader(),
          info.fileExistenceCheck(),
          NOOP_CONTEXT,
          ProjectDefinition.create(
              info.projectIncludes(),
              info.projectExcludes(),
              info.languageClasses(),
              info.testSources()),
          newDirectExecutorService());
    }
  }
}
