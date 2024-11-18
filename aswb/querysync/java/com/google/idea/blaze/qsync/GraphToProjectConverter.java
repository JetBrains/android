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
package com.google.idea.blaze.qsync;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparingInt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.RuleKinds;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.LibraryOrBuilder;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType;
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher;
import com.google.idea.blaze.qsync.query.PackageSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Converts a {@link BuildGraphData} instance into a project proto. */
public class GraphToProjectConverter {

  private final PackageReader packageReader;
  private final Predicate<Path> fileExistenceCheck;
  private final Context<?> context;

  private final ProjectDefinition projectDefinition;
  private final ListeningExecutorService executor;
  private final Supplier<Boolean> useNewResDirLogic;
  private final Supplier<Boolean> guessAndroidResPackages;

  public GraphToProjectConverter(
      PackageReader packageReader,
      Path workspaceRoot,
      Context<?> context,
      ProjectDefinition projectDefinition,
      ListeningExecutorService executor,
      Supplier<Boolean> useNewResDirLogic,
      Supplier<Boolean> guessAndroidResPackages) {
    this.packageReader = packageReader;
    this.fileExistenceCheck = p -> Files.isRegularFile(workspaceRoot.resolve(p));
    this.context = context;
    this.projectDefinition = projectDefinition;
    this.executor = executor;
    this.useNewResDirLogic = useNewResDirLogic;
    this.guessAndroidResPackages = guessAndroidResPackages;
  }

  @VisibleForTesting
  public GraphToProjectConverter(
      PackageReader packageReader,
      Predicate<Path> fileExistenceCheck,
      Context<?> context,
      ProjectDefinition projectDefinition,
      ListeningExecutorService executor) {
    this.packageReader = packageReader;
    this.fileExistenceCheck = fileExistenceCheck;
    this.context = context;
    this.projectDefinition = projectDefinition;
    this.executor = executor;
    this.useNewResDirLogic = Suppliers.ofInstance(true);
    this.guessAndroidResPackages = Suppliers.ofInstance(false);
  }

  /**
   * Calculates the source roots for all files in the project. While the vast majority of projects
   * will fall into the standard java/javatest packages, there are projects that do not conform with
   * this convention.
   *
   * <p>Mapping blaze projects to .imls will always be an aproximation, because blaze does not
   * impose any restrictions on how the source files are on disk. IntelliJ does.
   *
   * <p>The code in .imls is organized as follows (simplified view):
   *
   * <p>A project is a collection of modules. (For now we only have one module, so we do not model
   * dependencies yet). A module is a collection of content roots. A content root, is a directory
   * were code of different kind is located. Inside a content root there can be different source
   * roots. A source root is a directory inside the content root, that has a coherent group of
   * source files. A source root can be test only. Source roots can be nested. These source files
   * *must* be organized in a package-friendly directory structure. Most importantly, the directory
   * structure does not have to start at the root of the package, for that source roots can have a
   * package prefix that is a applied to the inner structure.
   *
   * <p>The algorithm implemented here makes one assumption over the code. All source files within
   * the same blaze package that are children of other source files, are correctly structured. This
   * is evidently not true for the general case, but even the most complex projects in our
   * repository follow this rule. And this is a rule, easy to workaround by a user if it doesn't
   * hold on their project.
   *
   * <pre>
   * The algorithm works as follows:
   *   1.- The top-most source files (most one per directory) is chosen per blaze package.
   *   2.- Read the actual package of each java file, and use that as the directories prefix.
   *   3.- Split all the packages by content root.
   *   4.- Merge compatible packages. This is a heuristic step, where each source root
   *       is bubbled up as far as possible, merging compatible siblings. For a better description
   *       see the comment on that function.
   * </pre>
   *
   * @param srcFiles all the files that should be included.
   * @param packages the BUILD files to create source roots for.
   * @return the content roots in the following form : Content Root -> Source Root -> package
   *     prefix. A content root contains multiple source roots, each one with a package prefix.
   */
  @VisibleForTesting
  public ImmutableMap<Path, ImmutableMap<Path, String>> calculateJavaRootSources(
      Collection<Path> srcFiles, PackageSet packages) throws BuildException {

    // A map from package to the file chosen to represent it.
    ImmutableList<Path> chosenFiles = chooseTopLevelFiles(srcFiles, packages);

    // A map from a directory to its prefix
    ImmutableMap<Path, String> prefixes = readPackages(chosenFiles);

    // All packages split by their content roots
    ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix = splitByRoot(prefixes);

    // Merging packages that can share the same prefix
    rootToPrefix = mergeCompatibleSourceRoots(rootToPrefix);

    return rootToPrefix;
  }

