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
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;

import com.google.auto.value.AutoValue;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Test utility for building {@link GraphToProjectConverter} instances */
@AutoValue
abstract class GraphToProjectConvertersForTests {
  public abstract PackageReader packageReader();

  public abstract PackageReader.ParallelReader parallelPackageReader();

  public abstract Predicate<Path> fileExistenceCheck();

  public abstract ImmutableSet<Path> projectIncludes();

  public abstract ImmutableSet<Path> projectExcludes();

  public abstract ImmutableSet<QuerySyncLanguage> languageClasses();

  public abstract ImmutableSet<String> testSources();

  public abstract ImmutableSet<Path> systemExcludes();

  static Builder builder() {
    return new AutoValue_GraphToProjectConvertersForTests.Builder()
        .setPackageReader(EMPTY_PACKAGE_READER)
        .setParallelPackageReader(SIMPLE_PARALLEL_PACKAGE_READER)
        .setFileExistenceCheck(Predicates.alwaysTrue())
        .setLanguageClasses(ImmutableSet.of())
        .setProjectIncludes(ImmutableSet.of())
        .setProjectExcludes(ImmutableSet.of())
        .setTestSources(ImmutableSet.of())
        .setSystemExcludes(ImmutableSet.of());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setPackageReader(PackageReader value);

    abstract Builder setParallelPackageReader(PackageReader.ParallelReader value);

    abstract Builder setFileExistenceCheck(Predicate<Path> value);

    abstract Builder setProjectIncludes(ImmutableSet<Path> value);

    public abstract Builder setProjectExcludes(ImmutableSet<Path> value);

    abstract Builder setLanguageClasses(ImmutableSet<QuerySyncLanguage> languages);

    abstract Builder setTestSources(ImmutableSet<String> value);

    abstract Builder setSystemExcludes(ImmutableSet<Path> value);

    abstract GraphToProjectConvertersForTests autoBuild();

    public GraphToProjectConverter build() {
      GraphToProjectConvertersForTests info = autoBuild();
      return new GraphToProjectConverter(
          info.packageReader(),
          new PackageReader.ParallelReader.SingleThreadedForTests(),
          v -> info.fileExistenceCheck().test(v),
          NOOP_CONTEXT,
          ProjectDefinition.builder()
              .setProjectIncludes(info.projectIncludes())
              .setProjectExcludes(info.projectExcludes())
              .setTargetPatterns(ImmutableList.of())
              .setLanguageClasses(info.languageClasses())
              .setTestSources(info.testSources())
              .setSystemExcludes(info.systemExcludes())
              .build(),
          newDirectExecutorService());
    }
  }
}
