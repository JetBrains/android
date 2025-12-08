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
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
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
internal class ProjectStructureReaderImpl(private val fileExtensions: FileExtensions) : ProjectStructureReader {

  override fun read(context: Context<*>, workspaceRoot: Path, projectDefinition: ProjectDefinition): ProjectStructureData {
    val includeAbsolute =
      projectDefinition.projectIncludes.map { workspaceRoot.resolve(it) }.filter { Files.exists(it) && Files.isDirectory(it) }
    val excludeAbsolute = projectDefinition.projectExcludes.map { workspaceRoot.resolve(it) }.toSet()

    if (includeAbsolute.isEmpty()) {
      return ProjectStructureData.EMPTY
    }

    val sourcesMap: ConcurrentHashMap<Path, HashMap<QuerySyncLanguage?, MutableList<Path>>> =
      ConcurrentHashMap()
    val languages: MutableSet<QuerySyncLanguage> = ConcurrentHashMap.newKeySet()

    val fileProcessor = FileProcessor(workspaceRoot, fileExtensions)
    val directoryProcessorImpl = DirectoryProcessorImpl(context, excludeAbsolute)

    val buildPackageCache = ConcurrentHashMap<Path, Optional<Path>>()

    fun findBuildPackage(filePath: Path): Path? {
      val parent = filePath.parent ?: return null
      return buildPackageCache
        .computeIfAbsent(parent) { dir ->
          var current: Path? = dir
          while (current != null) {
            if (Files.exists(workspaceRoot.resolve(current).resolve("BUILD"))) {
              return@computeIfAbsent Optional.of(current)
            }
            if (current == Path.of("")) break
            current = current.parent
          }
          Optional.empty()
        }
        .orElse(null)
    }

    fun aggregateResult(result: FileProcessResult) {
      when (result) {
        is FileProcessResult.SourceFile -> {
          val buildPackage = findBuildPackage(result.relativePath)
          if (buildPackage != null) {
            val packageSources = sourcesMap.computeIfAbsent(buildPackage) { HashMap() }
            val lang = result.language
            synchronized(packageSources) {
              val langSources = packageSources.computeIfAbsent(lang) { mutableListOf() }
              langSources.add(result.relativePath)
            }
          }
          result.language?.let { languages.add(it) }
        }
        is FileProcessResult.Package -> {
          // Ensure package entry exists even if no sources are found
          sourcesMap.computeIfAbsent(result.packagePath) { HashMap() }
        }
        is FileProcessResult.Ignored -> {}
      }
    }

    val directoryProcessor = DirectoryProcessor { currentDir ->
      val contents = directoryProcessorImpl.processDirectory(currentDir)
      if (contents != null) {
        for (file in contents.files) {
          val result = fileProcessor.processRegularFile(file, currentDir)
          aggregateResult(result)
        }
      }
      contents
    }

    val duration = measureTime { runBlocking { traverseIncludedDirectories(includeAbsolute, directoryProcessor) } }

    val finalSourcesMap: Map<Path, SourceSet> =
      sourcesMap.mapValues { (_, langMap) ->
        val javaSources =
          langMap[QuerySyncLanguage.JVM]?.sorted() ?: emptyList()
        val nonJavaSources =
          langMap
            .filterKeys { it != QuerySyncLanguage.JVM }
            .values
            .flatten()
            .sorted()
        SourceSet(javaSourceFiles = javaSources, nonJavaSourceFiles = nonJavaSources)
      }

    val result =
      ProjectStructureData(packageSourceSets = finalSourcesMap, activeLanguages = languages)

    val numJavaFiles = finalSourcesMap.values.sumOf { it.javaSourceFiles.size }
    val numNonJavaFiles = finalSourcesMap.values.sumOf { it.nonJavaSourceFiles.size }

    context.output(
      PrintOutput.log(
        "Finished reading project structure in ${duration.inWholeMilliseconds} ms, " +
          "found ${finalSourcesMap.size} packages, " +
          "$numJavaFiles Java/Kotlin source files, " +
          "$numNonJavaFiles other source files. " +
          "Detected languages: ${result.activeLanguages}"
      )
    )
    return result
  }
}
