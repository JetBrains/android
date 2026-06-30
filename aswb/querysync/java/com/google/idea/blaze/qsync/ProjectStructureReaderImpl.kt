/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.java.choosePackageCandidate
import com.google.idea.blaze.qsync.project.BuildPackage
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.SourceSet
import com.google.idea.blaze.traverser.DirectoryProcessor
import com.google.idea.blaze.traverser.traverseIncludedDirectories
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking

/** Default implementation of [ProjectStructureReader] that traverses the filesystem to identify project packages and source files. */
internal class ProjectStructureReaderImpl(private val fileExtensions: FileExtensions, private val packageReader: PackageReader) :
  ProjectStructureReader {

  override fun read(context: Context<*>, workspaceRoot: Path, projectDefinition: ProjectDefinition): ProjectStructureData {
    val includeAbsolute =
      projectDefinition.projectIncludes.map { workspaceRoot.resolve(it) }.filter { Files.exists(it) && Files.isDirectory(it) }
    val excludeAbsolute = projectDefinition.projectExcludes.map { workspaceRoot.resolve(it) }.toSet()

    if (includeAbsolute.isEmpty()) {
      return ProjectStructureData.EMPTY
    }

    /**
     * Map storing discovered source files grouped by:
     * 1. Project structure root path (relative to workspace) (ConcurrentHashMap)
     * 2. Build package path (relative to workspace) (ConcurrentHashMap)
     * 3. Java package (String) (ConcurrentHashMap)
     * 4. Language (JVM, CC, etc.) (guarded by: leaf map instance monitor) -> List of source file paths (relative to workspace) (guarded by:
     *    leaf map instance monitor)
     */
    val sourcesMap:
      ConcurrentHashMap<Path, ConcurrentHashMap<Path, ConcurrentHashMap<String, HashMap<QuerySyncLanguage?, MutableList<Path>>>>> =
      ConcurrentHashMap()
    val languages: MutableSet<QuerySyncLanguage> = ConcurrentHashMap.newKeySet()
    val warnedPackages: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    val fileProcessor = FileProcessor(workspaceRoot, fileExtensions)
    val directoryProcessorImpl = DirectoryProcessorImpl(context, excludeAbsolute)

    val buildPackageCache = ConcurrentHashMap<Path, Optional<Path>>()

    fun findBuildPackage(filePath: Path): Path? {
      val parent = filePath.parent ?: Path.of("")
      val cached = buildPackageCache[parent]
      if (cached != null) {
        return cached.orElse(null)
      }

      val visited = mutableListOf<Path>()
      var current = parent
      var result: Path? = null
      do {
        val cachedParent = buildPackageCache[current]
        if (cachedParent != null) {
          result = cachedParent.orElse(null)
          break
        }
        val resolved = workspaceRoot.resolve(current)
        if (Files.exists(resolved.resolve("BUILD")) || Files.exists(resolved.resolve("BUILD.bazel"))) {
          result = current
          break
        }
        visited.add(current)
        val reachedRoot = current == Path.of("")
        current = current.parent ?: Path.of("")
      } while (!reachedRoot)

      val buildPackage = Optional.ofNullable(result)
      for (dir in visited) {
        buildPackageCache[dir] = buildPackage
      }
      if (result != null) {
        buildPackageCache[result] = buildPackage
      }
      return result
    }

    fun aggregateResult(includeRoot: Path, result: FileProcessResult, forcedPackage: String? = null) {
      when (result) {
        is FileProcessResult.SourceFile -> {
          val buildPackage = findBuildPackage(result.relativePath)
          if (buildPackage != null) {
            if (buildPackage.startsWith(includeRoot)) {
              val javaPackage = if (result.language == QuerySyncLanguage.JVM) forcedPackage ?: "" else ""
              val rootMap = sourcesMap.computeIfAbsent(includeRoot) { ConcurrentHashMap() }
              val buildPkgMap = rootMap.computeIfAbsent(buildPackage) { ConcurrentHashMap() }
              val packageSources = buildPkgMap.computeIfAbsent(javaPackage) { HashMap() }
              val lang = result.language
              synchronized(packageSources) {
                val langSources = packageSources.computeIfAbsent(lang) { mutableListOf() }
                if (langSources.contains(result.relativePath)) {
                  error("Duplicate file found: ${result.relativePath}")
                }
                langSources.add(result.relativePath)
              }
            } else {
              val fitsAnyRoot = projectDefinition.projectIncludes.any { buildPackage.startsWith(it) }
              if (!fitsAnyRoot && warnedPackages.add(buildPackage)) {
                context.output(PrintOutput.log("WARNING: Package $buildPackage is outside all project structure roots"))
              }
            }
          }
          result.language?.let { languages.add(it) }
        }
        is FileProcessResult.Package -> {
          // Do nothing to match query mode behavior (don't create empty source sets for packages)
        }
        is FileProcessResult.Ignored -> {}
      }
    }

    val directoryProcessor = DirectoryProcessor { rootDir, currentDir ->
      val contents = directoryProcessorImpl.processDirectory(rootDir, currentDir)
      if (contents != null) {
        val includeRoot = workspaceRoot.relativize(rootDir)
        val candidateFile =
          choosePackageCandidate(contents.files, fileExtensions) { Files.exists(workspaceRoot.resolve(currentDir).resolve(it)) }
        val javaPackage = candidateFile?.let { packageReader.readPackage(context, workspaceRoot.resolve(currentDir).resolve(it)) } ?: ""

        for (file in contents.files) {
          val result = fileProcessor.processRegularFile(file, currentDir)
          aggregateResult(includeRoot, result, javaPackage)
        }
      }
      contents
    }

    val duration = measureTime { runBlocking { traverseIncludedDirectories(includeAbsolute, directoryProcessor) } }

    val roots =
      sourcesMap.map { (includeRoot, packageMap) ->
        val buildPackages =
          packageMap.mapValues { (buildPackage, packageMap) ->
            BuildPackage(
              path = buildPackage,
              sourceSets =
                packageMap.map { (javaPackage, langMap) ->
                  val javaSources = langMap[QuerySyncLanguage.JVM]?.sorted() ?: emptyList()
                  val nonJavaSources = langMap.filterKeys { it != QuerySyncLanguage.JVM }.values.flatten().sorted()
                  SourceSet(
                    rootPath = buildPackage,
                    javaSourceFiles = javaSources.map { buildPackage.relativize(it) },
                    nonJavaSourceFiles = nonJavaSources.map { buildPackage.relativize(it) },
                    javaPackage = javaPackage,
                  )
                },
            )
          }
        ProjectStructureRoot(projectStructureRootPath = includeRoot, buildPackages = buildPackages)
      }

    val result = ProjectStructureData.create(roots = roots, activeLanguages = languages)

    val numJavaFiles = roots.sumOf { it.buildPackages.values.flatMap { it.sourceSets }.sumOf { it.javaSourceFiles.size } }
    val numNonJavaFiles = roots.sumOf { it.buildPackages.values.flatMap { it.sourceSets }.sumOf { it.nonJavaSourceFiles.size } }
    val numPackages = roots.sumOf { it.buildPackages.size }

    context.output(
      PrintOutput.log(
        "Finished reading project structure in ${duration.inWholeMilliseconds} ms, " +
          "found $numPackages packages, " +
          "$numJavaFiles Java/Kotlin source files, " +
          "$numNonJavaFiles other source files. " +
          "Detected languages: ${result.activeLanguages}"
      )
    )
    return result
  }
}
