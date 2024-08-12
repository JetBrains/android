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
package com.google.idea.blaze.java.sync.source;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.util.PackagePrefixCalculator;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This is a utility class for calculating the java sources and their package prefixes given a
 * module and its Blaze {@link ArtifactLocation} list.
 */
public final class SourceDirectoryCalculator {

  private static final Splitter PACKAGE_SPLITTER = Splitter.on('.');
  private static final Splitter PATH_SPLITTER = Splitter.on('/');
  private static final Joiner PACKAGE_JOINER = Joiner.on('.');
  private static final Joiner PATH_JOINER = Joiner.on('/');

  private static final JavaPackageReader generatedFileJavaPackageReader =
      new FilePathJavaPackageReader();
  private final ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();

  public ImmutableList<BlazeContentEntry> calculateContentEntries(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ArtifactLocationDecoder artifactLocationDecoder,
      ImportRoots importRoots,
      Collection<SourceArtifact> sources,
      Map<TargetKey, ArtifactLocation> javaPackageManifests) {

    ManifestFilePackageReader manifestFilePackageReader =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("ReadPackageManifests", EventType.Other));
              Map<TargetKey, Map<ArtifactLocation, String>> manifestMap =
                  PackageManifestReader.getInstance()
                      .readPackageManifestFiles(
                          project,
                          childContext,
                          artifactLocationDecoder,
                          javaPackageManifests,
                          FetchExecutor.EXECUTOR);
              return new ManifestFilePackageReader(manifestMap);
            });

    final List<JavaPackageReader> javaPackageReaders =
        Lists.newArrayList(
            manifestFilePackageReader,
            JavaSourcePackageReader.getInstance(),
            generatedFileJavaPackageReader);

    Collection<SourceArtifact> nonGeneratedSources = filterGeneratedArtifacts(sources);

    // Sort artifacts and excludes into their respective workspace paths
    Multimap<WorkspacePath, SourceArtifact> sourcesUnderDirectoryRoot =
        sortArtifactLocationsByRootDirectory(importRoots, nonGeneratedSources);

    List<BlazeContentEntry> result = Lists.newArrayList();
    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("CalculateSourceDirectories", EventType.Other));
          for (WorkspacePath workspacePath : importRoots.rootDirectories()) {
            File contentRoot = workspaceRoot.fileForPath(workspacePath);
            ImmutableList<BlazeSourceDirectory> sourceDirectories =
                calculateSourceDirectoriesForContentRoot(
                    context,
                    workspaceRoot,
                    artifactLocationDecoder,
                    workspacePath,
                    sourcesUnderDirectoryRoot.get(workspacePath),
                    javaPackageReaders);
            result.add(new BlazeContentEntry(contentRoot, sourceDirectories));
          }
          result.sort(Comparator.comparing(lhs -> lhs.contentRoot));
        });
    return ImmutableList.copyOf(result);
  }

  private Collection<SourceArtifact> filterGeneratedArtifacts(
      Collection<SourceArtifact> artifactLocations) {
    return artifactLocations.stream()
        .filter(sourceArtifact -> sourceArtifact.artifactLocation.isSource())
        .collect(Collectors.toList());
  }

  private static Multimap<WorkspacePath, SourceArtifact> sortArtifactLocationsByRootDirectory(
      ImportRoots importRoots, Collection<SourceArtifact> sources) {

    Multimap<WorkspacePath, SourceArtifact> result = ArrayListMultimap.create();

    for (SourceArtifact sourceArtifact : sources) {
      String sourcePath = sourceArtifact.artifactLocation.getRelativePath();
      if (importRoots.excludeDirectories().stream()
          .anyMatch(excluded -> isUnderRootDirectory(excluded, sourcePath))) {
        continue;
      }
      importRoots.rootDirectories().stream()
          .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, sourcePath))
          .findFirst()
          .ifPresent(foundWorkspacePath -> result.put(foundWorkspacePath, sourceArtifact));
    }
    return result;
  }

  private static boolean isUnderRootDirectory(WorkspacePath rootDirectory, String relativePath) {
    if (rootDirectory.isWorkspaceRoot()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
        && (relativePath.length() == rootDirectoryString.length()
            || (relativePath.charAt(rootDirectoryString.length()) == '/'));
  }

  /** Calculates all source directories for a single content root. */
  private ImmutableList<BlazeSourceDirectory> calculateSourceDirectoriesForContentRoot(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspacePath directoryRoot,
      Collection<SourceArtifact> sourceArtifacts,
      Collection<JavaPackageReader> javaPackageReaders) {

    // Split out java-like files
    Predicate<ArtifactLocation> isSourceFile = JavaLikeLanguage.getSourceFileMatcher();
    List<SourceArtifact> javaArtifacts = Lists.newArrayList();
    for (SourceArtifact sourceArtifact : sourceArtifacts) {
      if (isSourceFile.test(sourceArtifact.artifactLocation)) {
        javaArtifacts.add(sourceArtifact);
      }
    }

    List<BlazeSourceDirectory> result = Lists.newArrayList();

    // Add java source directories
    calculateJavaSourceDirectories(
        context,
        workspaceRoot,
        artifactLocationDecoder,
        directoryRoot,
        javaArtifacts,
        javaPackageReaders,
        result);

    if (result.isEmpty()) {
      // if there are no nested source directories, then mark the content root as a source directory
      return ImmutableList.of(
          BlazeSourceDirectory.builder(workspaceRoot.fileForPath(directoryRoot))
              .setPackagePrefix(PackagePrefixCalculator.packagePrefixOf(directoryRoot))
              .setGenerated(false)
              .build());
    }

    result.sort(BlazeSourceDirectory.COMPARATOR);
    return ImmutableList.copyOf(result);
  }

  /** Adds the java source directories. */
  private void calculateJavaSourceDirectories(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspacePath directoryRoot,
      Collection<SourceArtifact> javaArtifacts,
      Collection<JavaPackageReader> javaPackageReaders,
      Collection<BlazeSourceDirectory> result) {

    List<SourceRoot> sourceRootsPerFile = Lists.newArrayList();

    // Get java sources
    List<ListenableFuture<SourceRoot>> sourceRootFutures = Lists.newArrayList();
    for (final SourceArtifact sourceArtifact : javaArtifacts) {
      ListenableFuture<SourceRoot> future =
          executorService.submit(
              () ->
                  sourceRootForJavaSource(
                      context, artifactLocationDecoder, sourceArtifact, javaPackageReaders));
      sourceRootFutures.add(future);
    }
    try {
      for (SourceRoot sourceRoot : Futures.allAsList(sourceRootFutures).get()) {
        if (sourceRoot != null) {
          sourceRootsPerFile.add(sourceRoot);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      throw new IllegalStateException("Could not read sources", e);
    }

    // Sort source roots into their respective directories
    Map<WorkspacePath, Multiset<SourceRoot>> sourceDirectoryToSourceRoots = new HashMap<>();
    for (SourceRoot sourceRoot : sourceRootsPerFile) {
      sourceDirectoryToSourceRoots
          .computeIfAbsent(sourceRoot.workspacePath, k -> HashMultiset.create())
          .add(sourceRoot);
    }

    // Create a mapping from directory to package prefix
    Map<WorkspacePath, SourceRoot> workspacePathToSourceRoot = Maps.newHashMap();
    for (WorkspacePath workspacePath : sourceDirectoryToSourceRoots.keySet()) {
      Multiset<SourceRoot> sources = sourceDirectoryToSourceRoots.get(workspacePath);
      Multiset<String> packages = HashMultiset.create();

      for (Multiset.Entry<SourceRoot> entry : sources.entrySet()) {
        packages.setCount(entry.getElement().packagePrefix, entry.getCount());
      }

      final String directoryPackagePrefix;
      // Common case -- all source files agree on a single package
      if (packages.elementSet().size() == 1) {
        directoryPackagePrefix = packages.elementSet().iterator().next();
      } else {
        String preferredPackagePrefix = PackagePrefixCalculator.packagePrefixOf(workspacePath);
        directoryPackagePrefix = pickMostFrequentlyOccurring(packages, preferredPackagePrefix);
      }

      SourceRoot candidateRoot = new SourceRoot(workspacePath, directoryPackagePrefix);
      workspacePathToSourceRoot.put(workspacePath, candidateRoot);
    }

    // First, create a graph of the directory structure from root to each source file
    Map<WorkspacePath, SourceRootDirectoryNode> sourceRootDirectoryNodeMap = Maps.newHashMap();
    SourceRootDirectoryNode rootNode = new SourceRootDirectoryNode(directoryRoot, null);
    sourceRootDirectoryNodeMap.put(directoryRoot, rootNode);
    for (SourceRoot sourceRoot : workspacePathToSourceRoot.values()) {
      final String sourcePathRelativeToDirectoryRoot =
          sourcePathRelativeToDirectoryRoot(directoryRoot, sourceRoot.workspacePath);
      List<String> pathComponents =
          !Strings.isNullOrEmpty(sourcePathRelativeToDirectoryRoot)
              ? PATH_SPLITTER.splitToList(sourcePathRelativeToDirectoryRoot)
              : ImmutableList.of();
      SourceRootDirectoryNode previousNode = rootNode;
      for (int i = 0; i < pathComponents.size(); ++i) {
        final WorkspacePath workspacePath =
            getWorkspacePathFromPathComponents(directoryRoot, pathComponents, i + 1);
        SourceRootDirectoryNode node = sourceRootDirectoryNodeMap.get(workspacePath);
        if (node == null) {
          node = new SourceRootDirectoryNode(workspacePath, pathComponents.get(i));
          sourceRootDirectoryNodeMap.put(workspacePath, node);
          previousNode.children.add(node);
        }
        previousNode = node;
      }
    }

    // Add package prefix votes at each directory node
    for (SourceRoot sourceRoot : workspacePathToSourceRoot.values()) {
      final String sourcePathRelativeToDirectoryRoot =
          sourcePathRelativeToDirectoryRoot(directoryRoot, sourceRoot.workspacePath);

      List<String> packageComponents = PACKAGE_SPLITTER.splitToList(sourceRoot.packagePrefix);
      List<String> pathComponents =
          !Strings.isNullOrEmpty(sourcePathRelativeToDirectoryRoot)
              ? PATH_SPLITTER.splitToList(sourcePathRelativeToDirectoryRoot)
              : ImmutableList.of();
      int packageIndex = packageComponents.size();
      int pathIndex = pathComponents.size();
      while (pathIndex >= 0 && packageIndex >= 0) {
        final WorkspacePath workspacePath =
            getWorkspacePathFromPathComponents(directoryRoot, pathComponents, pathIndex);

        SourceRootDirectoryNode node = sourceRootDirectoryNodeMap.get(workspacePath);

        String packagePrefix = PACKAGE_JOINER.join(packageComponents.subList(0, packageIndex));

        // If this is the source root containing Java files, we *have* to pick its package prefix
        // Otherwise just add a vote
        if (sourceRoot.workspacePath.equals(workspacePath)) {
          node.forcedPackagePrefix = packagePrefix;
        } else {
          node.packagePrefixVotes.add(packagePrefix);
        }

        String pathComponent = pathIndex > 0 ? pathComponents.get(pathIndex - 1) : "";
        String packageComponent = packageIndex > 0 ? packageComponents.get(packageIndex - 1) : "";
        if (!pathComponent.equals(packageComponent)) {
          break;
        }

        --packageIndex;
        --pathIndex;
      }
    }

    Map<WorkspacePath, SourceRoot> sourceRoots = Maps.newHashMap();
    SourceRootDirectoryNode root = sourceRootDirectoryNodeMap.get(directoryRoot);
    visitDirectoryNode(sourceRoots, root, null);

    for (SourceRoot sourceRoot : sourceRoots.values()) {
      result.add(
          BlazeSourceDirectory.builder(workspaceRoot.fileForPath(sourceRoot.workspacePath))
              .setPackagePrefix(sourceRoot.packagePrefix)
              .setGenerated(false)
              .build());
    }
  }

  private static String sourcePathRelativeToDirectoryRoot(
      WorkspacePath directoryRoot, WorkspacePath workspacePath) {
    int directoryRootLength = directoryRoot.relativePath().length();
    String relativePath = workspacePath.relativePath();
    final String relativeSourcePath;
    if (relativePath.length() > directoryRootLength) {
      if (directoryRootLength > 0) {
        relativeSourcePath = relativePath.substring(directoryRootLength + 1);
      } else {
        relativeSourcePath = relativePath;
      }
    } else {
      relativeSourcePath = "";
    }
    return relativeSourcePath;
  }

  private static WorkspacePath getWorkspacePathFromPathComponents(
      WorkspacePath directoryRoot, List<String> pathComponents, int pathIndex) {
    String directoryRootRelativePath = PATH_JOINER.join(pathComponents.subList(0, pathIndex));
    final WorkspacePath workspacePath;
    if (directoryRootRelativePath.isEmpty()) {
      workspacePath = directoryRoot;
    } else if (directoryRoot.isWorkspaceRoot()) {
      workspacePath = new WorkspacePath(directoryRootRelativePath);
    } else {
      workspacePath =
          new WorkspacePath(
              PATH_JOINER.join(directoryRoot.relativePath(), directoryRootRelativePath));
    }
    return workspacePath;
  }

  private static void visitDirectoryNode(
      Map<WorkspacePath, SourceRoot> sourceRoots,
      SourceRootDirectoryNode node,
      @Nullable String parentCompatiblePackagePrefix) {
    String packagePrefix =
        node.forcedPackagePrefix != null
            ? node.forcedPackagePrefix
            : pickMostFrequentlyOccurring(
                node.packagePrefixVotes,
                PackagePrefixCalculator.packagePrefixOf(node.workspacePath));
    packagePrefix = packagePrefix != null ? packagePrefix : parentCompatiblePackagePrefix;
    if (packagePrefix != null && !packagePrefix.equals(parentCompatiblePackagePrefix)) {
      sourceRoots.put(node.workspacePath, new SourceRoot(node.workspacePath, packagePrefix));
    }
    for (SourceRootDirectoryNode child : node.children) {
      String compatiblePackagePrefix = null;
      if (packagePrefix != null) {
        compatiblePackagePrefix =
            Strings.isNullOrEmpty(packagePrefix)
                ? child.directoryName
                : packagePrefix + "." + child.directoryName;
      }
      visitDirectoryNode(sourceRoots, child, compatiblePackagePrefix);
    }
  }

  @Nullable
  private static <T> T pickMostFrequentlyOccurring(Multiset<T> set, String prefer) {
    T best = null;
    int bestCount = 0;

    for (T candidate : set.elementSet()) {
      int candidateCount = set.count(candidate);
      if (candidateCount > bestCount || (candidateCount == bestCount && candidate.equals(prefer))) {
        best = candidate;
        bestCount = candidateCount;
      }
    }
    return best;
  }

  @Nullable
  private SourceRoot sourceRootForJavaSource(
      BlazeContext context,
      ArtifactLocationDecoder decoder,
      SourceArtifact sourceArtifact,
      Collection<JavaPackageReader> javaPackageReaders) {

    String declaredPackage = null;
    for (JavaPackageReader reader : javaPackageReaders) {
      declaredPackage = reader.getDeclaredPackageOfJavaFile(context, decoder, sourceArtifact);
      if (declaredPackage != null) {
        break;
      }
    }
    if (declaredPackage == null) {
      context.output(
          PrintOutput.log(
              "Failed to inspect the package name of java source file: "
                  + sourceArtifact.artifactLocation));
      return null;
    }
    String parentPath = new File(sourceArtifact.artifactLocation.getRelativePath()).getParent();
    return new SourceRoot(new WorkspacePath(Strings.nullToEmpty(parentPath)), declaredPackage);
  }

  static class SourceRoot {
    final WorkspacePath workspacePath;
    final String packagePrefix;

    SourceRoot(WorkspacePath workspacePath, String packagePrefix) {
      this.workspacePath = workspacePath;
      this.packagePrefix = packagePrefix;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SourceRoot that = (SourceRoot) o;
      return Objects.equal(workspacePath, that.workspacePath)
          && Objects.equal(packagePrefix, that.packagePrefix);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(workspacePath, packagePrefix);
    }

    @Override
    public String toString() {
      return "SourceRoot {"
          + '\n'
          + "  workspacePath: "
          + workspacePath
          + '\n'
          + "  packagePrefix: "
          + packagePrefix
          + '\n'
          + '}';
    }
  }

  static class SourceRootDirectoryNode {
    final WorkspacePath workspacePath;
    @Nullable final String directoryName;
    final Set<SourceRootDirectoryNode> children = Sets.newHashSet();
    final Multiset<String> packagePrefixVotes = HashMultiset.create();
    String forcedPackagePrefix;

    SourceRootDirectoryNode(WorkspacePath workspacePath, @Nullable String directoryName) {
      this.workspacePath = workspacePath;
      this.directoryName = directoryName;
    }
  }
}
