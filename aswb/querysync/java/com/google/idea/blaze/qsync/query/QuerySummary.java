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
import static com.google.common.collect.Multimaps.flatteningToMultimap;
import static com.google.idea.blaze.common.proto.ProtoStringInterner.intern;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Summaries the output from a {@code query} invocation into just the data needed by the rest of
 * querysync.
 *
 * <p>The main purpose of the summarized output is to allow the outputs from multiple {@code query}
 * invocations to be combined. This enables delta updates to the project.
 *
 * <p>If extra data from the {@code query} invocation is needed by later stages of sync, that data
 * should be added to the {@link Query.Summary} proto and this code should be updated accordingly.
 * The proto should remain a simple mapping of data from the build proto, i.e. no complex
 * functionality should be added to this class. Non-trivial calculations based on the output of the
 * query belong in {@link com.google.idea.blaze.qsync.BlazeQueryParser} instead.
 *
 * <p>Instances of the the {@link Query.Summary} proto are maintained in memory so data should not
 * be added to it unnecessarily.
 */
@AutoValue
public abstract class QuerySummary {

  /**
   * The current version of the Query.Summary proto that this is compatible with. Any persisted
   * protos with a different version embedded in them will be discarded.
   *
   * <p>Whenever changing the logic in this class such that the Query.Summary proto contents will be
   * different for the same input, this version should be incremented.
   */
  @VisibleForTesting public static final int PROTO_VERSION = 10;

  public static final QuerySummary EMPTY =
      create(Query.Summary.newBuilder().setVersion(PROTO_VERSION).build());

  // Compile-time dependency attributes, as they appear in streamed_proto output
  private static final ImmutableSet<String> DEPENDENCY_ATTRIBUTES =
      ImmutableSet.of(
          // android_local_test depends on junit implicitly using the _junit attribute.
          "$junit",
          "deps",
          // java_proto_library and java_lite_proto_library rules depend on the proto runtime
          // library via these proto_toolchain attributes. In Starlark, the attribute names
          // begin with an underscore instead of a colon (e.g., _aspect_java_proto_toolchain).
          ":aspect_java_proto_toolchain",
          ":aspect_proto_toolchain_for_javalite",
          // This is not strictly correct, as source files of rule with 'export' do not
          // depend on exported targets.
          "exports");

  // Compile time dependency attributes scoped to specific rule kind, for cases where sync does not
  // need to always need to traverse the attribute.
  private static final ImmutableMap<String, ImmutableSet<String>> RULE_SCOPED_ATTRIBUTES =
      ImmutableMap.of(
          "$toolchain",
          ImmutableSet.of(
              "_java_grpc_library",
              "_java_lite_grpc_library",
              "kt_jvm_library_helper",
              "android_library",
              "kt_android_library"));

  // Runtime dependency attributes
  private static final ImmutableSet<String> RUNTIME_DEP_ATTRIBUTES =
      ImmutableSet.of(
          // From android_binary rules used in android_instrumentation_tests
          "instruments",
          // From android_instrumentation_test rules
          "test_app");

  // Source attributes.
  private static final ImmutableSet<String> SRCS_ATTRIBUTES =
      ImmutableSet.of(
          "srcs", "java_srcs", "kotlin_srcs", "java_test_srcs", "kotlin_test_srcs", "common_srcs");

  public abstract Query.Summary proto();

  public boolean isCompatibleWithCurrentPluginVersion() {
    return proto().getVersion() == PROTO_VERSION;
  }

  /** Do not generate toString, this object is too large */
  @Override
  public final String toString() {
    return super.toString();
  }

  public static QuerySummary create(Query.Summary proto) {
    return new AutoValue_QuerySummary(proto);
  }

  public static QuerySummary create(InputStream protoInputStream) throws IOException {
    // IMPORTANT: when changing the logic herein, you should also update PROTO_VERSION above.
    // Failure to do so is likely to result in problems during a partial sync.
    Map<String, Query.SourceFile> sourceFileMap = Maps.newHashMap();
    Map<String, Query.Rule> ruleMap = Maps.newHashMap();
    Set<String> packagesWithErrors = Sets.newHashSet();
    Build.Target target;
    while ((target = intern(Target.parseDelimitedFrom(protoInputStream))) != null) {
      switch (target.getType()) {
        case SOURCE_FILE:
          Query.SourceFile sourceFile =
              Query.SourceFile.newBuilder()
                  .setLocation(target.getSourceFile().getLocation())
                  .addAllSubinclude(target.getSourceFile().getSubincludeList())
                  .build();
          sourceFileMap.put(target.getSourceFile().getName(), sourceFile);
          if (target.getSourceFile().getPackageContainsErrors()) {
            packagesWithErrors.add(target.getSourceFile().getName());
          }
          break;
        case RULE:
          // TODO We don't need all rules types in the proto since many are not user later on.
          //   We could filter the rules here, or even create rule-specific proto messages to
          //   reduce the size of the output proto.
          Query.Rule.Builder rule =
              Query.Rule.newBuilder().setRuleClass(target.getRule().getRuleClass());
          for (Build.Attribute a : target.getRule().getAttributeList()) {
            if (SRCS_ATTRIBUTES.contains(a.getName())) {
              rule.addAllSources(a.getStringListValueList());
            } else if (a.getName().equals("hdrs")) {
              rule.addAllHdrs(a.getStringListValueList());
            } else if (attributeIsTrackedDependency(a.getName(), target)) {
              if (a.hasStringValue()) {
                rule.addDeps(a.getStringValue());
              } else {
                rule.addAllDeps(a.getStringListValueList());
              }
            } else if (RUNTIME_DEP_ATTRIBUTES.contains(a.getName())) {
              if (a.hasStringValue()) {
                rule.addRuntimeDeps(a.getStringValue());
              } else {
                rule.addAllRuntimeDeps(a.getStringListValueList());
              }
            } else if (a.getName().equals("idl_srcs")) {
              rule.addAllIdlSources(a.getStringListValueList());
            } else if (a.getName().equals("resource_files")) {
              rule.addAllResourceFiles(a.getStringListValueList());
            } else if (a.getName().equals("manifest")) {
              rule.setManifest(a.getStringValue());
            } else if (a.getName().equals("custom_package")) {
              rule.setCustomPackage(a.getStringValue());
            } else if (a.getName().equals("copts")) {
              rule.addAllCopts(a.getStringListValueList());
            } else if (a.getName().equals("tags")) {
              rule.addAllTags(a.getStringListValueList());
            } else if (a.getName().equals("main_class")) {
              rule.setMainClass(a.getStringValue());
            }

            if (a.getName().equals("test_app")) {
              rule.setTestApp(a.getStringValue());
            } else if (a.getName().equals("instruments")) {
              rule.setInstruments(a.getStringValue());
            }
          }
          ruleMap.put(Label.of(target.getRule().getName()).toString(), rule.build());
          break;
        default:
          break;
      }
    }
    return create(
        Query.Summary.newBuilder()
            .setVersion(PROTO_VERSION)
            .putAllSourceFiles(sourceFileMap)
            .putAllRules(ruleMap)
            .addAllPackagesWithErrors(packagesWithErrors)
            .build());
  }

