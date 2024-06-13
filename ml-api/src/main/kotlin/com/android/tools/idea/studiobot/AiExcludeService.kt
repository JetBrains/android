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
import com.intellij.util.concurrency.annotations.RequiresReadLock
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
abstract class AiExcludeService {
  /**
   * Returns `true` if one or more `.aiexclude` files in [project] block [file], if [file] is not
   * part of [project], or if the file's exclusion cannot currently be ruled out.
   *
   * If you need to know _why_ the file is excluded, consider calling [getFileExclusionStatus]
   * instead.
   */
  fun isFileExcluded(project: Project, file: VirtualFile) =
    getFileExclusionStatus(project, file) != ExclusionStatus.ALLOWED

  /** Returns the status of [file], with respect to [project]'s `.aiexclude` configuration. */
  abstract fun getFileExclusionStatus(project: Project, file: VirtualFile): ExclusionStatus

  /**
   * Returns the [List] of `.aiexclude` files in [project] that block [file]. This can only be
   * called in smart mode within a read lock (so that smart mode status cannot change during
   * execution). Invoking this method outside smart mode will throw [IllegalStateException]. To
   * determine if a file is excluded without requiring smart mode, use [isFileExcluded].
   *
   * The [List] may be empty if [file] is blocked because it is outside the project, instead of
   * because of an aiexclude rule.
   */
  @RequiresReadLock
  abstract fun getBlockingFiles(project: Project, file: VirtualFile): List<VirtualFile>

  @TestOnly
  class FakeAiExcludeService : AiExcludeService() {
    var defaultStatus: ExclusionStatus = ExclusionStatus.ALLOWED
    var defaultBlockingFiles: List<VirtualFile> = listOf()
    val fileStatus: MutableMap<VirtualFile, ExclusionStatus> = mutableMapOf()
    val blockingFiles: MutableMap<VirtualFile, List<VirtualFile>> = mutableMapOf()

    override fun getBlockingFiles(project: Project, file: VirtualFile): List<VirtualFile> =
      blockingFiles[file] ?: defaultBlockingFiles

    override fun getFileExclusionStatus(project: Project, file: VirtualFile): ExclusionStatus =
      fileStatus[file] ?: defaultStatus
  }

  enum class ExclusionStatus {
    /** The file is allowed to be used with AI features. */
    ALLOWED,
    /** The status could not be determined (e.g. we are in dumb mode). */
    INDETERMINATE,
    /** The file does not belong to the project. */
    NOT_IN_PROJECT,
    /** The file is not allowed to be used with AI features. */
    EXCLUDED,
  }
}

class AiExcludeException(problemFiles: Collection<VirtualFile>) :
  RuntimeException("One or more files are blocked by aiexclude: $problemFiles")
