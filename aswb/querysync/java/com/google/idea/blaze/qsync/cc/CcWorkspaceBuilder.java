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
package com.google.idea.blaze.qsync.cc;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.deps.CcCompilationInfo;
import com.google.idea.blaze.qsync.deps.CcToolchain;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings;
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage;
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Updates the project proto with the output from a CC dependencies build. */
public class CcWorkspaceBuilder {

  private static final AtomicInteger nextFlagSetId = new AtomicInteger(0);

  private final CcDependenciesInfo ccDependenciesInfo;
  private final BuildGraphData graph;
  private final Context<?> context;

  /* Map from toolchain ID -> language -> flags for that toolchain & language. */
  private final Map<String, Multimap<CcLanguage, CcCompilerFlag>> toolchainLanguageFlags =
      Maps.newHashMap();
  /* Map from toolchain ID -> compiler executable. */
  private final Map<String, ProjectPath> compilerExecutableMap = Maps.newHashMap();
  /* Map of unique sets of compiler flags to an ID to identify them.
   * We do this as the downstream code turns each set of flags into a CidrCompilerSwitches instance
   * which can have a large memory footprint. */
  private final Map<Set<CcCompilerFlag>, String> uniqueFlagSetIds = Maps.newHashMap();

  /* Workspace builder, populated incrementally from within the various visitXxxx methods. */
  ProjectProto.CcWorkspace.Builder workspaceBuilder = ProjectProto.CcWorkspace.newBuilder();

  public CcWorkspaceBuilder(
      CcDependenciesInfo ccDepsInfo, BuildGraphData graph, Context<?> context) {
    this.ccDependenciesInfo = ccDepsInfo;
    this.graph = graph;
    this.context = context;
  }

  public ProjectProto.Project updateProjectProtoForCcDeps(ProjectProto.Project projectProto) {
    return createWorkspace()
        .map(
            w ->
                projectProto.toBuilder()
                    .setCcWorkspace(w)
                    .addActiveLanguages(LanguageClass.LANGUAGE_CLASS_CC)
                    .build())
        .orElse(projectProto);
  }

  private Optional<ProjectProto.CcWorkspace> createWorkspace() {
    if (ccDependenciesInfo.targetInfoMap().isEmpty()) {
      return Optional.empty();
    }

    visitToolchainMap(ccDependenciesInfo.toolchainInfoMap());
    visitTargetMap(ccDependenciesInfo.targetInfoMap());

    return Optional.of(workspaceBuilder.build());
  }

  private void visitToolchainMap(Map<String, CcToolchain> toolchainInfoMap) {
    toolchainInfoMap.values().forEach(this::visitToolchain);
  }

  private void visitToolchain(CcToolchain toolchainInfo) {
    compilerExecutableMap.put(toolchainInfo.id(), toolchainInfo.compilerExecutable());

    ImmutableList<CcCompilerFlag> commonFlags =
        toolchainInfo.builtInIncludeDirectories().stream()
            .map(p -> makePathFlag("-I", p))
            .collect(toImmutableList());

    toolchainLanguageFlags.put(
        toolchainInfo.id(),
        ImmutableListMultimap.<CcLanguage, CcCompilerFlag>builder()
            .putAll(
                CcLanguage.C,
                ImmutableList.<CcCompilerFlag>builder()
                    .addAll(commonFlags)
                    .addAll(
                        toolchainInfo.cOptions().stream()
                            .map(f -> makeStringFlag(f, ""))
                            .iterator())
                    .build())
            .putAll(
                CcLanguage.CPP,
                ImmutableList.<CcCompilerFlag>builder()
                    .addAll(commonFlags)
                    .addAll(
                        toolchainInfo.cppOptions().stream()
                            .map(f -> makeStringFlag(f, ""))
                            .iterator())
                    .build())
            .build());
  }

  private void visitTargetMap(Map<Label, CcCompilationInfo> targetMap) {
    targetMap.forEach(this::visitTarget);
  }

