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
import com.google.idea.blaze.qsync.java.choosePackageCandidates
import com.google.idea.blaze.qsync.project.BuildPackage
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.SourceSet
import com.google.idea.blaze.qsync.project.computePackageStamp
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking

/** Default implementation of [ProjectStructureReader] that traverses the filesystem to identify project packages and source files. */
internal class ProjectStructureReaderImpl(private val fileExtensions: FileExtensions, private val packageReader: PackageReader) :
  ProjectStructureReader {

  override fun read(context: Context<*>, workspaceRoot: Path, projectDefinition: ProjectDefinition): ProjectStructureData =
    scanDirectories(context, workspaceRoot, projectDefinition)

  private fun scanDirectories(
    context: Context<*>,
    workspaceRoot: Path,
    projectDefinition: ProjectDefinition,
    locator: BuildPackageLocator = BuildPackageLocator(workspaceRoot),
  ): ProjectStructureData {
    /**
     * Map storing discovered source files grouped by:
     * 1. Project structure root path (relative to workspace) (ConcurrentHashMap)
     * 2. Build package path (relative to workspace) (ConcurrentHashMap)
     * 3. Java package (String) (ConcurrentHashMap)
     * 4. Java package content (JavaPackageContent)
     */
    val sourcesMap: ConcurrentHashMap<Path, ConcurrentHashMap<Path, ConcurrentHashMap<String, JavaPackageContent>>> = ConcurrentHashMap()
    val packageTimestamps: ConcurrentHashMap<Path, Long> = ConcurrentHashMap()
    val directSubpackagesMap: ConcurrentHashMap<Path, MutableSet<Path>> = ConcurrentHashMap()

    val languages: MutableSet<QuerySyncLanguage> = ConcurrentHashMap.newKeySet()
    val warnedPackages: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    val fileProcessor = FileProcessor(workspaceRoot, fileExtensions)

    fun aggregateResult(includeRoot: Path, result: FileProcessResult, forcedPackage: String? = null) {
      when (result) {
        is FileProcessResult.SourceFile -> {
          val buildPackage = locator.findBuildPackage(result.relativePath.parent ?: Path.of(""))
          if (buildPackage != null) {
            if (buildPackage.startsWith(includeRoot)) {
              val javaPackage = if (result.language == QuerySyncLanguage.JVM) forcedPackage ?: "" else ""
              val rootMap = sourcesMap.computeIfAbsent(includeRoot) { ConcurrentHashMap() }
              val buildPkgMap = rootMap.computeIfAbsent(buildPackage) { ConcurrentHashMap() }
              val packageContent = buildPkgMap.computeIfAbsent(javaPackage) { JavaPackageContent() }
              val added =
                if (result.language == QuerySyncLanguage.JVM) {
                  packageContent.javaSources.add(result.relativePath)
                } else {
                  packageContent.nonJavaSources.add(result.relativePath)
                }
              if (!added) {
                error("Duplicate file found: ${result.relativePath}")
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
          packageTimestamps.merge(result.packagePath, result.buildFileLastModifiedTimeMs, ::maxOf)
          if (result.packagePath.startsWith(includeRoot)) {
            val rootMap = sourcesMap.computeIfAbsent(includeRoot) { ConcurrentHashMap() }
            rootMap.computeIfAbsent(result.packagePath) { ConcurrentHashMap() }
          }
          if (result.packagePath.toString().isNotEmpty()) {
            val parentPath = result.packagePath.parent ?: Path.of("")
            val enclosingParentPackage = locator.findBuildPackage(parentPath)
            if (enclosingParentPackage != null && enclosingParentPackage != result.packagePath) {
              directSubpackagesMap.computeIfAbsent(enclosingParentPackage) { ConcurrentHashMap.newKeySet() }.add(result.packagePath)
            }
          }
        }
        is FileProcessResult.Ignored -> {}
      }
    }

    val duration = measureTime {
      runBlocking {
        traverseProjectDirectories(context, workspaceRoot, projectDefinition) { rootDir, currentDir, contents ->
          val includeRoot = workspaceRoot.relativize(rootDir)
          val candidateFiles =
            choosePackageCandidates(contents.files.map { it.path }, fileExtensions) {
              Files.exists(workspaceRoot.resolve(currentDir).resolve(it))
            }
          val javaPackage =
            candidateFiles.firstNotNullOfOrNull { packageReader.readPackage(context, workspaceRoot.resolve(currentDir).resolve(it)) } ?: ""

          for (file in contents.files) {
            val result = fileProcessor.processRegularFile(file, currentDir)
            aggregateResult(includeRoot, result, javaPackage)
          }
          contents
        }
      }
    }

    val roots = sourcesMap.map { (includeRoot, packageMap) ->
      val buildPackages = packageMap.mapValues { (buildPackage, packageMap) ->
        val sourceSets = packageMap.map { (javaPackage, packageContent) ->
          SourceSet(
            rootPath = buildPackage,
            javaSourceFiles = packageContent.javaSources.sorted().map { buildPackage.relativize(it) },
            nonJavaSourceFiles = packageContent.nonJavaSources.sorted().map { buildPackage.relativize(it) },
            javaPackage = javaPackage,
          )
        }
        val timestamp = packageTimestamps[buildPackage] ?: 0L
        val directSubpackages = directSubpackagesMap[buildPackage]?.toList() ?: emptyList()
        val stamp = computePackageStamp(buildFileTimestamp = timestamp, sourceSets = sourceSets, directSubpackages = directSubpackages)
        BuildPackage(path = buildPackage, sourceSets = sourceSets, stamp = stamp)
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

private class JavaPackageContent {
  val javaSources: MutableSet<Path> = ConcurrentHashMap.newKeySet()
  val nonJavaSources: MutableSet<Path> = ConcurrentHashMap.newKeySet()
}

private class BuildPackageLocator(private val workspaceRoot: Path) {
  private val cache = ConcurrentHashMap<Path, Optional<Path>>()

  private fun isBuildPackageDirectory(dir: Path): Boolean = Files.exists(dir.resolve("BUILD")) || Files.exists(dir.resolve("BUILD.bazel"))

  fun findBuildPackage(startPath: Path): Path? {
    val cachedStart = cache[startPath]
    if (cachedStart != null) {
      return cachedStart.orElse(null)
    }

    val visited = mutableListOf<Path>()
    var current = startPath
    var result: Path? = null
    do {
      val cachedCurrent = cache[current]
      if (cachedCurrent != null) {
        result = cachedCurrent.orElse(null)
        break
      }
      if (isBuildPackageDirectory(workspaceRoot.resolve(current))) {
        result = current
        break
      }
      visited.add(current)
      val reachedRoot = current == Path.of("")
      current = current.parent ?: Path.of("")
    } while (!reachedRoot)

    val cachedResult = Optional.ofNullable(result)
    visited.forEach { cache[it] = cachedResult }
    if (result != null) {
      cache[result] = cachedResult
    }
    return result
  }
}
