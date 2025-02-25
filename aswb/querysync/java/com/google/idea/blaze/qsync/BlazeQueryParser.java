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

import com.google.common.collect.ImmutableMap;
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
import com.google.idea.blaze.qsync.query.QueryData;
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
      "_java_grpc_library",
      "_java_lite_grpc_library",
      "aar_import",
      "af_internal_soyinfo_generator",
      "java_import",
      "java_lite_proto_library",
      "java_mutable_proto_library",
      "java_proto_library",
      "java_stubby_library",
      "kt_grpc_library_helper",
      "kt_proto_library_helper", // Underlying rule for kt_jvm_lite_proto_library and kt_jvm_proto_library
      "kt_stubby_library_helper");

  private final Context<?> context;
  private final SetView<String> alwaysBuildRuleKinds;

  private final QuerySummary query;

  private final BuildGraphData.Builder graphBuilder = BuildGraphData.builder();

  private final Set<Label> projectDeps = Sets.newHashSet();
  // All the project targets the aspect needs to build
  private final Set<Label> projectTargetsToBuild = new HashSet<>();
  // An aggregation of all the dependencies of java rules
  private final Set<Label> javaDeps = new HashSet<>();


  public static class RuleVisitors {

    @FunctionalInterface
    interface RuleVisitor {
      void visit(BlazeQueryParser parser, Label label, QueryData.Rule rule, ProjectTarget.Builder targetBuilder);
    }

    private final ImmutableMap<String, RuleVisitor> myVisitorsByRuleClass;

    {
      ImmutableMap.Builder<String, RuleVisitor> builder = ImmutableMap.builder();
      register(builder, RuleKinds.ANDROID_RULE_KINDS, BlazeQueryParser::visitJavaRule);
      register(builder, RuleKinds.JAVA_RULE_KINDS, BlazeQueryParser::visitJavaRule);
      register(builder, RuleKinds.CC_RULE_KINDS, BlazeQueryParser::visitCcRule);
      register(builder, RuleKinds.PROTO_SOURCE_RULE_KINDS, BlazeQueryParser::visitProtoRule);
      myVisitorsByRuleClass = builder.buildOrThrow();
    }

    public void visit(BlazeQueryParser parser, Label label, QueryData.Rule rule, ProjectTarget.Builder targetBuilder) {
      String ruleClass = rule.ruleClass();
      final var visitor = myVisitorsByRuleClass.get(ruleClass);
      if (visitor != null) {
        visitor.visit(parser, label, rule, targetBuilder);
      }
    }

    private static void register(ImmutableMap.Builder<String, RuleVisitor> builder,
                                 Set<String> kinds,
                                 RuleVisitor visitor) {
      for (String kind : kinds) {
        builder.put(kind, visitor);
      }
    }
  }

  /**
   * Returns the list of all rule classes directly supported by the current query sync configuration.
   *
   * <p>This information is only supposed ot be used to refine the Bazel query that query sync issues.
   */
  public static ImmutableSet<String> getAllSupportedRuleClasses() {
    return new RuleVisitors().myVisitorsByRuleClass.keySet();
  }

  public BlazeQueryParser(
      QuerySummary query, Context<?> context, ImmutableSet<String> handledRuleKinds) {
    this.context = context;
    this.alwaysBuildRuleKinds = Sets.difference(ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
    this.query = query;
  }

  public BuildGraphData parse() {
    context.output(PrintOutput.log("Analyzing project structure..."));

    long now = System.nanoTime();
    final var visitors = new RuleVisitors();
    for (Map.Entry<Label, QueryData.SourceFile> sourceFileEntry :
        query.getSourceFilesMap().entrySet()) {
      if (sourceFileEntry.getKey().getWorkspaceName().isEmpty()) {
        graphBuilder
            .sourceFileLabelsBuilder().add(sourceFileEntry.getKey());
      } else {
        context.output(
            new PrintOutput(
                "Skipping unsupported non-root workspace source: " + sourceFileEntry.getValue()));
      }
    }
    for (Map.Entry<Label, QueryData.Rule> ruleEntry : query.getRulesMap().entrySet()) {
      ProjectTarget.Builder targetBuilder = ProjectTarget.builder();

      QueryData.Rule rule = ruleEntry.getValue();
      targetBuilder.label(ruleEntry.getKey()).kind(rule.ruleClass());
      if (!rule.testApp().isEmpty()) {
        targetBuilder.testApp(Label.of(rule.testApp()));
      }
      if (!rule.instruments().isEmpty()) {
        targetBuilder.instruments(Label.of(rule.instruments()));
      }
      if (!rule.customPackage().isEmpty()) {
        targetBuilder.customPackage(rule.customPackage());
      }
      if (!rule.mainClass().isEmpty()) {
        targetBuilder.mainClass(rule.mainClass());
      }

      visitors.visit(this, ruleEntry.getKey(), rule, targetBuilder);
      if (alwaysBuildRuleKinds.contains(rule.ruleClass())) {
        projectTargetsToBuild.add(ruleEntry.getKey());
      }
      targetBuilder.tags(rule.tags());
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

    context.output(PrintOutput.log("%-10d Source files", graph.sourceFileLabels().size()));
    context.output(PrintOutput.log("%-10d Java sources", graph.javaSources().size()));
    context.output(PrintOutput.log("%-10d Packages", graph.packages().size()));
    context.output(PrintOutput.log("%-10d Dependencies", javaDeps.size()));
    context.output(PrintOutput.log("%-10d External dependencies", graph.projectDeps().size()));

    return graph;
  }

  private void visitProtoRule(Label unused, QueryData.Rule rule, ProjectTarget.Builder targetBuilder) {
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.sources()));

    Set<Label> thisDeps = Sets.newHashSet(rule.deps());
    targetBuilder.depsBuilder().addAll(thisDeps);
  }

  private void visitJavaRule(
      Label label, QueryData.Rule rule, ProjectTarget.Builder targetBuilder) {
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(QuerySyncLanguage.JAVA);
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.sources()))
        .putAll(SourceType.ANDROID_RESOURCES, expandFileGroupValues(rule.resourceFiles()));

    Set<Label> thisDeps = Sets.newHashSet(rule.deps());
    targetBuilder.depsBuilder().addAll(thisDeps);

    targetBuilder.runtimeDepsBuilder().addAll(rule.runtimeDeps());
    javaDeps.addAll(thisDeps);

    if (RuleKinds.isAndroid(rule.ruleClass())) {
      // Add android targets with aidl files as external deps so the aspect generates
      // the classes
      if (!rule.idlSources().isEmpty()) {
        projectTargetsToBuild.add(label);
      }
      if (rule.manifest().isPresent()) {
        targetBuilder
            .sourceLabelsBuilder()
            .put(SourceType.ANDROID_MANIFEST, rule.manifest().get());
      }
    }
  }

  private void visitCcRule(Label label, QueryData.Rule rule, ProjectTarget.Builder targetBuilder) {
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(QuerySyncLanguage.CC);
    targetBuilder.coptsBuilder().addAll(rule.copts());
    targetBuilder
        .sourceLabelsBuilder()
        .putAll(SourceType.REGULAR, expandFileGroupValues(rule.sources()))
        .putAll(SourceType.CC_HEADERS, expandFileGroupValues(rule.hdrs()));

    Set<Label> thisDeps = Sets.newHashSet(rule.deps());
    targetBuilder.depsBuilder().addAll(thisDeps);
  }

  /** Require build step for targets with generated sources. */
  private void addProjectTargetsToBuildIfGenerated(Label label, Label source) {
    if (!query.getSourceFilesMap().containsKey(source)) {
      projectTargetsToBuild.add(label);
    }
  }

  /** Returns a set of sources for a rule, expanding any in-project {@code filegroup} rules */
  private ImmutableSet<Label> expandFileGroupValues(List<Label> labelLists) {
    return labelLists.stream()
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

    for (Label source : requireNonNull(query.getRulesMap().get(label)).sources()) {
      if (visited.add(source)) {
        result.addAll(expandSourceLabel(source));
      }
    }

    return result.build();
  }

  private boolean shouldExpandSourceLabel(Label label) {
    QueryData.Rule rule = query.getRulesMap().get(label);
    if (rule == null) {
      return false;
    }
    if (rule.ruleClass().equals("filegroup")) {
      return true;
    }
    if (rule.tags().contains("ij-ignore-source-transform")) {
      // This rule has a tag asking us to skip some source transformation and use its sources
      // directly instead - i.e. expand it as we would for a filegroup. This ensures that the IDE
      // considers the workspace sources as the actual sources, rather than the generated
      // (transformed) sources.
      return true;
    }
    return false;
  }
}
