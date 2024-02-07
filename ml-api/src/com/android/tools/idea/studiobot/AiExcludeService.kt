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
import org.jetbrains.annotations.TestOnly

/**
 * Studio Bot allows users to control how much information about their project is shared during the API calls with the backend.
 * Users can choose to either send no project specific information at all [StudioBot.isContextAllowed], or specify that
 * the content of some files must never be sent to the backend.
 *
 * Users can place files called .aiexclude throughout their project to block ai features from accessing or being
 * active in those files. In order to send queries directly to a model, clients must consult aiexclude.
 * When building queries, clients must check each file they read using [isFileExcluded]. Then to send
 * the query, construct a ValidatedQueryRequest by passing your intended query and the files used to construct it.
 * This can then be sent to [ChatService.sendChatQuery] or [LlmService.sendQuery].
 *
 * See [aiexclude Documentation](https://developer.android.com/studio/preview/studio-bot/data-and-privacy#aiexclude).
 */
abstract class AiExcludeService {
  /**
   * @return true if one or more aiexclude files in [project] block [file]
   */
  abstract fun isFileExcluded(project: Project, file: VirtualFile): Boolean

  /**
   * Clients requesting to send queries to Studio Bot that may contain or be constructed using context
   * like source files and build artifacts from the user's project must create a [ValidatedQuery] by
   * providing the intended query string and the list of files accessed while building it. If all of these
   * files are allowed by aiexclude, a [ValidatedQuery] is returned, otherwise a [RuntimeException] is thrown.
   *
   * @param filesUsedAsContext The project files used to write the query, which will be checked against aiexclude.
   */
  abstract fun validateQuery(
    project: Project,
    query: String,
    filesUsedAsContext: Collection<VirtualFile>): Result<ValidatedQuery>

  /**
   * A query which may depend on some files or other context, and is certified to have
   * complied with aiexclude.
   */
  sealed interface ValidatedQuery {
    val query: String
  }

  protected class ValidatedQueryImpl(override val query: String): ValidatedQuery

  @TestOnly
  open class StubAiExcludeService: AiExcludeService() {
    override fun isFileExcluded(project: Project, file: VirtualFile): Boolean = false

    class StubValidatedQuery(override val query: String) : ValidatedQuery

    override fun validateQuery(project: Project, query: String, filesUsedAsContext: Collection<VirtualFile>): Result<StubValidatedQuery> =
      Result.success(StubValidatedQuery(query))
  }
}