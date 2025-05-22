/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Summaries the output from a {@code query} invocation into just the data needed by the rest of
 * querysync.
 *
 * <p>The main purpose of the summarized output is to allow the outputs from multiple {@code query}
 * invocations to be combined. This enables delta updates to the project.
 */
public interface QuerySummary {
  /**
   * Returns the map of source files included in the query output.
   *
   * <p>This is a map of source target label to the {@link QueryData.SourceFile} proto representing it.
   */
  ImmutableMap<Label, QueryData.SourceFile> getSourceFilesMap();

  /**
   * Returns the map of rules included in the query output.
   *
   * <p>This is a map of rule label to the {@link QueryData.Rule} proto representing it.
   */
  ImmutableMap<Label, QueryData.Rule> getRulesMap();

  /**
   * Returns the set of build packages in the query output.
   *
   * <p>The packages are workspace relative paths that contain a BUILD file.
   */
  PackageSet getPackages();

  /**
   * Returns a map of .bzl file labels to BUILD file labels that include them.
   *
   * <p>This is used to determine, for example, which build files include a given .bzl file.
   */
  ImmutableMultimap<Path, Path> getReverseSubincludeMap();

  /**
   * Returns the set of labels of all files includes from BUILD files.
   */
  ImmutableSet<Label> getAllBuildIncludedFiles();

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  Optional<Path> getParentPackage(Path buildPackage);

  /**
   * Returns the list of packages that the query sync was unable to fetch or fetched with errors.
   */
  ImmutableSet<Path> getPackagesWithErrors();

  /**
   * Returns the number of packages that the query sync was unable to fetch or fetched with errors.
   */
  int getPackagesWithErrorsCount();

  /**
   * Returns the number of currently loaded rules in the project scope.
   */
  int getRulesCount();

  /**
   * Returns the configured query strategy.
   */
  QuerySpec.QueryStrategy getQueryStrategy();

  boolean isCompatibleWithCurrentPluginVersion();
  Query.Summary protoForSerializationOnly();

  QuerySummary EMPTY = new QuerySummary() {
    @Override
    public ImmutableMap<Label, QueryData.SourceFile> getSourceFilesMap() {
      return ImmutableMap.of();
    }

    @Override
    public ImmutableMap<Label, QueryData.Rule> getRulesMap() {
      return ImmutableMap.of();
    }

    @Override
    public PackageSet getPackages() {
      return PackageSet.EMPTY;
    }

    @Override
    public ImmutableMultimap<Path, Path> getReverseSubincludeMap() {
      return ImmutableMultimap.of();
    }

    @Override
    public ImmutableSet<Label> getAllBuildIncludedFiles() {
      return ImmutableSet.of();
    }

    @Override
    public Optional<Path> getParentPackage(Path buildPackage) {
      return Optional.empty();
    }

    @Override
    public ImmutableSet<Path> getPackagesWithErrors() {
      return ImmutableSet.of();
    }

    @Override
    public int getPackagesWithErrorsCount() {
      return 0;
    }

    @Override
    public int getRulesCount() {
      return 0;
    }

    @Override
    public QuerySpec.QueryStrategy getQueryStrategy() {
      return QuerySpec.QueryStrategy.PLAIN;
    }

    @Override
    public boolean isCompatibleWithCurrentPluginVersion() {
      return true;
    }

    @Override
    public Query.Summary protoForSerializationOnly() {
      return Query.Summary.getDefaultInstance();
    }
  };
}
