/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.common.Label.toLabelList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.RuleKinds;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.BuildGraphData.Location;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.Query.Rule;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class that parses the proto output from a `blaze query --output=streamed_proto` invocation, and
 * yields a {@link BuildGraphData} instance derived from it. Instances of this class are single use.
 */
public class BlazeQueryParser {

  // Rules that will need to be built, whether or not the target is included in the
  // project.
  public static final ImmutableSet<String> ALWAYS_BUILD_RULE_KINDS =
      ImmutableSet.of(
          "java_proto_library",
          "java_lite_proto_library",
          "java_mutable_proto_library",
          // Underlying rule for kt_jvm_lite_proto_library and kt_jvm_proto_library
          "kt_proto_library_helper",
          "_java_grpc_library",
          "_java_lite_grpc_library",
          "kt_grpc_library_helper",
          "java_stubby_library",
          "kt_stubby_library_helper",
          "aar_import",
          "java_import");

  private final Context<?> context;
  private final SetView<String> alwaysBuildRuleKinds;

  private final QuerySummary query;

  private final BuildGraphData.Builder graphBuilder = BuildGraphData.builder();

  private final Set<Label> projectDeps = Sets.newHashSet();
  // All the project targets the aspect needs to build
  private final Set<Label> projectTargetsToBuild = new HashSet<>();
  // An aggregation of all the dependencies of java rules
  private final Set<Label> javaDeps = new HashSet<>();

