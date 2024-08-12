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
package com.google.idea.blaze.cpp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.io.File;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** A clustering of "equivalent" Blaze targets for creating {@link OCResolveConfiguration}. */
final class BlazeResolveConfiguration {

  private final Project project;
  private final BlazeResolveConfigurationData configurationData;

  private final String displayNameIdentifier;
  private final ImmutableList<TargetKey> targets;
  private final ImmutableMap<TargetKey, ImmutableList<VirtualFile>> targetSources;

  private BlazeResolveConfiguration(
      Project project,
      BlazeResolveConfigurationData configurationData,
      String displayName,
      ImmutableList<TargetKey> targets,
      ImmutableMap<TargetKey, ImmutableList<VirtualFile>> targetSources) {
    this.project = project;
    this.configurationData = configurationData;
    this.displayNameIdentifier = displayName;
    this.targets = ImmutableList.copyOf(targets);
    this.targetSources = targetSources;
  }

  static BlazeResolveConfiguration createForTargets(
      Project project,
      BlazeProjectData blazeProjectData,
      BlazeResolveConfigurationData configurationData,
      Collection<TargetKey> targets) {
    return new BlazeResolveConfiguration(
        project,
        configurationData,
        computeDisplayName(targets),
        ImmutableList.copyOf(targets),
        computeTargetToSources(blazeProjectData, targets));
  }

  Collection<TargetKey> getTargets() {
    return targets;
  }

  private static String computeDisplayName(Collection<TargetKey> targets) {
    TargetKey minTargetKey = targets.stream().min(TargetKey::compareTo).orElse(null);
    Preconditions.checkNotNull(minTargetKey);
    String minTarget = minTargetKey.toString();
    if (targets.size() == 1) {
      return minTarget;
    } else {
      return String.format("%s and %d other target(s)", minTarget, targets.size() - 1);
    }
  }

  public String getDisplayName() {
    return displayNameIdentifier;
  }

  boolean isEquivalentConfigurations(BlazeResolveConfiguration other) {
    return configurationData.equals(other.configurationData)
        && displayNameIdentifier.equals(other.displayNameIdentifier)
        && targets.equals(other.targets)
        && targetSources.equals(other.targetSources);
  }

  @Nullable
  OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile);
    }

    if (OCFileTypeHelpers.isHeaderFile(fileName)) {
      return getLanguageKind(getSourceFileForHeaderFile(sourceOrHeaderFile));
    }

    return null;
  }

  private OCLanguageKind getLanguageKind(@Nullable VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Nullable
  private VirtualFile getSourceFileForHeaderFile(VirtualFile headerFile) {
    Collection<VirtualFile> roots =
        OCImportGraph.getInstance(project).getAllHeaderRoots(headerFile);

    final String headerNameWithoutExtension = headerFile.getNameWithoutExtension();
    for (VirtualFile root : roots) {
      if (root.getNameWithoutExtension().equals(headerNameWithoutExtension)) {
        return root;
      }
    }
    return null;
  }

  private static OCLanguageKind getMaximumLanguageKind() {
    return CLanguageKind.CPP;
  }

  @VisibleForTesting
  List<ExecutionRootPath> getLibraryHeadersRootsInternal() {
    ImmutableList.Builder<ExecutionRootPath> roots = ImmutableList.builder();
    roots.addAll(configurationData.transitiveQuoteIncludeDirectories);
    roots.addAll(configurationData.transitiveIncludeDirectories);
    roots.addAll(configurationData.transitiveSystemIncludeDirectories);
    return roots.build();
  }

  @VisibleForTesting
  ImmutableCollection<String> getTargetCopts() {
    return configurationData.localCopts;
  }

  BlazeCompilerSettings getCompilerSettings() {
    return configurationData.compilerSettings;
  }

  ImmutableList<VirtualFile> getSources(TargetKey targetKey) {
    return targetSources.get(targetKey);
  }

  private static ImmutableMap<TargetKey, ImmutableList<VirtualFile>> computeTargetToSources(
      BlazeProjectData blazeProjectData, Collection<TargetKey> targets) {
    ImmutableMap.Builder<TargetKey, ImmutableList<VirtualFile>> targetSourcesBuilder =
        ImmutableMap.builder();
    for (TargetKey targetKey : targets) {
      targetSourcesBuilder.put(targetKey, computeSources(blazeProjectData, targetKey));
    }
    return targetSourcesBuilder.build();
  }

  private static ImmutableList<VirtualFile> computeSources(
      BlazeProjectData blazeProjectData, TargetKey targetKey) {
    ImmutableList.Builder<VirtualFile> builder = ImmutableList.builder();

    TargetIdeInfo targetIdeInfo = blazeProjectData.getTargetMap().get(targetKey);
    if (targetIdeInfo.getcIdeInfo() == null) {
      return ImmutableList.of();
    }

    for (ArtifactLocation sourceArtifact : targetIdeInfo.getSources()) {
      File file = blazeProjectData.getArtifactLocationDecoder().decode(sourceArtifact);
      VirtualFile vf = VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(file);
      if (vf == null) {
        continue;
      }
      if (!OCFileTypeHelpers.isSourceFile(vf.getName())) {
        continue;
      }
      builder.add(vf);
    }
    return builder.build();
  }
}