  private static boolean attributeIsTrackedDependency(String attributeName, Build.Target target) {
    if (DEPENDENCY_ATTRIBUTES.contains(attributeName)) {
      return true;
    }
    if (RULE_SCOPED_ATTRIBUTES.containsKey(attributeName)) {
      return requireNonNull(RULE_SCOPED_ATTRIBUTES.get(attributeName))
          .contains(target.getRule().getRuleClass());
    }
    return false;
  }

  public static QuerySummary create(File protoFile) throws IOException {
    return create(new BufferedInputStream(new FileInputStream(protoFile)));
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Returns the map of source files included in the query output.
   *
   * <p>This is a map of source target label to the {@link SourceFile} proto representing it.
   */
  @Memoized
  public ImmutableMap<Label, SourceFile> getSourceFilesMap() {
    return proto().getSourceFilesMap().entrySet().stream()
        .collect(toImmutableMap(e -> Label.of(e.getKey()), Map.Entry::getValue));
  }

  /**
   * Returns the map of rules included in the query output.
   *
   * <p>This is a map of rule label to the {@link Query.Rule} proto representing it.
   */
  @Memoized
  public ImmutableMap<Label, Query.Rule> getRulesMap() {
    return proto().getRulesMap().entrySet().stream()
        .collect(toImmutableMap(e -> Label.of(e.getKey()), Map.Entry::getValue));
  }

  @Memoized
  public ImmutableSet<Path> getPackagesWithErrors() {
    return proto().getPackagesWithErrorsList().stream()
        .map(Label::of)
        .map(Label::getPackage) // The packages are BUILD file labels.
        .collect(toImmutableSet());
  }

  /**
   * Returns the set of build packages in the query output.
   *
   * <p>The packages are workspace relative paths that contain a BUILD file.
   */
  @Memoized
  public PackageSet getPackages() {
    return new PackageSet(
        Stream.concat(
                getSourceFilesMap().keySet().stream().map(Label::getPackage).distinct(),
                getPackagesWithErrors().stream())
            .collect(toImmutableSet()));
  }

  /**
   * Returns a map of .bzl file labels to BUILD file labels that include them.
   *
   * <p>This is used to determine, for example, which build files include a given .bzl file.
   */
  @Memoized
  public ImmutableMultimap<Path, Path> getReverseSubincludeMap() {
    SetMultimap<Path, Path> includes =
        getSourceFilesMap().entrySet().stream()
            .collect(
                flatteningToMultimap(
                    e -> e.getKey().toFilePath(),
                    e ->
                        e.getValue().getSubincludeList().stream()
                            .map(Label::of)
                            .map(Label::toFilePath),
                    HashMultimap::create));
    return ImmutableMultimap.copyOf(Multimaps.invertFrom(includes, HashMultimap.create()));
  }

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  public Optional<Path> getParentPackage(Path buildPackage) {
    return getPackages().getParentPackage(buildPackage);
  }

  /**
   * Builder for {@link QuerySummary}. This should be used when constructing a summary from a map of
   * source files and rules. To construct one from a serialized proto, you should use {@link
   * QuerySummary#create(InputStream)} instead.
   */
  public static class Builder {
    private final Query.Summary.Builder builder =
        Query.Summary.newBuilder().setVersion(PROTO_VERSION);

    Builder() {}

    public Builder putAllSourceFiles(Map<Label, Query.SourceFile> sourceFileMap) {
      builder.putAllSourceFiles(
          sourceFileMap.entrySet().stream()
              .collect(toImmutableMap(e -> e.getKey().toString(), Map.Entry::getValue)));
      return this;
    }

    public Builder putAllRules(Map<Label, Query.Rule> rulesMap) {
      builder.putAllRules(
          rulesMap.entrySet().stream()
              .collect(toImmutableMap(e -> e.getKey().toString(), Map.Entry::getValue)));
      return this;
    }

    public Builder putAllPackagesWithErrors(Set<Path> packagesWithErrors) {
      packagesWithErrors.stream()
          // TODO: b/334110669 - Consider multi workspace-builds.
          .map(p -> Label.fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, p, "BUILD"))
          .map(Label::toString)
          .forEach(builder::addPackagesWithErrors);
      return this;
    }

    public QuerySummary build() {
      return QuerySummary.create(intern(builder.build()));
    }
  }
}
