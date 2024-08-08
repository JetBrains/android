/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.logging;

import com.google.auto.value.AutoValue;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Provider of directories which should be logged. Plugins should specify any directories they
 * introduce to store data or caches.
 */
public interface LoggedDirectoryProvider {

  /** A directory which should be included in our metrics. */
  @AutoValue
  abstract class LoggedDirectory {
    /** The path of the directory. */
    public abstract Path path();
    /** An identifying name for the IDE part which introduces and manages this directory. */
    public abstract String originatingIdePart();
    /** The general purpose of this directory in the IDE context. */
    public abstract String purpose();

    public static Builder builder() {
      return new AutoValue_LoggedDirectoryProvider_LoggedDirectory.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setPath(Path value);

      public abstract Builder setOriginatingIdePart(String value);

      public abstract Builder setPurpose(String value);

      public abstract LoggedDirectory build();
    }
  }

  ExtensionPointName<LoggedDirectoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.LoggedDirectoryProvider");

  /**
   * Provides a directory for which metrics should be logged.
   *
   * <p>The returned value may change over the lifetime of the IDE. An empty {@link Optional}
   * signifies that the directory is currently not available. It's still acceptable to return a
   * directory whose existence on disk is not guaranteed.
   *
   * @param project the project context (if this is relevant)
   * @return the directory to log metrics for
   */
  Optional<LoggedDirectory> getLoggedDirectory(Project project);
}