  private void visitTarget(Label label, CcCompilationInfo target) {
    if (!graph.targetMap().containsKey(label)) {
      // This target is no longer present in the project. Ignore it.
      // We should really clean up the dependency cache itself to remove any artifacts relating to
      // no-longer-present targets, but that will be a lot more work. For now, just ensure we don't
      // crash.
      return;
    }
    ProjectTarget projectTarget = Preconditions.checkNotNull(graph.targetMap().get(label));

    CcToolchain toolchain = ccDependenciesInfo.toolchainInfoMap().get(target.toolchainId());

    ImmutableList<CcCompilerFlag> targetFlags =
        ImmutableList.<CcCompilerFlag>builder()
            .addAll(projectTarget.copts().stream().map(d -> makeStringFlag(d, "")).iterator())
            .addAll(target.defines().stream().map(d -> makeStringFlag("-D", d)).iterator())
            .addAll(target.includeDirectories().stream().map(p -> makePathFlag("-I", p)).iterator())
            .addAll(
                target.quoteIncludeDirectories().stream()
                    .map(p -> makePathFlag("-iquote", p))
                    .iterator())
            .addAll(
                target.systemIncludeDirectories().stream()
                    .map(p -> makePathFlag("-isystem", p))
                    .iterator())
            .addAll(
                target.frameworkIncludeDirectories().stream()
                    .map(p -> makePathFlag("-F", p))
                    .iterator())
            .build();

    ImmutableList.Builder<CcSourceFile> srcsBuilder = ImmutableList.builder();
    // TODO(mathewi): The handling of flag sets here is not optimal, since we recalculate an
    //  identical flag set for each source of the same language, then immediately de-dupe them in
    //  the addFlagSet call. For large flag sets this may be slow.
    for (Path srcPath : graph.getTargetSources(label, SourceType.all())) {
      Optional<CcLanguage> lang = getLanguage(srcPath);
      if (lang.isPresent()) {
        srcsBuilder.add(
            CcSourceFile.newBuilder()
                .setLanguage(lang.get())
                .setWorkspacePath(srcPath.toString())
                .setCompilerSettings(
                    CcCompilerSettings.newBuilder()
                        .setCompilerExecutablePath(
                            compilerExecutableMap.get(target.toolchainId()).toProto())
                        .setFlagSetId(
                            addFlagSet(
                                ImmutableList.<CcCompilerFlag>builder()
                                    .addAll(targetFlags)
                                    .addAll(
                                        toolchainLanguageFlags
                                            .get(target.toolchainId())
                                            .get(lang.get()))
                                    .build())))
                .build());
      }
    }
    ImmutableList<CcSourceFile> srcs = srcsBuilder.build();

    CcCompilationContext targetContext =
        CcCompilationContext.newBuilder()
            .setId(label + "%" + toolchain.targetGnuSystemName())
            .setHumanReadableName(label + " - " + toolchain.targetGnuSystemName())
            .addAllSources(srcs)
            .putAllLanguageToCompilerSettings(
                toolchainLanguageFlags.get(toolchain.id()).asMap().entrySet().stream()
                    .collect(
                        toImmutableMap(
                            e -> e.getKey().getValueDescriptor().getName(),
                            e ->
                                CcCompilerSettings.newBuilder()
                                    .setCompilerExecutablePath(
                                        compilerExecutableMap.get(toolchain.id()).toProto())
                                    .setFlagSetId(addFlagSet(e.getValue()))
                                    .build())))
            .build();
    workspaceBuilder.addContexts(targetContext);
  }

  /** Ensure that the given flagset exists, adding it if necessary, and return its unique ID. */
  private String addFlagSet(Collection<CcCompilerFlag> flags) {
    // Create a set so that two flags sets are considered equivalent if their flag order differs.
    ImmutableSet<CcCompilerFlag> canonicalFlagSet = ImmutableSet.copyOf(flags);
    String flagSetId = uniqueFlagSetIds.get(canonicalFlagSet);

    if (flagSetId == null) {
      flagSetId = Integer.toString(nextFlagSetId.incrementAndGet());
      uniqueFlagSetIds.put(canonicalFlagSet, flagSetId);
      workspaceBuilder.putFlagSets(
          flagSetId, CcCompilerFlagSet.newBuilder().addAllFlags(flags).build());
    }
    return flagSetId;
  }

  private CcCompilerFlag makeStringFlag(String flag, String value) {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPlainValue(value).build();
  }

  private CcCompilerFlag makePathFlag(String flag, ProjectPath path) {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPath(path.toProto()).build();
  }

  private static final ImmutableMap<String, CcLanguage> EXTENSION_TO_LANGUAGE_MAP =
      ImmutableMap.of(
          "c", CcLanguage.C,
          "cc", CcLanguage.CPP,
          "cpp", CcLanguage.CPP,
          "cxx", CcLanguage.CPP,
          "c++", CcLanguage.CPP,
          "C", CcLanguage.C);
  /* Files we ignore because they are not top level source files: */
  private static final ImmutableSet<String> IGNORE_SRC_FILE_EXTENSIONS =
      ImmutableSet.of("h", "hh", "hpp", "hxx", "inc", "inl", "H", "S", "a", "lo", "so", "o");

  private Optional<CcLanguage> getLanguage(Path srcPath) {
    // logic in here based on https://bazel.build/reference/be/c-cpp#cc_library.srcs
    int lastDot = srcPath.getFileName().toString().lastIndexOf('.');
    if (lastDot < 0) {
      // default to cpp
      context.output(
          PrintOutput.log("No extension for c/c++ source file %s; assuming cpp", srcPath));
      return Optional.of(CcLanguage.CPP);
    }
    String ext = srcPath.getFileName().toString().substring(lastDot + 1);
    if (IGNORE_SRC_FILE_EXTENSIONS.contains(ext)) {
      return Optional.empty();
    }
    if (EXTENSION_TO_LANGUAGE_MAP.containsKey(ext)) {
      return Optional.of(EXTENSION_TO_LANGUAGE_MAP.get(ext));
    }
    context.output(
        PrintOutput.log(
            "Unrecognized extension %s for c/c++ source file %s; assuming cpp", ext, srcPath));
    return Optional.of(CcLanguage.CPP);
  }
}
