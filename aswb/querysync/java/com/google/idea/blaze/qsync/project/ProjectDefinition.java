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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.query.QuerySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents input the the query sync process. This class contains data that is derived from the
 * user project config and is constructed before the sync begins.
 */
@AutoValue
public abstract class ProjectDefinition {

  public static final ProjectDefinition EMPTY =
      create(
          /* includes= */ ImmutableSet.of(),
          /* excludes= */ ImmutableSet.of(),
          /* languageClasses= */ ImmutableSet.of(),
          /* testSources= */ ImmutableSet.of());

  /**
   * Project includes, also know as root directories. Taken from the users {@code .blazeproject}
   * file. Paths are relative to the workspace root.
   */
  public abstract ImmutableSet<Path> projectIncludes();

  /**
   * Project includes. Taken from the users {@code .blazeproject} file. Paths are relative to the
   * workspace root, and indicate sub-paths from within {@link #projectIncludes()} that are not part
   * of the project.
   */
  public abstract ImmutableSet<Path> projectExcludes();

  public abstract ImmutableSet<QuerySyncLanguage> languageClasses();

  /**
   * Test sources. Taken from the user's {@code .blazeproject} file. Paths are relative to the
   * workspace root, and indicate directories that are considered test sources.
   */
  public abstract ImmutableSet<String> testSources();

  public static ProjectDefinition create(
      ImmutableSet<Path> includes,
      ImmutableSet<Path> excludes,
      ImmutableSet<QuerySyncLanguage> languageClasses,
      ImmutableSet<String> testSources) {
    return new AutoValue_ProjectDefinition(includes, excludes, languageClasses, testSources);
  }

  /**
   * Constructs a query spec from a sync spec. Filters the import roots to those that can be safely
   * queried.
   */
  public QuerySpec deriveQuerySpec(Context<?> context, Path workspaceRoot) throws IOException {
    QuerySpec.Builder result = QuerySpec.builder().workspaceRoot(workspaceRoot);
    for (Path include : projectIncludes()) {
      if (isValidPathForQuery(context, workspaceRoot.resolve(include))) {
        result.includePath(include);
      }
    }
    for (Path exclude : projectExcludes()) {
      if (isValidPathForQuery(context, workspaceRoot.resolve(exclude))) {
        result.excludePath(exclude);
      }
    }
    return result.build();
  }

  /**
   * Determines if a given absolute path is a valid path to query. A path is valid if it contains a
   * BUILD file somewhere within it.
   *
   * <p>Emits warnings via context if any issues are found with the path.
   */
  private static boolean isValidPathForQuery(Context<?> context, Path candidate)
      throws IOException {
    if (Files.exists(candidate.resolve("BUILD"))) {
      return true;
    }
    if (!Files.isDirectory(candidate)) {
      context.output(
          PrintOutput.output(
              "Directory specified in project does not exist or is not a directory: %s",
              candidate));
      return false;
    }
    boolean valid = false;
    try (Stream<Path> stream = Files.list(candidate)) {
      for (Path child : stream.toArray(Path[]::new)) {
        if (Files.isDirectory(child)) {
          boolean validChild = isValidPathForQuery(context, child);
          valid = valid || validChild;
        } else {
          if (child.toString().endsWith(".java") || child.toString().endsWith(".kt")) {
            context.output(
                PrintOutput.log("WARNING: Sources found outside BUILD packages: " + child));
          }
        }
      }
    }
    return valid;
  }

  /** Returns the exclude paths by the include path that they fall within. */
  @Memoized
  public ListMultimap<Path, Path> getExcludesByRootDirectory() {
    ListMultimap<Path, Path> result = ArrayListMultimap.create();
    for (Path exclude : projectExcludes()) {
      projectIncludes().stream()
          .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, exclude))
          .findFirst()
          .ifPresent(foundWorkspacePath -> result.put(foundWorkspacePath, exclude));
    }
    return result;
  }

  public boolean isIncluded(Label target) {
    return isIncluded(target.getPackage());
  }

  public boolean isIncluded(Path workspacePath) {
    return getIncludingContentRoot(workspacePath).isPresent();
  }

  public boolean isExcluded(Path workspacePath) {
    return projectExcludes().stream().anyMatch(workspacePath::startsWith);
  }

  /**
   * Returns the content root containing a workspace-relative path
   *
   * @param workspacePath {@link Path} relative to the workspace
   * @return {@link Optional<Path>} of the content root that contains {@code workspacePath}. Returns
   *     an empty Optional if no content entry contains {@code workspacePath} or if {@code
   *     workspacePath} is contained in an excluded directory.
   */
  public Optional<Path> getIncludingContentRoot(Path workspacePath) {
    Optional<Path> contentRoot =
        projectIncludes().stream().filter(workspacePath::startsWith).findAny();

    if (contentRoot.isEmpty()) {
      return contentRoot;
    }

    if (isExcluded(workspacePath)) {
      // Path is excluded
      return Optional.empty();
    }

    return contentRoot;
  }

  private static boolean isUnderRootDirectory(Path rootDirectory, Path relativePath) {
    // TODO this can probably be cleaned up (or removed?) by using Path API properly.
    if (rootDirectory.toString().equals(".") || rootDirectory.toString().isEmpty()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
        && (relativePath.toString().length() == rootDirectoryString.length()
            || (relativePath.toString().charAt(rootDirectoryString.length()) == '/'));
  }
}
