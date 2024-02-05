/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.ml.xmltocompose

import com.intellij.openapi.Disposable

/**
 * Question to precede every layout when querying Studio Bot. The exact same question is used to
 * train the model.
 */
internal const val PROMPT_PREFIX =
  "What's the Jetpack Compose equivalent of the following Android XML layout?"

/**
 * Service that takes the content of an XML layout file as a string and returns a corresponding
 * Compose file, also as a string.
 *
 * The conversion is intended to be backed LLMs triggered using the Studio Bot API. [PROMPT_PREFIX]
 * is the query to be included when passing the layout to Studio Bot.
 */
interface XmlToComposeConverter : Disposable {
  suspend fun convertToCompose(xml: String): String

  override fun dispose() {}
}