  /**
   * Calculates directories containing non-java source files.
   *
   * @param srcFiles all the sources in the project, excluding java.
   * @return mapping of content roots (project includes) to directories (relative to the content
   *     root) containing proto files.
   */
  @VisibleForTesting
  public ImmutableMultimap<Path, Path> nonJavaSourceFolders(Collection<Path> srcFiles) {
    ImmutableMultimap.Builder<Path, Path> contentRootToSource = ImmutableMultimap.builder();

    // Calculate all source directories.
    ImmutableSet<Path> srcDirectories =
        srcFiles.stream().map(Path::getParent).filter(Objects::nonNull).collect(toImmutableSet());

    // Separate by project includes
    for (Path srcDir : srcDirectories) {
      projectDefinition
          .getIncludingContentRoot(srcDir)
          .ifPresent(
              contentRoot -> contentRootToSource.put(contentRoot, contentRoot.relativize(srcDir)));
    }

    return contentRootToSource.build();
  }

  @VisibleForTesting
  ImmutableMap<Path, ImmutableMap<Path, String>> splitByRoot(Map<Path, String> prefixes) {
    ImmutableMap.Builder<Path, ImmutableMap<Path, String>> split = ImmutableMap.builder();
    for (Path root : projectDefinition.projectIncludes()) {
      ImmutableMap.Builder<Path, String> inRoot = ImmutableMap.builder();
      for (Entry<Path, String> pkg : prefixes.entrySet()) {
        Path rel = pkg.getKey();
        if (root.toString().isEmpty() || rel.startsWith(root)) {
          Path relToRoot = root.relativize(rel);
          inRoot.put(relToRoot, pkg.getValue());
        }
      }
      split.put(root, inRoot.buildKeepingLast());
    }
    return split.buildKeepingLast();
  }

