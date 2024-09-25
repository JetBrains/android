// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.studiobot

import com.android.tools.idea.studiobot.prompts.FileWithSelection
import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Path

/**
 * [NoOpStudioBot] is [StudioBot] implementation that does nothing.
 *
 * It is intended to "disable" [StudioBot] in IntelliJ IDEA by providing stub implementations of all APIs.
 *
 * This service is instantiated only when it is run in IDEA.
 * Android Studio should instantiate its services in the `aiplugin-api-androidstudio.xml` configuration file.
 */
class NoOpStudioBot : StudioBot {

  override val MAX_QUERY_CHARS: Int = 0

  override fun isAvailable(): Boolean = false

  override fun isContextAllowed(project: Project): Boolean = false

  override fun aiExcludeService(project: Project): AiExcludeService = NoOpAllFilesExcludedAiExcludedService

  override fun chat(project: Project): ChatService = NoOpChatService

  override fun model(project: Project, modelType: ModelType): Model = EmptyModel

  /**
   * [NoOpAllFilesExcludedAiExcludedService] always considers all project files as excluded.
   */
  private object NoOpAllFilesExcludedAiExcludedService : AiExcludeService {
    override fun isFileExcluded(file: VirtualFile): Boolean = true

    override fun isFileExcluded(file: Path): Boolean = true

    override fun getExclusionStatus(file: VirtualFile): AiExcludeService.ExclusionStatus = AiExcludeService.ExclusionStatus.EXCLUDED

    override fun getExclusionStatus(file: Path): AiExcludeService.ExclusionStatus = AiExcludeService.ExclusionStatus.EXCLUDED

    override fun getBlockingFiles(file: VirtualFile): List<VirtualFile> = emptyList()

    override fun getBlockingFiles(file: Path): List<VirtualFile> = emptyList()
  }

  private object NoOpChatService : ChatService {
    override fun stageChatQuery(prompt: String, requestSource: StudioBot.RequestSource) {
      // Do nothing
    }

    override fun sendChatQuery(prompt: Prompt, requestSource: StudioBot.RequestSource, displayText: String?) {
      // Do nothing
    }
  }

  private object EmptyModel : Model {
    override fun config(): ModelConfig = ModelConfig(inputTokenLimit = 0, outputTokenLimit = 0)

    override suspend fun generateCode(
      userQuery: String,
      fileContext: FileWithSelection?,
      language: MimeType,
      config: GenerationConfig,
      history: Prompt?,
      legacyClientSidePrompt: Prompt?,
      isTransformUseCase: Boolean,
    ): List<Content> = emptyList()

    override fun generateContent(prompt: Prompt, config: GenerationConfig): Flow<Content> = emptyFlow()

  }
}
