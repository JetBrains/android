/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.studiobot

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/**
 * Studio Bot allows users to control how much information about their project is shared during the
 * API calls with the backend. Users can choose to either send no project specific information at
 * all [StudioBot.isContextAllowed], or specify that the content of some files must never be sent to
 * the backend.
 *
 * Users can place files called .aiexclude throughout their project to block ai features from
 * accessing or being active in those files. In order to send queries directly to a model, clients
 * must consult aiexclude. When building queries, clients must check each file they read using
 * [isFileExcluded]. Then to send the query, construct a Prompt by passing your intended query and
 * the files used to construct it. This can then be sent to [ChatService.sendChatQuery] or
 * [LlmService.sendQuery].
 *
 * See
 * [aiexclude Documentation](https://developer.android.com/studio/preview/gemini/data-and-privacy#aiexclude).
 */
interface AiExcludeService {
  val project: Project

  /**
   * Returns `true` if one or more `.aiexclude` files in [project] block [file], if [file] is not
   * part of [project], or if the file's exclusion cannot currently be ruled out.
   *
   * If you need to know _why_ the file is excluded, consider calling [getExclusionStatus] instead.
   */
  fun isFileExcluded(file: VirtualFile): Boolean

  /**
   * Returns `true` if one or more `.aiexclude` files in [project] block [file], if [file] is not
   * part of [project], or if the file's exclusion cannot currently be ruled out.
   *
   * If you need to know _why_ the file is excluded, consider calling [getExclusionStatus] instead.
   */
  fun isFileExcluded(file: Path): Boolean

  /** Returns the status of [file], with respect to [project]'s `.aiexclude` configuration. */
  fun getExclusionStatus(file: VirtualFile): ExclusionStatus

  /** Returns the status of [file], with respect to [project]'s `.aiexclude` configuration. */
  fun getExclusionStatus(file: Path): ExclusionStatus

  /**
   * Returns the [List] of `.aiexclude` files in [project] that block [file]. This can only be
   * called in smart mode within a read lock (so that smart mode status cannot change during
   * execution). Invoking this method outside smart mode will throw [IllegalStateException]. To
   * determine if a file is excluded without requiring smart mode, use [isFileExcluded].
   *
   * The [List] may be empty if [file] is blocked because it is outside the project and VCS roots,
   * instead of because of an aiexclude rule.
   */
  @RequiresReadLock fun getBlockingFiles(file: VirtualFile): List<VirtualFile>

  /**
   * Returns the [List] of `.aiexclude` files in [project] that block [file]. This can only be
   * called in smart mode within a read lock (so that smart mode status cannot change during
   * execution). Invoking this method outside smart mode will throw [IllegalStateException]. To
   * determine if a file is excluded without requiring smart mode, use [isFileExcluded].
   *
   * The [List] may be empty if [file] is blocked because it is outside the project and VCS roots,
   * instead of because of an aiexclude rule.
   */
  @RequiresReadLock fun getBlockingFiles(file: Path): List<VirtualFile>

  // TODO(b/350768333): move to test sources
  @TestOnly
  class FakeAiExcludeService(override val project: Project) : AiExcludeService {
    var defaultStatus: ExclusionStatus = ExclusionStatus.ALLOWED
    private val exclusionStatus: MutableMap<Any, ExclusionStatus> = mutableMapOf()
    private val blockingFiles: MutableMap<Any, List<VirtualFile>> = mutableMapOf()

    fun addExclusion(
      file: VirtualFile,
      status: ExclusionStatus = ExclusionStatus.EXCLUDED,
      blockingFiles: List<VirtualFile> = emptyList(),
    ) {
      doAddExclusion(file, status, blockingFiles)
    }

    fun addExclusion(
      file: Path,
      status: ExclusionStatus = ExclusionStatus.EXCLUDED,
      blockingFiles: List<VirtualFile> = emptyList(),
    ) {
      doAddExclusion(file, status, blockingFiles)
    }

    override fun isFileExcluded(file: VirtualFile) =
      getExclusionStatus(file) != ExclusionStatus.ALLOWED

    override fun isFileExcluded(file: Path): Boolean =
      getExclusionStatus(file) != ExclusionStatus.ALLOWED

    override fun getBlockingFiles(file: VirtualFile): List<VirtualFile> =
      blockingFiles[file] ?: file.toNioPathOrNull()?.let { getBlockingFiles(it) } ?: emptyList()

    override fun getBlockingFiles(file: Path): List<VirtualFile> =
      blockingFiles[file] ?: emptyList()

    override fun getExclusionStatus(file: VirtualFile): ExclusionStatus =
      exclusionStatus[file]
        ?: file.toNioPathOrNull()?.let { getExclusionStatus(it) }
        ?: defaultStatus

    override fun getExclusionStatus(file: Path): ExclusionStatus =
      exclusionStatus[file] ?: defaultStatus

    private fun doAddExclusion(
      file: Any,
      status: ExclusionStatus = ExclusionStatus.EXCLUDED,
      blockingFiles: List<VirtualFile> = emptyList(),
    ) {
      if (status == defaultStatus) {
        exclusionStatus.remove(file)
      } else {
        exclusionStatus[file] = status
      }
      if (blockingFiles.isEmpty()) {
        this.blockingFiles.remove(file)
      } else {
        this.blockingFiles[file] = blockingFiles
      }
    }
  }

  enum class ExclusionStatus {
    /** The file is allowed to be used with AI features. */
    ALLOWED,
    /** The status could not be determined (e.g. we are in dumb mode). */
    INDETERMINATE,
    /**
     * The file does not belong to the project and is not under any VCS roots attached to the
     * project. In this case it should be excluded by default, as .aiexclude rules do not apply to
     * it.
     */
    NOT_IN_PROJECT_OR_VCS,
    /** The file is not allowed to be used with AI features. */
    EXCLUDED,
  }
}

class AiExcludeException(problemFiles: Collection<VirtualFile>) :
  RuntimeException("One or more files are blocked by aiexclude: $problemFiles")
