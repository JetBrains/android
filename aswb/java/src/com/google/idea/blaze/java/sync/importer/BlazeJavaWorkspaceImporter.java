/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.DuplicateSourceDetector;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyLibrary;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.source.SourceDirectoryCalculator;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Builds a BlazeWorkspace. */
public final class BlazeJavaWorkspaceImporter {
  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final BuildSystemProvider buildSystemProvider;
  private final ImportRoots importRoots;
  private final TargetMap targetMap;
  private final JdepsMap jdepsMap;
  @Nullable private final JavaWorkingSet workingSet;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final DuplicateSourceDetector duplicateSourceDetector = new DuplicateSourceDetector();
  private final JavaSourceFilter sourceFilter;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final List<BlazeJavaSyncAugmenter> augmenters;
  private final ProjectViewSet projectViewSet;
  @Nullable private final SyncState oldSyncState;

  public BlazeJavaWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      TargetMap targetMap,
      JavaSourceFilter sourceFilter,
      JdepsMap jdepsMap,
      @Nullable JavaWorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder,
      @Nullable SyncState oldSyncState) {
    this.project = project;
    this.workspaceRoot = workspaceRoot;
    this.buildSystemProvider = Blaze.getBuildSystemProvider(project);
    this.importRoots =
        ImportRoots.builder(workspaceRoot, buildSystemProvider.getBuildSystem().getName())
            .add(projectViewSet)
            .build();
    this.targetMap = targetMap;
    this.sourceFilter = sourceFilter;
    this.jdepsMap = jdepsMap;
    this.workingSet = workingSet;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.augmenters = Arrays.asList(BlazeJavaSyncAugmenter.EP_NAME.getExtensions());
    this.projectViewSet = projectViewSet;
    this.oldSyncState = oldSyncState;
  }

  public BlazeJavaImportResult importWorkspace(BlazeContext context) {
    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();
    for (TargetIdeInfo target : sourceFilter.sourceTargets) {
      addTargetAsSource(
          workspaceBuilder, target, sourceFilter.targetToJavaSources.get(target.getKey()));
    }

    SourceDirectoryCalculator sourceDirectoryCalculator = new SourceDirectoryCalculator();
    ImmutableList<BlazeContentEntry> contentEntries =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            artifactLocationDecoder,
            importRoots,
            workspaceBuilder.sourceArtifacts,
            workspaceBuilder.javaPackageManifests);

    int totalContentEntryCount = 0;
    for (BlazeContentEntry contentEntry : contentEntries) {
      totalContentEntryCount += contentEntry.sources.size();
    }
    context.output(PrintOutput.log("Java content entry count: " + totalContentEntryCount));

    BlazeJavaImportResult.Builder importResultBuilder = BlazeJavaImportResult.builder();
    ImmutableMap<LibraryKey, BlazeJarLibrary> libraries =
        buildLibraries(context, workspaceBuilder, sourceFilter.libraryTargets, importResultBuilder);

    duplicateSourceDetector.reportDuplicates(context);

    String sourceVersion = findSourceVersion(targetMap);

    return importResultBuilder
        .setContentEntries(contentEntries)
        .setLibraries(libraries)
        .setBuildOutputJars(ImmutableList.sortedCopyOf(workspaceBuilder.buildOutputJars))
        .setJavaSourceFiles(ImmutableSet.copyOf(workspaceBuilder.addedSourceFiles))
        .setSourceVersion(sourceVersion)
        .setPluginProcessorJars(workspaceBuilder.pluginProcessorJars)
        .build();
  }

  private ImmutableMap<LibraryKey, BlazeJarLibrary> buildLibraries(
      BlazeContext context,
      WorkspaceBuilder workspaceBuilder,
      List<TargetIdeInfo> libraryTargets,
      BlazeJavaImportResult.Builder importResultBuilder) {
    // Build library maps
    Multimap<TargetKey, BlazeJarLibrary> targetKeyToLibrary = ArrayListMultimap.create();
    Map<String, BlazeJarLibrary> jdepsPathToLibrary = Maps.newHashMap();

    // Add any output jars from source rules
    targetKeyToLibrary.putAll(workspaceBuilder.outputJarsFromSourceTargets);
    for (BlazeJarLibrary library : workspaceBuilder.outputJarsFromSourceTargets.values()) {
      addLibraryToJdeps(jdepsPathToLibrary, library);
    }

    for (TargetIdeInfo target : libraryTargets) {
      JavaIdeInfo javaIdeInfo = target.getJavaIdeInfo();
      if (javaIdeInfo == null) {
        continue;
      }

      for (LibraryArtifact jar : javaIdeInfo.getJars()) {
        BlazeJarLibrary library = new BlazeJarLibrary(jar, target.getKey());
        targetKeyToLibrary.put(target.getKey(), library);
        addLibraryToJdeps(jdepsPathToLibrary, library);
      }
    }

    // Preserve classpath order. Add leaf level dependencies first and work the way up. This
    // prevents conflicts when a JAR repackages it's dependencies. In such a case we prefer to
    // resolve symbols from the original JAR rather than the repackaged version.
    // Using accessOrdered LinkedHashMap because jars that are present in `workspaceBuilder.jdeps`
    // and in `workspaceBuilder.directDeps`, we want to treat it as a directDep
    Map<LibraryKey, BlazeJarLibrary> result =
        new LinkedHashMap<>(
            /* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);

    // Collect jars from jdep references
    for (String jdepsPath : workspaceBuilder.jdeps) {
      ArtifactLocation artifact =
          ExecutionPathHelper.parse(workspaceRoot, buildSystemProvider, jdepsPath);
      if (sourceFilter.jdepsPathsForExcludedJars.contains(artifact.getRelativePath())) {
        continue;
      }
      BlazeJarLibrary library = jdepsPathToLibrary.get(artifact.getRelativePath());
      if (library == null) {
        // It's in the target's jdeps, but our aspect never attached to the target building it.
        // Perhaps it's an implicit dependency, or not referenced in an attribute we propagate
        // along. Make a best-effort attempt to add it to the project anyway.
        ArtifactLocation srcJar = guessSrcJarLocation(artifact);
        ImmutableList<ArtifactLocation> srcJars =
            srcJar != null ? ImmutableList.of(srcJar) : ImmutableList.of();
        library =
            new BlazeJarLibrary(
                new LibraryArtifact(artifact, null, srcJars), /* targetKey= */ null);
      }
      result.put(library.key, library);
    }

    // Collect jars referenced by direct deps from your working set
    for (TargetKey deps : workspaceBuilder.directDeps) {
      for (BlazeJarLibrary library : targetKeyToLibrary.get(deps)) {
        result.put(library.key, library);
      }
    }

    // Collect generated jars from source rules
    for (BlazeJarLibrary library : workspaceBuilder.generatedJarsFromSourceTargets) {
      result.put(library.key, library);
    }

    // Filter out any libraries corresponding to empty jars
    return EmptyLibrary.removeEmptyLibraries(
        project, context, artifactLocationDecoder, result, oldSyncState, importResultBuilder);
  }

  private void addLibraryToJdeps(
      Map<String, BlazeJarLibrary> jdepsPathToLibrary, BlazeJarLibrary library) {
    LibraryArtifact libraryArtifact = library.libraryArtifact;
    ArtifactLocation interfaceJar = libraryArtifact.getInterfaceJar();
    if (interfaceJar != null) {
      jdepsPathToLibrary.put(interfaceJar.getRelativePath(), library);
    }
    ArtifactLocation classJar = libraryArtifact.getClassJar();
    if (classJar != null) {
      jdepsPathToLibrary.put(classJar.getRelativePath(), library);
    }
  }

  private void addTargetAsSource(
      WorkspaceBuilder workspaceBuilder,
      TargetIdeInfo target,
      Collection<ArtifactLocation> javaSources) {
    JavaIdeInfo javaIdeInfo = target.getJavaIdeInfo();
    if (javaIdeInfo == null) {
      return;
    }

    TargetKey targetKey = target.getKey();
    Collection<String> jars = jdepsMap.getDependenciesForTarget(targetKey);
    if (jars != null) {
      // TODO (b/242871251): switch back to jars when we are able to access these -kt-ijar.jar from
      // provider. Otherwise they fall back to LocalArtifact since they cannot be accessed and make
      // loading time longer.
      workspaceBuilder.jdeps.addAll(
          jars.stream()
              .filter(jar -> !jar.contains("-kt-ijar.jar") && !jar.contains("-kt-src.jar"))
              .collect(toImmutableList()));
    }

    // Add all deps if this target is in the current working set
    if (workingSet == null || workingSet.isTargetInWorkingSet(target)) {
      // Add self, so we pick up our own gen jars if in working set
      workspaceBuilder.directDeps.add(targetKey);
      for (Dependency dep : target.getDependencies()) {
        if (dep.getDependencyType() != DependencyType.COMPILE_TIME) {
          continue;
        }
        // forward deps from java proto_library aspect targets
        TargetIdeInfo depTarget = targetMap.get(dep.getTargetKey());
        if (depTarget != null
            && JavaBlazeRules.getJavaProtoLibraryKinds().contains(depTarget.getKind())) {
          workspaceBuilder.directDeps.addAll(
              depTarget.getDependencies().stream().map(Dependency::getTargetKey).collect(toList()));
        } else {
          workspaceBuilder.directDeps.add(dep.getTargetKey());
        }
      }
    }

    for (ArtifactLocation artifactLocation : javaSources) {
      if (artifactLocation.isSource()) {
        duplicateSourceDetector.add(targetKey, artifactLocation);
        workspaceBuilder.sourceArtifacts.add(new SourceArtifact(targetKey, artifactLocation));
        workspaceBuilder.addedSourceFiles.add(artifactLocation);
      }
    }

    ArtifactLocation manifest = javaIdeInfo.getPackageManifest();
    if (manifest != null) {
      workspaceBuilder.javaPackageManifests.put(targetKey, manifest);
    }
    for (LibraryArtifact libraryArtifact : javaIdeInfo.getJars()) {
      ArtifactLocation classJar = libraryArtifact.getClassJar();
      if (classJar != null) {
        workspaceBuilder.buildOutputJars.add(classJar);
      }
    }
    if (augmenters.stream().allMatch(argument -> argument.shouldAttachGenJar(target))) {
      javaIdeInfo.getGeneratedJars().stream()
          .map(jar -> new BlazeJarLibrary(jar, targetKey))
          .forEach(workspaceBuilder.generatedJarsFromSourceTargets::add);
    }
    if (javaIdeInfo.getFilteredGenJar() != null) {
      workspaceBuilder.generatedJarsFromSourceTargets.add(
          new BlazeJarLibrary(javaIdeInfo.getFilteredGenJar(), targetKey));
    }
    if (JavaSourceFilter.isJavaProtoTarget(target)) {
      // add generated jars from all proto library targets in the project
      javaIdeInfo.getJars().stream()
          .map(jar -> new BlazeJarLibrary(jar, targetKey))
          .forEach(workspaceBuilder.generatedJarsFromSourceTargets::add);
    }

    for (BlazeJavaSyncAugmenter augmenter : augmenters) {
      augmenter.addJarsForSourceTarget(
          workspaceLanguageSettings,
          projectViewSet,
          target,
          workspaceBuilder.outputJarsFromSourceTargets.get(targetKey),
          workspaceBuilder.generatedJarsFromSourceTargets);
    }

    javaIdeInfo.getPluginProcessorJars().stream()
        .map(LibraryArtifact::jarForIntellijLibrary)
        .forEach(workspaceBuilder.pluginProcessorJars::add);
  }

  @Nullable
  private String findSourceVersion(TargetMap targetMap) {
    return targetMap.targets().stream()
        .filter(t -> t.getJavaToolchainIdeInfo() != null)
        .map(t -> t.getJavaToolchainIdeInfo().getSourceVersion())
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private static class WorkspaceBuilder {
    Set<String> jdeps = Sets.newHashSet();
    Set<TargetKey> directDeps = Sets.newHashSet();
    Set<ArtifactLocation> addedSourceFiles = Sets.newHashSet();
    Multimap<TargetKey, BlazeJarLibrary> outputJarsFromSourceTargets = ArrayListMultimap.create();
    List<BlazeJarLibrary> generatedJarsFromSourceTargets = Lists.newArrayList();
    List<ArtifactLocation> buildOutputJars = Lists.newArrayList();
    List<SourceArtifact> sourceArtifacts = Lists.newArrayList();
    Map<TargetKey, ArtifactLocation> javaPackageManifests = Maps.newHashMap();
    final Set<ArtifactLocation> pluginProcessorJars = Sets.newHashSet();
  }

  /**
   * Uses a filename heuristic to guess the location of a source jar corresponding to the given
   * output jar.
   */
  @Nullable
  private static ArtifactLocation guessSrcJarLocation(ArtifactLocation outputJar) {
    String srcJarRelPath = guessSrcJarRelativePath(outputJar.getRelativePath());
    if (srcJarRelPath == null) {
      return null;
    }
    // we don't check whether the source jar actually exists, to avoid unnecessary file system
    // operations
    return ArtifactLocation.Builder.copy(outputJar).setRelativePath(srcJarRelPath).build();
  }

  @Nullable
  private static String guessSrcJarRelativePath(String relPath) {
    if (relPath.endsWith("-hjar.jar")) {
      return relPath.substring(0, relPath.length() - "-hjar.jar".length()) + "-src.jar";
    }
    if (relPath.endsWith("-ijar.jar")) {
      return relPath.substring(0, relPath.length() - "-ijar.jar".length()) + "-src.jar";
    }
    return null;
  }
}
