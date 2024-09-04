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
package com.google.idea.blaze.qsync.query;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class QuerySummaryTestBuilder {

  private final ImmutableList.Builder<Label> packagesBuilder = ImmutableList.builder();
  private final ImmutableSet.Builder<Label> buildFilesWithErrorsBuilder = ImmutableSet.builder();

  private final ImmutableMultimap.Builder<String, String> includesBuilder =
      ImmutableMultimap.builder();

  public QuerySummaryTestBuilder() {}

  @CanIgnoreReturnValue
  public QuerySummaryTestBuilder addPackages(String... packages) {
    stream(packages).map(Label::of).forEach(packagesBuilder::add);
    return this;
  }

  @CanIgnoreReturnValue
  public QuerySummaryTestBuilder addSubincludes(Multimap<String, String> subIncludes) {
    includesBuilder.putAll(subIncludes);
    return this;
  }

  @CanIgnoreReturnValue
  public QuerySummaryTestBuilder addBuildFileLabelsWithErrors(String... buildFilesWithErrors) {
    stream(buildFilesWithErrors).map(Label::of).forEach(buildFilesWithErrorsBuilder::add);
    return this;
  }

  public Query.Summary build() {
    ImmutableList<Label> packages = packagesBuilder.build();
    ImmutableSet<Label> buildFilesWithErrors = buildFilesWithErrorsBuilder.build();
    ImmutableMultimap<String, String> includes = includesBuilder.build();
    Set<Label> sourceFiles =
        packages.stream()
            .map(Label::getPackage)
            .map(p -> Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, p, Path.of("BUILD")))
            .collect(toCollection(HashSet::new));
    includes.keySet().stream().map(Label::of).forEach(sourceFiles::add);

    QuerySummary.Builder builder = QuerySummary.newBuilder();
    builder.putAllRules(
      packages.stream().filter(l -> !buildFilesWithErrors.contains(l.siblingWithName("BUILD")))
        .collect(toImmutableMap(l -> Label.of(l.toString()), l -> Query.Rule.newBuilder().setRuleClass("java_library").build())));
    builder.putAllSourceFiles(
      sourceFiles.stream().collect(toImmutableMap(src -> src, src -> SourceFile.newBuilder()
        .setLocation(src + ":1:1")
        .addAllSubinclude(includes.get(src.toString()))
        .build()))
    );

    builder.putAllPackagesWithErrors(buildFilesWithErrors.stream().map(Label::getPackage).collect(toImmutableSet()));

    return builder.build().protoForSerializationOnly();
  }
}