  private ImmutableMap<Path, String> readPackages(Collection<Path> files) throws BuildException {
    try {
      long now = System.currentTimeMillis();
      ArrayList<Path> allFiles = new ArrayList<>(files);
      List<String> allPackages = packageReader.readPackages(allFiles);
      long elapsed = System.currentTimeMillis() - now;
      context.output(PrintOutput.log("%-10d Java files read (%d ms)", files.size(), elapsed));

      ImmutableMap.Builder<Path, String> prefixes = ImmutableMap.builder();
      Iterator<Path> i = allFiles.iterator();
      Iterator<String> j = allPackages.iterator();
      while (i.hasNext() && j.hasNext()) {
        prefixes.put(i.next().getParent(), j.next());
      }
      return prefixes.buildOrThrow();
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  @VisibleForTesting
  protected ImmutableList<Path> chooseTopLevelFiles(Collection<Path> files, PackageSet packages)
      throws BuildException {

    ImmutableListMultimap<Path, Path> filesByPath = Multimaps.index(files, Path::getParent);
    // A map from directory to the candidate chosen to represent that directory
    // We filter out non-existent files, but without checking for the existence of all files as
    // that slows things down unnecessarily.
    Map<Path, Path> candidates = Maps.newConcurrentMap();
    List<ListenableFuture<?>> futures = Lists.newArrayList();
    for (Path dir : filesByPath.keySet()) {
      futures.add(
          executor.submit(
              () -> {
                // We use a priority queue to find the first element without sorting, since in most
                // cases we only need the first element.
                PriorityQueue<Path> dirFiles =
                    new PriorityQueue<>(Comparator.comparing(Path::getFileName));
                dirFiles.addAll(filesByPath.get(dir));
                Path candidate = dirFiles.poll();
                while (candidate != null && !fileExistenceCheck.test(candidate)) {
                  candidate = dirFiles.poll();
                }
                if (candidate != null) {
                  candidates.put(dir, candidate);
                }
              }));
    }

    try {
      Uninterruptibles.getUninterruptibly(Futures.allAsList(futures));
    } catch (ExecutionException e) {
      throw new BuildException(e);
    }

    // Filter the files that are top level files only
    return candidates.values().stream()
        .filter(file -> isTopLevel(packages, candidates, file))
        .collect(toImmutableList());
  }

  private static boolean isTopLevel(PackageSet packages, Map<Path, Path> candidates, Path file) {
    Path dir = relativeParentOf(file);
    while (dir != null) {
      Path existing = candidates.get(dir);
      if (existing != null && existing != file) {
        return false;
      }
      if (packages.contains(dir)) {
        return true;
      }
      dir = relativeParentOf(dir);
    }
    return false;
  }

  @Nullable
  private static Path relativeParentOf(Path path) {
    Preconditions.checkState(!path.isAbsolute());
    if (path.toString().isEmpty()) {
      return null;
    }
    Path parent = path.getParent();
    return parent == null ? Path.of("") : parent;
  }

  private static String lastSubpackageOf(String pkg) {
    return pkg.substring(pkg.lastIndexOf('.') + 1);
  }

  @Nullable
  private static String parentPackageOf(String pkg) {
    if (pkg.isEmpty()) {
      return null;
    }
    int ix = pkg.lastIndexOf('.');
    return ix == -1 ? "" : pkg.substring(0, ix);
  }

  /**
   * Merges source roots that are compatible. Consider the following example, where source roots are
   * written like "directory" ["prefix"]:
   *
   * <pre>
   *   1.- Two sibling roots:
   *     "a/b/c/d" ["com.google.d"]
   *     "a/b/c/e" ["com.google.e"]
   *   Can be merged to:
   *     "a/b/c" ["com.google"]
   *
   *   2.- Nested roots:
   *     "a/b/c/d" ["com.google.d"]
   *     "a/b/c/d/e" ["com.google.d.e"]
   *   Can be merged to:
   *     "a/b/c" ["com.google"]
   * </pre>
   *
   * This function works by trying to move a source root up as far as possible (until it reaches the
   * content root). When it finds a source root above, there can be two scenarios: a) the parent
   * source root is compatible (like example 2 above), in which case they are merged. b) the parent
   * root is not compatible, in which case it needs to stop there and cannot be moved further up.
   * This is true even if the parent source root is later moved up.
   */
  @VisibleForTesting
  static ImmutableMap<Path, ImmutableMap<Path, String>> mergeCompatibleSourceRoots(
      ImmutableMap<Path, ImmutableMap<Path, String>> srcRoots) {
    ImmutableMap.Builder<Path, ImmutableMap<Path, String>> result = ImmutableMap.builder();
    for (Entry<Path, ImmutableMap<Path, String>> contentRoot : srcRoots.entrySet()) {
      result.put(contentRoot.getKey(), mergeSourceRoots(contentRoot.getValue()));
    }
    return result.buildOrThrow();
  }

  /**
   * Given directory to package mappings known to be true from the source code builds finds the root
   * mappings that are sufficient for the IDE to derive the provided mappings, i.e. having
   *
   * <pre>
   *   java/src/com/google/app => com.google.app
   *   java/src/com/google/lib => com.google.lib
   *   java/src/com/google/sample/else => com.example.else
   * </pre>
   *
   * <p>produces:
   *
   * <pre>
   *   java/src => ""
   *   java/src/com/google/sample => com.example
   * </pre>
   */
  private static ImmutableMap<Path, String> mergeSourceRoots(
      Map<Path, String> expectedDirectoryToPackageMap) {
    final Map<Path, Set<String>> dirWants = addPossibleParentMatches(expectedDirectoryToPackageMap);
    final ImmutableMap<Path, String> dirAllResult =
        chooseFinalMappings(expectedDirectoryToPackageMap, dirWants);
    return selectEssentialMappings(dirAllResult);
  }

  /**
   * Given an unambiguous directory to package mapping that includes intermediate directories
   * selects those root mappings that are required to establish top level mappings and drops any
   * that can be derived from them.
   *
   * <p>i.e.
   *
   * <pre>
   *   src/ => ""
   *   src/com/ => com
   *   src/com/google/ => com.google
   *   src/com/google/lib => com.google.lib
   *   src/com/google/else => smth.else
   * </pre>
   *
   * <p>results in
   *
   * <pre>
   *   src/ => ""
   *   src/com/google/else => smth.else
   * </pre>
   */
  private static ImmutableMap<Path, String> selectEssentialMappings(
      ImmutableMap<Path, String> dirAllResult) {
    ImmutableMap.Builder<Path, String> result = ImmutableMap.builder();
    for (Entry<Path, String> entry : dirAllResult.entrySet()) {
      final var parentPath = relativeParentOf(entry.getKey());
      final var existingParentPkg = dirAllResult.get(parentPath);
      if (existingParentPkg == null
          || !appendPackage(existingParentPkg, entry.getKey().getFileName().toString())
              .equals(entry.getValue())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result.buildOrThrow();
  }

  /**
   * Given expanded directory to package mappings and the originally expected directory to package
   * map builds an unambiguous map from directories to packages.
   *
   * <p>If the expanded map contains conflicting entries (result of local package mapping and parent
   * expansion) they are ignored and the local package mapping is used, if present.
   *
   * <p>For example, in the following structure:
   *
   * <pre>
   *   src/ => ""
   *   src/com/ => com
   *   src/com/google/ => com.google; smth
   *   src/com/google/lib => com.google.lib
   *   src/com/google/else => smth.else
   * </pre>
   *
   * <p>`src/com/google/ => com.google; smth` is resolved as `com.google` if it is also a local
   * mapping, which would later result in a new source folder created for `src/com/google/else =>
   * smth.else`.
   */
  private static ImmutableMap<Path, String> chooseFinalMappings(
      Map<Path, String> expectedDirectoryToPackageMap, Map<Path, Set<String>> dirWants) {
    ImmutableMap.Builder<Path, String> dirAllResult = ImmutableMap.builder();
    for (final Path directory : new TreeSet<>(dirWants.keySet())) {
      final var wants = dirWants.get(directory);
      String pkg;
      if (wants != null && wants.size() == 1) {
        pkg = wants.iterator().next();
      } else {
        pkg = expectedDirectoryToPackageMap.get(directory);
      }
      if (pkg != null) {
        dirAllResult.put(directory, pkg);
      }
    }
    return dirAllResult.buildOrThrow();
  }

  /**
   * Given a set of directory to package mappings expand them to all mappings that can be derived
   * from parent directories.
   *
   * <p>i.e. in the presence of `src/com/google/smth => com.google.smth` add mappings like `src =>
   * ""`, `src/com => com`, `src/com/google => com.google`, but stop if there is a mismatch between
   * directory names and package names, i.e. when `java/src/smth => com.google.smth` is present
   * expand it only to `java/src => com.google` as it would still correctly map sub-directories and
   * when multiple similar sub-directories are present this is a preferred configuration.
   */
  private static Map<Path, Set<String>> addPossibleParentMatches(Map<Path, String> sourceRoots) {
    final Set<Path> directories = new TreeSet<>(sourceRoots.keySet());
    final Map<Path, Set<String>> dirWants = new LinkedHashMap<>();
    for (final Path directory : directories) {
      final String prefix = sourceRoots.get(directory);
      var dir = directory;
      var pref = prefix;
      while (dir != null
          && pref != null
          && dir.getFileName().toString().equals(lastSubpackageOf(pref))) {
        Set<String> wants = dirWants.computeIfAbsent(dir, it -> new HashSet<>());
        wants.add(pref);
        dir = relativeParentOf(dir);
        pref = parentPackageOf(pref);
      }
      if (dir != null && pref != null) {
        dirWants.computeIfAbsent(dir, it -> new HashSet<>()).add(pref);
      }
    }
    return dirWants;
  }

  private static String appendPackage(String parentPackage, String subpackage) {
    return parentPackage.isEmpty() ? subpackage : parentPackage + "." + subpackage;
  }

  public ProjectProto.Project createProject(BuildGraphData graph) throws BuildException {
    ImmutableMap<Path, ImmutableMap<Path, String>> javaSourceRoots =
        calculateJavaRootSources(graph.getJavaSourceFiles(), graph.packages());
    ImmutableMultimap<Path, Path> rootToNonJavaSource =
        nonJavaSourceFolders(
            graph.getSourceFilesByRuleKindAndType(not(RuleKinds::isJava), SourceType.all()));
    ImmutableSet<Path> androidResDirs;
    if (useNewResDirLogic.get()) {
      // Note: according to:
      //  https://developer.android.com/guide/topics/resources/providing-resources
      // "Never save resource files directly inside the res/ directory. It causes a compiler error."
      // This implies that we can safely take the grandparent of each resource file to find the
      // top level res dir:
      List<Path> resList = graph.getAndroidResourceFiles();
      androidResDirs =
          resList.stream()
              .map(Path::getParent)
              .distinct()
              .map(Path::getParent)
              .distinct()
              .collect(toImmutableSet());
    } else {
      // TODO(mathewi) Remove this and the corresponding experiment once the logic has been proven.
      androidResDirs = computeAndroidResourceDirectories(graph.sourceFileLabels());
    }
    ImmutableSet<String> androidResPackages;
    if (guessAndroidResPackages.get()) {
      androidResPackages =
          computeAndroidSourcePackages(graph.getAndroidSourceFiles(), javaSourceRoots);
    } else {
      androidResPackages = ImmutableSet.of();
    }

    context.output(PrintOutput.log("%-10d Android resource directories", androidResDirs.size()));
    if (guessAndroidResPackages.get()) {
      context.output(PrintOutput.log("%-10d Android resource packages", androidResPackages.size()));
    }

    ProjectProto.Module.Builder workspaceModule =
        ProjectProto.Module.newBuilder()
            .setName(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
            .setType(ProjectProto.ModuleType.MODULE_TYPE_DEFAULT)
            .addAllAndroidResourceDirectories(
                androidResDirs.stream().map(Path::toString).collect(toImmutableList()))
            .addAllAndroidSourcePackages(androidResPackages)
            .addAllAndroidCustomPackages(graph.getAllCustomPackages());

    ListMultimap<Path, Path> excludesByRootDirectory =
        projectDefinition.getExcludesByRootDirectory();
    TestSourceGlobMatcher testSourceGlobMatcher = TestSourceGlobMatcher.create(projectDefinition);
    for (Path dir : projectDefinition.projectIncludes()) {
      ProjectProto.ContentEntry.Builder contentEntry =
          ProjectProto.ContentEntry.newBuilder()
              .setRoot(
                  ProjectProto.ProjectPath.newBuilder()
                      .setPath(dir.toString())
                      .setBase(Base.WORKSPACE));
      Map<Path, String> sourceRootsWithPrefixes = javaSourceRoots.get(dir);
      for (Entry<Path, String> entry : sourceRootsWithPrefixes.entrySet()) {
        Path path = dir.resolve(entry.getKey());
        contentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setProjectPath(
                    ProjectProto.ProjectPath.newBuilder()
                        .setBase(Base.WORKSPACE)
                        .setPath(path.toString()))
                .setPackagePrefix(entry.getValue())
                .setIsTest(testSourceGlobMatcher.matches(path))
                .build());
      }
      for (Path nonJavaDirPath : rootToNonJavaSource.get(dir)) {
        if (javaSourceRoots.get(dir).keySet().stream()
            .noneMatch(p -> p.toString().isEmpty() || nonJavaDirPath.startsWith(p))) {
          Path path = dir.resolve(nonJavaDirPath);
          // TODO(b/305743519): make java source properties like package prefix specific to java
          // source folders only.
          contentEntry.addSources(
              ProjectProto.SourceFolder.newBuilder()
                  .setProjectPath(
                      ProjectProto.ProjectPath.newBuilder()
                          .setBase(Base.WORKSPACE)
                          .setPath(path.toString()))
                  .setPackagePrefix("")
                  .setIsTest(testSourceGlobMatcher.matches(path))
                  .build());
        }
      }
      for (Path exclude : excludesByRootDirectory.get(dir)) {
        contentEntry.addExcludes(exclude.toString());
      }
      workspaceModule.addContentEntries(contentEntry);
    }

    ImmutableSet.Builder<LanguageClass> activeLanguages = ImmutableSet.builder();
    if (graph.targetMap().values().stream().map(ProjectTarget::kind).anyMatch(RuleKinds::isJava)) {
      activeLanguages.add(LanguageClass.LANGUAGE_CLASS_JAVA);
    }
    if (graph.targetMap().values().stream().map(ProjectTarget::kind).anyMatch(RuleKinds::isCc)) {
      activeLanguages.add(LanguageClass.LANGUAGE_CLASS_CC);
    }

    return ProjectProto.Project.newBuilder()
        .addModules(workspaceModule)
        .addAllActiveLanguages(activeLanguages.build())
        .build();
  }

  /**
   * Heuristic for determining Android resource directories, by searching for .xml source files with
   * /res/ somewhere in the path under a build package. To be replaced by a more robust implementation.
   */
  @VisibleForTesting
  public static ImmutableSet<Path> computeAndroidResourceDirectories(
      ImmutableSet<Label> sourceFiles) {
    Set<Path> directories = new HashSet<>();
    for (var sourceFile : sourceFiles) {
      if (sourceFile.getName().toString().endsWith(".xml")) {
        List<Path> pathParts = Lists.newArrayList(sourceFile.getName());
        int resPos = pathParts.indexOf(Path.of("res"));
        if (resPos >= 0) {
          directories.add(sourceFile.getPackage().resolve(sourceFile.getName().subpath(0, resPos + 1)));
        }
      }
    }
    return ImmutableSet.copyOf(directories);
  }

  /**
   * Heuristic for computing android source java packages (used in generating R classes). Examines
   * packages of source files owned by Android targets (at most one file per target). Inefficient
   * for large projects with many android targets. To be replaced by a more robust implementation.
   */
  @VisibleForTesting
  public ImmutableSet<String> computeAndroidSourcePackages(
      List<Path> androidSourceFiles, ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix) {
    ImmutableSet.Builder<String> androidSourcePackages = ImmutableSet.builder();

    // Map entries are sorted by path length to ensure that, if the map contains keys k1 and k2,
    // where k1 is a prefix of k2, then k2 is checked before k1. We check by string length to ensure
    // the empty path is checked last.
    ImmutableMap<Path, ImmutableList<Entry<Path, String>>> sortedRootToPrefix =
        rootToPrefix.entrySet().stream()
            .map(
                entry -> {
                  Map<Path, String> sourceDirs = entry.getValue();
                  ImmutableList<Entry<Path, String>> sortedEntries =
                      ImmutableList.sortedCopyOf(
                          Collections.reverseOrder(
                              comparingInt(e -> e.getKey().toString().length())),
                          sourceDirs.entrySet());
                  return new SimpleEntry<>(entry.getKey(), sortedEntries);
                })
            .collect(toImmutableMap(Entry::getKey, Entry::getValue));

    for (Path androidSourceFile : androidSourceFiles) {
      boolean found = false;
      for (Entry<Path, ImmutableList<Entry<Path, String>>> root : sortedRootToPrefix.entrySet()) {
        if (androidSourceFile.startsWith(root.getKey())) {
          String inRoot =
              androidSourceFile.toString().substring(root.getKey().toString().length() + 1);
          ImmutableList<Entry<Path, String>> sourceDirs = root.getValue();
          for (Entry<Path, String> prefixes : sourceDirs) {
            if (inRoot.startsWith(prefixes.getKey().toString())) {
              found = true;
              String inSource = inRoot.substring(prefixes.getKey().toString().length());
              int ix = inSource.lastIndexOf('/');
              String suffix = ix != -1 ? inSource.substring(0, ix) : "";
              if (suffix.startsWith("/")) {
                suffix = suffix.substring(1);
              }
              String pkg = prefixes.getValue();
              if (!suffix.isEmpty()) {
                if (pkg.length() > 0) {
                  pkg += ".";
                }
                pkg += suffix.replace('/', '.');
              }
              androidSourcePackages.add(pkg);
              break;
            }
          }
          if (found) {
            break;
          }
        }
      }
      if (!found) {
        context.output(
            PrintOutput.log(
                String.format("Android source %s not found in any root", androidSourceFile)));
      }
    }
    return androidSourcePackages.build();
  }
}
