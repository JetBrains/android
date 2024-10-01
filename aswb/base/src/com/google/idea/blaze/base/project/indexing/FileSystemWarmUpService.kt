/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.google.idea.blaze.base.project.indexing

import com.google.idea.common.experiments.BoolExperiment
import com.google.idea.common.experiments.IntExperiment
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.util.indexing.IndexableSetContributor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * A service that rescans all project content roots each time indexable roots are requested by the IDE.
 *
 * The [FileSystemWarmUpService] mitigates the risk of multiple directories or files that need their content to be loaded being processed
 * on a single thread with a cold remote file system. The [FileSystemWarmUpService] rescans roots recursively on multiple threads and
 * requests reads the [VirtualFile.getFileType] property.
 *
 * The service rescans the VFS on each indexable root collection, however it aborts processing any existing requests when a new requests
 * arrives.
 */
@Service(Service.Level.PROJECT)
class FileSystemWarmUpService(val project: Project, val coroutineScope: CoroutineScope) {
  private val logger = thisLogger()

  companion object {
    private val fileSystemWarmUpExperimentEnabled = BoolExperiment("file.system.warm.up.enabled", true)
    private val fileSystemWarmUpExperimentThreads = IntExperiment("file.system.warm.up.threads", 50)
  }

  // Request is not a data class to make each request processed by state flows.
  private /* not data */ class Request(val roots: Set<VirtualFile>)

  private object FileSystemWarmUpActivityKey : ActivityKey {
    override val presentableName: String = "file-system-warm-up"
  }

  private val requestFlow = MutableStateFlow(Request(roots = emptySet()))

  fun requestFileSystemWarmUp() {
    if (!fileSystemWarmUpExperimentEnabled.value) return
    coroutineScope.launch {
      logger.info("Requesting file system warm-up...")
      requestFlow.emit(
        Request(
          roots = readAction {
            ModuleManager.getInstance(project)
              .findModuleByName(com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
              ?.let { ModuleRootManagerEx.getInstanceEx(it).contentRoots }
              ?.filter { it.isInLocalFileSystem }
              .orEmpty()
              .toSet()
          }
        )
      )
    }
  }

  init {
    coroutineScope.launch {
      // Process requests with collectLatest {}, which automatically cancels any unfinished processing if a new state arrives.
      requestFlow.collectLatest { request ->
        project.trackActivity(FileSystemWarmUpActivityKey) {
          processRequest(request)
        }
      }
    }
  }

  private suspend fun processRequest(request: Request) {
    logger.info("Processing file system warm-up request...")
    withContext(Dispatchers.IO.limitedParallelism(fileSystemWarmUpExperimentThreads.value)) {
      val processedFiles: Int
      val processedInMs = measureTimeMillis {
        processedFiles = FileProcessor.processFiles(request.roots)
      }
      logger.info("File system warm-up request processed ${processedFiles} files in ${processedInMs}ms.")
    }
  }

  private class FileProcessor private constructor(private val processorCoroutineScope: CoroutineScope) {
    private val logger = thisLogger()
    private val processedFileCounter = AtomicInteger()
    private val queuedFileCounter = AtomicInteger()

    private fun launchFileProcessing(fileOrDir: VirtualFile) {
      queuedFileCounter.incrementAndGet()
      processorCoroutineScope.launch {
        processFile(fileOrDir)
      }
    }

    private suspend fun processFile(file: VirtualFile) {
      val children = readAction {
        val filesProcessed = processedFileCounter.incrementAndGet()
        if (filesProcessed % 10_000 == 0) {
          logger.info("File system warm-up progress: $filesProcessed (${queuedFileCounter.get()})")
        }
        when {
          !file.isInLocalFileSystem -> return@readAction arrayOf()
          file.exists() && file.isDirectory -> file.children
          else -> {
            file.fileType
            arrayOf()
          }
        }
      }

      children.forEach {
        launchFileProcessing(it)
      }
    }

    companion object {
      suspend fun processFiles(files: Set<VirtualFile>): Int {
        return supervisorScope {
          val fileProcessor = FileProcessor(this)
          files.forEach { fileProcessor.launchFileProcessing(it) }
          fileProcessor.queuedFileCounter
        }.get()
      }
    }
  }
}

/**
 * An [IndexableSetContributor] that triggers file system warming up every time indexable roots are collected.
 *
 * Triggering warm-up on each indexable root collection ensures that any external changes to the file system won't accidentally end up
 * being processed on a single thread. Example issues are: (1) huge number of files in one indexable set because of the single module
 * project structure configured by the query sync and (2) a directory with a large number of files whose file type can only be detected
 * from their content (e.g. files without extension etc.)
 */
class WarmUpTriggeringIndexableSetContributor() : IndexableSetContributor() {
  override fun getAdditionalRootsToIndex(): Set<VirtualFile> = emptySet()

  override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> {
    project.service<FileSystemWarmUpService>().requestFileSystemWarmUp()
    return emptySet()
  }
}
