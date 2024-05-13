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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.run.editor.AndroidTestExtraParam.Companion.parseFromString
import com.android.tools.idea.run.editor.merge
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.diagnostic.Logger

fun getOptions(
  existingOptions: String, context: ConfigurationContext,
  extensions: List<TestRunConfigurationOptions>,
  logger: Logger
  ): String {
  val extraOptions = extensions
    .asSequence()
    .flatMap {
      try {
        it.getExtraOptions(context)
      } catch (e: Exception) {
        logger.error(
          "Failed to retrieve instrumentation test parameters from " +
          "extension ${it.javaClass.canonicalName}", e)
        listOf()
      }
    }
    .map { parseFromString(it) }
    .flatten()
  return parseFromString(existingOptions)
    .merge(extraOptions)
    .asSequence()
    .map { "-e " + it.NAME + " " + it.VALUE }
    .joinToString(" ")
}