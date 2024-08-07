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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.BLAZE_DATA_SUBDIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_HEADERS_DIRECTORY;

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
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcToolchainInfo;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings;
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage;
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
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

  /* The set of top-level directories within the workspace that store generated header files.
   * This is needed to distinguish between header search paths that refer to generated vs source
   * directories, since the information is not provided explicitly by bazel. We derive the contents
   * of this from the set of generated header files.
   *
   * Typically this will contain a single entry `bazel-out` but we don't hardcode that since it can
   * be different in different variants of bazel. */
  private final ImmutableSet<Path> topLevelGenHdrsPaths;

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
    // We save the top level dirs that generated headers go into as we need them later on to
    // distinguish between sources and generated files (bazel gives us all paths relative to
    // the workspace root, where generated files go under e.g. `bazel-out` there)
    // TODO(mathewi) this is not perfect, as it fails if there are no generated headers, although
    //  that failure is benign. It would be better to derive the information from the `bazel info`
    //  output.
    this.topLevelGenHdrsPaths =
        ccDependenciesInfo.targetInfoMap().values().stream()
            .map(CcTargetInfo::getGenHdrsList)
            .flatMap(List::stream)
            .map(
                a ->
                    switch (a.getPathCase()) {
                      case DIRECTORY -> a.getDirectory();
                      case FILE -> a.getFile();
                      case PATH_NOT_SET -> "";
                    })
            .map(Path::of)
            .map(p -> p.getName(0))
            .collect(toImmutableSet());
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

  private void visitToolchainMap(Map<String, CcToolchainInfo> toolchainInfoMap) {
    toolchainInfoMap.values().forEach(this::visitToolchain);
  }

  private void visitToolchain(CcToolchainInfo toolchainInfo) {
    compilerExecutableMap.put(
        toolchainInfo.getId(),
        ProjectPath.newBuilder()
            .setPath(toolchainInfo.getCompilerExecutable())
            .setBase(Base.WORKSPACE)
            .build());

    ImmutableList<CcCompilerFlag> commonFlags =
        toolchainInfo.getBuiltInIncludeDirectoriesList().stream()
            .map(Path::of)
            .map(p -> makePathFlag("-I", p))
            .collect(toImmutableList());

    toolchainLanguageFlags.put(
        toolchainInfo.getId(),
        ImmutableListMultimap.<CcLanguage, CcCompilerFlag>builder()
            .putAll(
                CcLanguage.C,
                ImmutableList.<CcCompilerFlag>builder()
                    .addAll(commonFlags)
                    .addAll(
                        toolchainInfo.getCOptionsList().stream()
                            .map(f -> makeStringFlag(f, ""))
                            .iterator())
                    .build())
            .putAll(
                CcLanguage.CPP,
                ImmutableList.<CcCompilerFlag>builder()
                    .addAll(commonFlags)
                    .addAll(
                        toolchainInfo.getCppOptionsList().stream()
                            .map(f -> makeStringFlag(f, ""))
                            .iterator())
                    .build())
            .build());
  }

  private void visitTargetMap(Map<Label, CcTargetInfo> targetMap) {
    targetMap.forEach(this::visitTarget);
  }

  private void visitTarget(Label label, CcTargetInfo target) {
    if (!graph.targetMap().containsKey(label)) {
      // This target is no longer present in the project. Ignore it.
      // We should really clean up the dependency cache itself to remove any artifacts relating to
      // no-longer-present targets, but that will be a lot more work. For now, just ensure we don't
      // crash.
      return;
    }
    ProjectTarget projectTarget = Preconditions.checkNotNull(graph.targetMap().get(label));

    CcToolchainInfo toolchain = ccDependenciesInfo.toolchainInfoMap().get(target.getToolchainId());

    ImmutableList<CcCompilerFlag> targetFlags =
        ImmutableList.<CcCompilerFlag>builder()
            .addAll(projectTarget.copts().stream().map(d -> makeStringFlag(d, "")).iterator())
            .addAll(target.getDefinesList().stream().map(d -> makeStringFlag("-D", d)).iterator())
            .addAll(
                target.getIncludeDirectoriesList().stream()
                    .map(Path::of)
                    .map(p -> makePathFlag("-I", p))
                    .iterator())
            .addAll(
                target.getQuoteIncludeDirectoriesList().stream()
                    .map(Path::of)
                    .map(p -> makePathFlag("-iquote", p))
                    .iterator())
            .addAll(
                target.getSystemIncludeDirectoriesList().stream()
                    .map(Path::of)
                    .map(p -> makePathFlag("-isystem", p))
                    .iterator())
            .addAll(
                target.getFrameworkIncludeDirectoriesList().stream()
                    .map(Path::of)
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
                            compilerExecutableMap.get(target.getToolchainId()))
                        .setFlagSetId(
                            addFlagSet(
                                ImmutableList.<CcCompilerFlag>builder()
                                    .addAll(targetFlags)
                                    .addAll(
                                        toolchainLanguageFlags
                                            .get(target.getToolchainId())
                                            .get(lang.get()))
                                    .build())))
                .build());
      }
    }
    ImmutableList<CcSourceFile> srcs = srcsBuilder.build();

    CcCompilationContext targetContext =
        CcCompilationContext.newBuilder()
            .setId(label + "%" + toolchain.getTargetName())
            .setHumanReadableName(label + " - " + toolchain.getTargetName())
            .addAllSources(srcs)
            .putAllLanguageToCompilerSettings(
                toolchainLanguageFlags.get(toolchain.getId()).asMap().entrySet().stream()
                    .collect(
                        toImmutableMap(
                            e -> e.getKey().getValueDescriptor().getName(),
                            e ->
                                CcCompilerSettings.newBuilder()
                                    .setCompilerExecutablePath(
                                        compilerExecutableMap.get(toolchain.getId()))
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

  private CcCompilerFlag makePathFlag(String flag, Path path) {
    ProjectPath.Builder pathBuilder = ProjectPath.newBuilder();
    if (topLevelGenHdrsPaths.contains(path.getName(0))) {
      // The directories given by blaze include the "bazel-out" component, but that is not present
      // in the paths of the generated headers themselves due to the legacy semantics of
      // OutputArtifactInfo which strips it. Since that determines their location in the
      // cache, remove here too it to ensure consistency:
      path = path.subpath(1, path.getNameCount());
      pathBuilder
          .setBase(Base.PROJECT)
          .setPath(
              Path.of(BLAZE_DATA_SUBDIRECTORY, GEN_HEADERS_DIRECTORY).resolve(path).toString());
    } else {
      pathBuilder.setBase(Base.WORKSPACE).setPath(path.toString());
    }
    return CcCompilerFlag.newBuilder().setFlag(flag).setPath(pathBuilder.build()).build();
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
