/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.google.idea.blaze.qsync

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.Uninterruptibles
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path
import java.util.Collections
import java.util.Comparator.comparingInt
import java.util.PriorityQueue
import java.util.TreeSet
import java.util.concurrent.ExecutionException
import kotlin.jvm.optionals.getOrNull

/** Converts a {@link BuildGraphDataImpl} instance into a project proto. */
class GraphToProjectConverter(
  private val packageReader: PackageReader,
  private val parallelPackageReader: PackageReader.ParallelReader,
  private val fileExistenceCheck: (Path) -> Boolean,
  private val context: Context<*>,
  private val projectDefinition: ProjectDefinition,
  private val executor: ListeningExecutorService,
) {

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
  @Throws(BuildException::class)
  fun calculateJavaRootSources(
    context: Context<*>,
    srcFiles: Collection<Path>,
    packages: PackageSet,
  ): Map<Path, Map<Path, String>> {

    // A map from package to the file chosen to represent it.
    val chosenFiles = chooseTopLevelFiles(srcFiles, packages)

    // A map from a directory to its prefix
    val prefixes = readPackages(context, chosenFiles)

    // All packages split by their content roots
    val rootToPrefix = splitByRoot(prefixes)

    // Merging packages that can share the same prefix
    return mergeCompatibleSourceRoots(rootToPrefix)
  }

  /**
   * Calculates directories containing non-java source files.
   *
   * @param nonJavaSrcFiles all the sources in the project, excluding java.
   * @return mapping of content roots (project includes) to directories (relative to the content
   *     root) containing proto files.
   */
  @VisibleForTesting
  fun nonJavaSourceFolders(nonJavaSrcFiles: Collection<Path>): Map<Path, Collection<Path>> {
    data class SourceFolder(val root: Path, val contentRoot: Path)
    return nonJavaSrcFiles
      .mapNotNull { it.parent }
      .distinct()
      .mapNotNull { SourceFolder(root = it,  contentRoot = projectDefinition.getIncludingContentRoot(it).getOrNull() ?: return@mapNotNull null) }
      .groupBy({ it.contentRoot }, { it.contentRoot.relativize(it.root) })
  }

  @VisibleForTesting
  fun splitByRoot(prefixes: Map<Path, String>): ImmutableMap<Path, ImmutableMap<Path, String>> {
    val split: ImmutableMap.Builder<Path, ImmutableMap<Path, String>> = ImmutableMap.builder()
    for (root in projectDefinition.projectIncludes()) {
      val inRoot: ImmutableMap.Builder<Path, String> = ImmutableMap.builder()
      for (pkg in prefixes.entries) {
        val rel = pkg.key
        if (root.toString().isEmpty() || rel.startsWith(root)) {
          val relToRoot = root.relativize(rel)
          inRoot.put(relToRoot, pkg.value)
        }
      }
      split.put(root, inRoot.buildKeepingLast())
    }
    return split.buildKeepingLast()
  }

  @Throws(BuildException::class)
  private fun readPackages(context: Context<*>, files: List<Path>): ImmutableMap<Path, String> {
    val now = System.currentTimeMillis()
    val allPackages = parallelPackageReader.readPackages(context, packageReader, files)
    val elapsed = System.currentTimeMillis() - now
    context.output(PrintOutput.log("%-10d Java files read (%d ms)", files.size, elapsed))

    val prefixes: ImmutableMap.Builder<Path, String> = ImmutableMap.builder()
    allPackages.forEach { (path, pkg) -> prefixes.put(path.parent, pkg) }
    return prefixes.buildOrThrow()
  }

  @VisibleForTesting
  @Throws(BuildException::class)
  protected fun chooseTopLevelFiles(files: Collection<Path>, packages: PackageSet): List<Path> {

    val filesByPath = files.groupBy { it.parent }
    // A map from directory to the candidate chosen to represent that directory
    // We filter out non-existent files, but without checking for the existence of all files as
    // that slows things down unnecessarily.
    val candidates: MutableMap<Path, Path> = Maps.newConcurrentMap()
    val futures = filesByPath.keys.map { dir ->
      executor.submit(
        {
          // We use a priority queue to find the first element without sorting, since in most
          // cases we only need the first element.
          val dirFiles: PriorityQueue<Path> = PriorityQueue(Comparator.comparing(Path::getFileName))
          dirFiles.addAll(filesByPath[dir].orEmpty())
          var candidate = dirFiles.poll()
          while (candidate != null && !fileExistenceCheck(candidate)) {
            candidate = dirFiles.poll()
          }
          if (candidate != null) {
            candidates.put(dir, candidate)
          }
        })
    }

    try {
      Uninterruptibles.getUninterruptibly(Futures.allAsList(futures))
    }
    catch (e: ExecutionException) {
      throw BuildException(e)
    }

    // Filter the files that are top level files only
    return candidates.values.filter { file -> isTopLevel(packages, candidates, file) }
  }

  companion object {
    private fun isTopLevel(packages: PackageSet, candidates: Map<Path, Path>, file: Path): Boolean {
      var dir = relativeParentOf(file)
      while (dir != null) {
        val existing = candidates.get(dir)
        if (existing != null && existing != file) {
          return false
        }
        if (packages.contains(dir)) {
          return true
        }
        dir = relativeParentOf(dir)
      }
      return false
    }

    private fun relativeParentOf(path: Path): Path? {
      Preconditions.checkState(!path.isAbsolute())
      if (path.toString().isEmpty()) {
        return null
      }
      return path.parent ?: Path.of("")
    }

    private fun lastSubpackageOf(pkg: String): String {
      return pkg.substring(pkg.lastIndexOf('.') + 1)
    }

    private fun parentPackageOf(pkg: String): String? {
      if (pkg.isEmpty()) {
        return null
      }
      val ix = pkg.lastIndexOf('.')
      return if (ix == -1) "" else pkg.substring(0, ix)
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
    @JvmStatic
    fun mergeCompatibleSourceRoots(srcRoots: ImmutableMap<Path, ImmutableMap<Path, String>>): ImmutableMap<Path, ImmutableMap<Path, String>> {
      val result: ImmutableMap.Builder<Path, ImmutableMap<Path, String>> = ImmutableMap.builder()
      for (contentRoot in srcRoots.entries) {
        result.put(contentRoot.key, mergeSourceRoots(contentRoot.value))
      }
      return result.buildOrThrow()
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
    private fun mergeSourceRoots(expectedDirectoryToPackageMap: Map<Path, String>): ImmutableMap<Path, String> {
      val dirWants = addPossibleParentMatches(expectedDirectoryToPackageMap)
      val dirAllResult = chooseFinalMappings(expectedDirectoryToPackageMap, dirWants)
      return selectEssentialMappings(dirAllResult)
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
    private fun selectEssentialMappings(dirAllResult: ImmutableMap<Path, String>): ImmutableMap<Path, String> {
      val result: ImmutableMap.Builder<Path, String> = ImmutableMap.builder()
      for (entry in dirAllResult.entries) {
        val parentPath = relativeParentOf(entry.key)
        val existingParentPkg = dirAllResult.get(parentPath)
        if (existingParentPkg == null
            || !appendPackage(existingParentPkg, entry.key.getFileName().toString())
            .equals(entry.value)) {
          result.put(entry.key, entry.value)
        }
      }
      return result.buildOrThrow()
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
    private fun chooseFinalMappings(
      expectedDirectoryToPackageMap: Map<Path, String>,
      dirWants: Map<Path, Set<String>>,
    ): ImmutableMap<Path, String> {
      val dirAllResult: ImmutableMap.Builder<Path, String> = ImmutableMap.builder()
      for (directory in TreeSet(dirWants.keys)) {
        val wants = dirWants.get(directory)
        val pkg = if (wants != null && wants.size == 1) {
          wants.iterator().next()
        }
        else {
          expectedDirectoryToPackageMap.get(directory)
        }
        if (pkg != null) {
          dirAllResult.put(directory, pkg)
        }
      }
      return dirAllResult.buildOrThrow()
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
    private fun addPossibleParentMatches(sourceRoots: Map<Path, String>): Map<Path, Set<String>> {
      val directories: Set<Path> = TreeSet(sourceRoots.keys)
      val dirWants: MutableMap<Path, MutableSet<String>> = mutableMapOf()
      for (directory in directories) {
        val prefix = sourceRoots.get(directory)
        var dir: Path? = directory
        var pref = prefix
        while (dir != null
               && pref != null
               && dir.getFileName().toString().equals(lastSubpackageOf(pref))) {
          val wants = dirWants.computeIfAbsent(dir) { hashSetOf() }
          wants.add(pref)
          dir = relativeParentOf(dir)
          pref = parentPackageOf(pref)
        }
        if (dir != null && pref != null) {
          dirWants.computeIfAbsent(dir) { it -> hashSetOf() }.add(pref)
        }
      }
      return dirWants
    }

    private fun appendPackage(parentPackage: String, subpackage: String): String {
      return if (parentPackage.isEmpty()) subpackage else "$parentPackage.$subpackage"
    }

    /**
     * Heuristic for determining Android resource directories, by searching for .xml source files with
     * /res/ somewhere in the path under a build package. To be replaced by a more robust implementation.
     */
    @VisibleForTesting
    @JvmStatic
    fun computeAndroidResourceDirectories(sourceFiles: ImmutableSet<Label>): ImmutableSet<Path> {
      val directories = hashSetOf<Path>()
      for (sourceFile in sourceFiles) {
        if (sourceFile.name.endsWith(".xml")) {
          @SuppressWarnings("PathAsIterable")
          val pathParts = sourceFile.getNamePath().toList()
          val resPos = pathParts.indexOf(Path.of("res"))
          if (resPos >= 0) {
            directories.add(sourceFile.getBuildPackagePath().resolve(sourceFile.getNamePath().subpath(0, resPos + 1)))
          }
        }
      }
      return ImmutableSet.copyOf(directories)
    }
  }

  @Throws(BuildException::class)
  fun  createProject(graph: BuildGraphData): ProjectProto.Project {
    val javaSourceRoots =calculateJavaRootSources(context, graph.getJavaSourceFiles(), graph.packages())
    val rootToNonJavaSource = nonJavaSourceFolders(graph.getSourceFilesByRuleKindAndType({ t -> !RuleKinds.isJava(t) }, *SourceType.all()))
    // Note: according to:
    //  https://developer.android.com/guide/topics/resources/providing-resources
    // "Never save resource files directly inside the res/ directory. It causes a compiler error."
    // This implies that we can safely take the grandparent of each resource file to find the
    // top level res dir:
    val resList = graph.getAndroidResourceFiles()
    val androidResDirs =
        resList
            .map(Path::getParent)
            .distinct()
            .map(Path::getParent)
            .distinct()
            .toSet()
    val androidResPackages = setOf<String>()

    context.output(PrintOutput.log("%-10d Android resource directories", androidResDirs.size))

    val workspaceModule : ProjectProto.Module.Builder =
        ProjectProto.Module.newBuilder()
          .setName(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
          .setType(ProjectProto.ModuleType.MODULE_TYPE_DEFAULT)
          .addAllAndroidResourceDirectories(androidResDirs.map { it.toString() })
          .addAllAndroidSourcePackages(androidResPackages)
          .addAllAndroidCustomPackages(graph.getAllCustomPackages())

    val excludesByRootDirectory =projectDefinition.getExcludesByRootDirectory()
    val testSourceGlobMatcher = TestSourceGlobMatcher.create(projectDefinition)
    for (dir in projectDefinition.projectIncludes()) {
      val contentEntry =
          ProjectProto.ContentEntry.newBuilder()
              .setRoot(
                  ProjectProto.ProjectPath.newBuilder()
                      .setPath(dir.toString())
                      .setBase(Base.WORKSPACE))
      val sourceRootsWithPrefixes = javaSourceRoots.get(dir).orEmpty()
      for (entry in sourceRootsWithPrefixes.entries) {
        val path = dir.resolve(entry.key)
        contentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setProjectPath(
                    ProjectProto.ProjectPath.newBuilder()
                        .setBase(Base.WORKSPACE)
                        .setPath(path.toString()))
                .setPackagePrefix(entry.value)
                .setIsTest(testSourceGlobMatcher.matches(path))
                .build())
      }
      for (nonJavaDirPath in rootToNonJavaSource.get(dir).orEmpty()) {
        if (javaSourceRoots.get(dir).orEmpty().keys
            .none {p -> p.toString().isEmpty() || nonJavaDirPath.startsWith(p)}) {
          val path = dir.resolve(nonJavaDirPath)
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
                  .build())
        }
      }
      for (exclude in excludesByRootDirectory.get(dir)) {
        contentEntry.addExcludes(exclude.toString())
      }
      workspaceModule.addContentEntries(contentEntry)
    }

    val activeLanguages = graph.getActiveLanguages()

    return ProjectProto.Project.newBuilder()
        .addModules(workspaceModule)
        .addAllActiveLanguages(activeLanguages.map{it -> it.protoValue}.toList())
        .build()
  }

  /**
   * Heuristic for computing android source java packages (used in generating R classes). Examines
   * packages of source files owned by Android targets (at most one file per target). Inefficient
   * for large projects with many android targets. To be replaced by a more robust implementation.
   */
  @VisibleForTesting
  fun computeAndroidSourcePackages(
    androidSourceFiles: List<Path>,
    rootToPrefix: ImmutableMap<Path, ImmutableMap<Path, String>>,
  ): ImmutableSet<String> {
    val androidSourcePackages: ImmutableSet.Builder<String> = ImmutableSet.builder()

    // Map entries are sorted by path length to ensure that, if the map contains keys k1 and k2,
    // where k1 is a prefix of k2, then k2 is checked before k1. We check by string length to ensure
    // the empty path is checked last.
    val sortedRootToPrefix: Map<Path, List<Map.Entry<Path, String>>>  =
        rootToPrefix
            .mapValues{
                entry ->
                  val  sourceDirs: Map<Path, String> = entry.value
                  val sortedEntries: List<Map.Entry<Path, String>>  =
                    sourceDirs.entries.sortedWith(Collections.reverseOrder(
                      comparingInt{e -> e.key.toString().length}))
                  sortedEntries
                }

    for (androidSourceFile in androidSourceFiles) {
      var found = false
      for (root in sortedRootToPrefix.entries) {
        if (androidSourceFile.startsWith(root.key)) {
          val inRoot =
              androidSourceFile.toString().substring(root.key.toString().length + 1)
          val sourceDirs = root.value
          for (prefixes in sourceDirs) {
            if (inRoot.startsWith(prefixes.key.toString())) {
              found = true
              val inSource = inRoot.substring(prefixes.key.toString().length)
              val ix = inSource.lastIndexOf('/')
              var suffix = if (ix != -1) inSource.substring(0, ix) else ""
              if (suffix.startsWith("/")) {
                suffix = suffix.substring(1)
              }
              var pkg = prefixes.value
              if (!suffix.isEmpty()) {
                if (pkg.isNotEmpty()) {
                  pkg += "."
                }
                pkg += suffix.replace('/', '.')
              }
              androidSourcePackages.add(pkg)
              break
            }
          }
          if (found) {
            break
          }
        }
      }
      if (!found) {
        context.output(
            PrintOutput.log(
                String.format("Android source %s not found in any root", androidSourceFile)))
      }
    }
    return androidSourcePackages.build()
  }
}
