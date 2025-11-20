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
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.query.PackageSet
import com.google.idea.blaze.traverser.DirectoryProcessor
import com.google.idea.blaze.traverser.traverseIncludedDirectories
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking

/**
 * Default implementation of [ProjectStructureReader] that traverses the filesystem to identify
 * project packages and source files.
 */
internal class ProjectStructureReaderImpl(private val fileExtensions: FileExtensions) :
  ProjectStructureReader {

  override fun read(
    context: Context<*>,
    workspaceRoot: Path,
    projectDefinition: ProjectDefinition,
  ): ProjectStructureData {
    val includeAbsolute =
      projectDefinition.projectIncludes
        .map { workspaceRoot.resolve(it) }
        .filter { Files.exists(it) && Files.isDirectory(it) }
    val excludeAbsolute =
      projectDefinition.projectExcludes.map { workspaceRoot.resolve(it) }.toSet()

    if (includeAbsolute.isEmpty()) {
      return ProjectStructureData.EMPTY
    }

    val packageDirs: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    val javaSourceFiles: MutableList<Path> = Collections.synchronizedList(mutableListOf())
    val nonJavaSourceFiles: MutableList<Path> = Collections.synchronizedList(mutableListOf())
    val languages: MutableSet<QuerySyncLanguage> = ConcurrentHashMap.newKeySet()

    val fileProcessor = FileProcessor(workspaceRoot, fileExtensions)
    val directoryProcessorImpl = DirectoryProcessorImpl(context, excludeAbsolute)

    fun aggregateResult(result: FileProcessResult) {
      when (result) {
        is FileProcessResult.Package -> packageDirs.add(result.packagePath)
        is FileProcessResult.SourceFile -> {
          if (result.language == QuerySyncLanguage.JVM) {
            javaSourceFiles.add(result.relativePath)
          } else {
            nonJavaSourceFiles.add(result.relativePath)
          }
          result.language?.let { languages.add(it) }
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

    val duration = measureTime {
      runBlocking { traverseIncludedDirectories(includeAbsolute, directoryProcessor) }
    }

    val result =
      ProjectStructureData(
        javaSourceFiles = javaSourceFiles.sorted(),
        packages = PackageSet(packageDirs),
        nonJavaSourceFiles = nonJavaSourceFiles.sorted(),
        activeLanguages = languages,
      )

    context.output(
      PrintOutput.log(
        "Finished reading project structure in ${duration.inWholeMilliseconds} ms, " +
          "found ${result.packages.size()} packages, " +
          "${result.javaSourceFiles.size} Java/Kotlin source files, " +
          "${result.nonJavaSourceFiles.size} other source files. " +
          "Detected languages: ${result.activeLanguages}"
      )
    )
    return result
  }
}
