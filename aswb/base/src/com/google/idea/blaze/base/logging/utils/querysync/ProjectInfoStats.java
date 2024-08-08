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
package com.google.idea.blaze.base.logging.utils.querysync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import java.nio.file.Path;

/** The basic information to track of a query sync project. */
@AutoValue
public abstract class ProjectInfoStats {
  private static final ProjectInfoStats EMPTY =
      new AutoValue_ProjectInfoStats.Builder()
          .setBlazeProjectFiles(ImmutableSet.of())
          .setLanguagesActive(ImmutableSet.of())
          .setProjectTargetCount(0)
          .setExternalDependencyCount(0)
          .build();

  public abstract ImmutableSet<Path> blazeProjectFiles();

  public abstract ImmutableSet<QuerySyncLanguage> languagesActive();

  public abstract int projectTargetCount();

  public abstract int externalDependencyCount();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  /** Auto value builder for ProjectInfoStats. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBlazeProjectFiles(ImmutableSet<Path> value);

    public abstract Builder setLanguagesActive(ImmutableSet<QuerySyncLanguage> value);

    public abstract Builder setProjectTargetCount(int targetCount);

    public abstract Builder setExternalDependencyCount(int depsCount);

    public abstract ProjectInfoStats build();
  }
}