  public BlazeQueryParser(
      QuerySummary query, Context<?> context, ImmutableSet<String> handledRuleKinds) {
    this.context = context;
    this.alwaysBuildRuleKinds = Sets.difference(ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
    this.query = query;
  }

  public BuildGraphData parse() {
    context.output(PrintOutput.log("Analyzing project structure..."));

    long now = System.nanoTime();

    for (Map.Entry<Label, Query.SourceFile> sourceFileEntry :
        query.getSourceFilesMap().entrySet()) {
      if (sourceFileEntry.getKey().getWorkspaceName().isEmpty()) {
        graphBuilder
            .locationsBuilder()
            .put(sourceFileEntry.getKey(), new Location(sourceFileEntry.getValue().getLocation()));
      } else {
        context.output(
            new PrintOutput(
                "Skipping unsupported non-root workspace source: " + sourceFileEntry.getValue()));
      }
    }
    for (Map.Entry<Label, Query.Rule> ruleEntry : query.getRulesMap().entrySet()) {
      String ruleClass = ruleEntry.getValue().getRuleClass();

      ProjectTarget.Builder targetBuilder = ProjectTarget.builder();

      targetBuilder.label(ruleEntry.getKey()).kind(ruleClass);
      if (!ruleEntry.getValue().getTestApp().isEmpty()) {
        targetBuilder.testApp(Label.of(ruleEntry.getValue().getTestApp()));
      }
      if (!ruleEntry.getValue().getInstruments().isEmpty()) {
        targetBuilder.instruments(Label.of(ruleEntry.getValue().getInstruments()));
      }
      if (!ruleEntry.getValue().getCustomPackage().isEmpty()) {
        targetBuilder.customPackage(ruleEntry.getValue().getCustomPackage());
      }
      if (!ruleEntry.getValue().getMainClass().isEmpty()) {
        targetBuilder.mainClass(ruleEntry.getValue().getMainClass());
      }

      if (RuleKinds.isJava(ruleClass)) {
        visitJavaRule(ruleEntry.getKey(), ruleEntry.getValue(), targetBuilder);
      }
      if (RuleKinds.isCc(ruleClass)) {
        visitCcRule(ruleEntry.getKey(), ruleEntry.getValue(), targetBuilder);
      }
      if (RuleKinds.isProtoSource(ruleClass)) {
        visitProtoRule(ruleEntry.getValue(), targetBuilder);
      }
      if (alwaysBuildRuleKinds.contains(ruleClass)) {
        projectTargetsToBuild.add(ruleEntry.getKey());
      }
      targetBuilder.tags(ruleEntry.getValue().getTagsList());
      ProjectTarget target = targetBuilder.build();

      for (Label thisSource : target.sourceLabels().values()) {
        addProjectTargetsToBuildIfGenerated(target.label(), thisSource);
      }

      graphBuilder.targetMapBuilder().put(ruleEntry.getKey(), target);
    }
    int nTargets = query.getRulesCount();

    // Calculate all the dependencies outside the project.
    for (Label dep : javaDeps) {
      if (!query.getRulesMap().containsKey(dep)) {
        projectDeps.add(dep);
      }
    }
    // Treat project targets the aspect needs to build as external deps
    projectDeps.addAll(projectTargetsToBuild);

    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(PrintOutput.log("%-10d Targets (%d ms):", nTargets, elapsedMs));

    BuildGraphData graph = graphBuilder.projectDeps(projectDeps).build();

    context.output(PrintOutput.log("%-10d Source files", graph.locations().size()));
    context.output(PrintOutput.log("%-10d Java sources", graph.javaSources().size()));
    context.output(PrintOutput.log("%-10d Packages", graph.packages().size()));
    context.output(PrintOutput.log("%-10d Dependencies", javaDeps.size()));
    context.output(PrintOutput.log("%-10d External dependencies", graph.projectDeps().size()));

    return graph;
  }

  private void visitProtoRule(Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.getSourcesList()));

    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    targetBuilder.depsBuilder().addAll(thisDeps);
  }

  private void visitJavaRule(Label label, Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(QuerySyncLanguage.JAVA);
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.getSourcesList()))
        .putAll(SourceType.ANDROID_RESOURCES, expandFileGroupValues(rule.getResourceFilesList()));

    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    targetBuilder.depsBuilder().addAll(thisDeps);

    targetBuilder.runtimeDepsBuilder().addAll(toLabelList(rule.getRuntimeDepsList()));
    javaDeps.addAll(thisDeps);

    if (RuleKinds.isAndroid(rule.getRuleClass())) {
      // Add android targets with aidl files as external deps so the aspect generates
      // the classes
      if (!rule.getIdlSourcesList().isEmpty()) {
        projectTargetsToBuild.add(label);
      }
      if (!rule.getManifest().isEmpty()) {
        targetBuilder
            .sourceLabelsBuilder()
            .put(SourceType.ANDROID_MANIFEST, Label.of(rule.getManifest()));
      }
    }
  }

  private void visitCcRule(Label label, Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(QuerySyncLanguage.CC);
    targetBuilder.coptsBuilder().addAll(rule.getCoptsList());
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.getSourcesList()))
        .putAll(SourceType.CC_HEADERS, expandFileGroupValues(rule.getHdrsList()));

    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    targetBuilder.depsBuilder().addAll(thisDeps);
  }

  /** Require build step for targets with generated sources. */
  private void addProjectTargetsToBuildIfGenerated(Label label, Label source) {
    if (!query.getSourceFilesMap().containsKey(source)) {
      projectTargetsToBuild.add(label);
    }
  }

  /** Returns a set of sources for a rule, expanding any in-project {@code filegroup} rules */
  private ImmutableSet<Label> expandFileGroupValues(List<String>... labelLists) {
    return stream(labelLists)
        .map(Label::toLabelList)
        .flatMap(List::stream)
        .map(this::expandSourceLabel)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  private ImmutableSet<Label> expandSourceLabel(Label label) {
    if (!shouldExpandSourceLabel(label)) {
      return ImmutableSet.of(label);
    }
    Set<Label> visited = Sets.newHashSet();
    ImmutableSet.Builder<Label> result = ImmutableSet.builder();

    for (String source : requireNonNull(query.getRulesMap().get(label)).getSourcesList()) {
      Label asLabel = Label.of(source);
      if (visited.add(asLabel)) {
        result.addAll(expandSourceLabel(asLabel));
      }
    }

    return result.build();
  }

  private boolean shouldExpandSourceLabel(Label label) {
    Rule rule = query.getRulesMap().get(label);
    if (rule == null) {
      return false;
    }
    if (rule.getRuleClass().equals("filegroup")) {
      return true;
    }
    if (rule.getTagsList().contains("ij-ignore-source-transform")) {
      // This rule has a tag asking us to skip some source transformation and use its sources
      // directly instead - i.e. expand it as we would for a filegroup. This ensures that the IDE
      // considers the workspace sources as the actual sources, rather than the generated
      // (transformed) sources.
      return true;
    }
    return false;
  }
}
