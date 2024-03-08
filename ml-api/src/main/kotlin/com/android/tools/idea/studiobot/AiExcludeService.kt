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
 * Users can place files called .aiexclude throughout their project to block ai features from accessing or being
 * active in those files. In order to send queries directly to a model, clients must consult aiexclude.
 * When building queries, clients must check each file they read using [isFileExcluded]. Then to send
 * the query, construct a SafePrompt by passing your intended query and the files used to construct it.
 * This can then be sent to [ChatService.sendChatQuery] or [LlmService.sendQuery].
 *
 * See
 * [aiexclude Documentation](https://developer.android.com/studio/preview/studio-bot/data-and-privacy#aiexclude).
 */
abstract class AiExcludeService {
  /**
   * Returns `true` if one or more `.aiexclude` files in [project] block [file], or if the file's
   * exclusion cannot currently be ruled out.
   */
  abstract fun isFileExcluded(project: Project, file: VirtualFile): Boolean

  /**
   * Returns the [List] of `.aiexclude` files in [project] that block [file]. This can only be
   * called in smart mode within a read lock (so that smart mode status cannot change during
   * execution). Invoking this method outside smart mode will throw [IllegalStateException]. To
   * determine if a file is excluded without requiring smart mode, use [isFileExcluded].
   *
   * The [List] may be empty if [file] is blocked because it is outside the project, instead
   * of because of an aiexclude rule.
   */
  @RequiresReadLock
  abstract fun getBlockingFiles(project: Project, file: VirtualFile): List<VirtualFile>

  @TestOnly
  open class StubAiExcludeService : AiExcludeService() {
    override fun isFileExcluded(project: Project, file: VirtualFile): Boolean = false

    override fun getBlockingFiles(project: Project, file: VirtualFile): List<VirtualFile> =
      emptyList()
  }
}

class AiExcludeException(problemFiles: Collection<VirtualFile>) :
  RuntimeException("One or more files are blocked by aiexclude: $problemFiles")
