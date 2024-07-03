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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/**
 * Entry point for Studio Bot functionality from android plugin. It mainly serves as a level of
 * indirection allowing the android plugin to not directly depend on Studio Bot.
 *
 * Before using Studio Bot APIs, you should first check if it is available by invoking
 * [isAvailable].
 *
 * Studio Bot currently provides two main entry points: You can use [chat] to get access to the
 * [ChatService]. Any calls made through this service will show the data being sent to and received
 * from the backend in the Studio Bot chat window. If you do not want to have your queries displayed
 * in the chat window, then use the [model] API to get access to the raw language model. In this
 * case, you are expected to prompt the model appropriately.
 *
 * No matter which API you use, it is important that project specific details are not included in
 * the prompts. See [AiExcludeService] and [isContextAllowed] for more details.
 */
interface StudioBot {
  /**
   * The maximum number of characters a query can contain before it starts getting cut off starting
   * from the end. This is an approximate value derived from the number of tokens supported by the
   * latest AIDA model.
   */
  val MAX_QUERY_CHARS: Int

  /** Returns whether Studio Bot is available (user has logged in and onboarded). */
  fun isAvailable(): Boolean = false

  /**
   * Returns whether the user has opted into sharing context from [project] in Studio Bot queries.
   */
  fun isContextAllowed(project: Project): Boolean = false

  fun aiExcludeService(project: Project): AiExcludeService

  /**
   * Returns an instance of the chat service, which is used to interface with the Studio Bot chat
   * window. This should be called only if [isAvailable] is true.
   */
  fun chat(project: Project): ChatService

  /**
   * Returns an instance of the raw model service. This should be called only if [isAvailable] is
   * true This service is used to send and receive text responses with a model directly, without
   * going through the chat UI.
   */
  fun model(project: Project, modelType: ModelType = ModelType.CHAT): Model

  /** Used for gathering metrics, like how many queries come from each part of Android Studio. */
  enum class RequestSource {
    SYNC,
    BUILD,
    DESIGN_TOOLS,
    EDITOR,
    PLAY_VITALS,
    CRASHLYTICS,
    LOGCAT,
    OTHER,
  }

  open class StubStudioBot : StudioBot {
    override val MAX_QUERY_CHARS = Int.MAX_VALUE

    override fun isAvailable(): Boolean = false

    override fun isContextAllowed(project: Project): Boolean = false

    override fun aiExcludeService(project: Project): AiExcludeService =
      AiExcludeService.FakeAiExcludeService(project)

    override fun chat(project: Project): ChatService = ChatService.StubChatService()

    override fun model(project: Project, modelType: ModelType): Model = StubModel()
  }

  companion object {
    fun getInstance(): StudioBot =
      ApplicationManager.getApplication().getService(StudioBot::class.java) ?: StubStudioBot()
  }
}

/**
 * The type of the model to use. Currently, only text to text models are available for production
 * use.
 */
enum class ModelType {
  // Model used in Studio Bot chat. A chat model is guaranteed to be available.
  CHAT,

  // Experimental model types: these require some additional set up to use.
  // Consult with the Studio Bot team before using these.
  // Currently, these models cannot be served in production and are for experimental
  // purposes only.

  // Gemini 1.0 Pro Vision model via the Gemini API. This requires an API key to use.
  // It does not support multi-turn conversations or a system message in the prompt.
  EXPERIMENTAL_VISION,

  // Gemini 1.5 Pro Long Context model via the Gemini API. This requires an API key to use.
  EXPERIMENTAL_LONG_CONTEXT,
}
