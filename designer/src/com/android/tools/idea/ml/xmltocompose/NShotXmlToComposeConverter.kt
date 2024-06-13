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

import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.VisibleForTesting

/**
 * Prompts to be appended after [PROMPT_PREFIX] to improve the quality of the Studio Bot responses.
 */
private val nShotPrompts =
  listOf(
    "Include imports in your answer.",
    "Add a @Preview function.",
    "Don't use ConstraintLayout.",
    "Use material3, not material.",
  )

private const val viewModelPrompt =
  "Create a subclass of androidx.lifecycle.ViewModel to store the states."

private const val displayDependenciesPrompt =
  "After the Kotlin code, display all the dependencies that are required to be added to" +
    " build.gradle.kts for this code to compile."

private const val customViewPrompt = "Wrap any Custom Views in an AndroidView composable."

private const val errorToGenerateComposeCode = "A valid compose code could not be generated."

private const val contextSharingNeedsToBeEnabled =
  "Please follow the Gemini onboarding and " +
    "enable context sharing if you want to use this feature."

/**
 * The [NShotXmlToComposeConverter] uses the n-shot prompt technique when querying Studio Bot. The
 * prompts in [nShotPrompts] are always used, while additional prompts can be used depending on the
 * parameters used when building the converter.
 */
class NShotXmlToComposeConverter
private constructor(private val project: Project, private val nShots: List<String>) :
  XmlToComposeConverter {

  private val logger = Logger.getInstance(NShotXmlToComposeConverter::class.java)

  override suspend fun convertToCompose(xml: String): ConversionResponse {
    val prompt = getPrompt(xml)
    val studioBot = StudioBot.getInstance()
    // The user must complete the Studio Bot onboarding and enable context sharing, otherwise we
    // can't use the sendQuery API.
    if (!studioBot.isContextAllowed(project)) {
      return ConversionResponse(
        generatedCode = contextSharingNeedsToBeEnabled,
        status = ConversionResponse.Status.ERROR,
      )
    }
    try {
      val response = studioBot.model(project).generateContent(prompt).toList()
      return ConversionResponse(
        generatedCode = response.map { it.text }.parseCode(),
        status = ConversionResponse.Status.SUCCESS,
      )
    } catch (t: Throwable) {
      logger.error("Error while trying to send query", t)
      return ConversionResponse(
        generatedCode = errorToGenerateComposeCode,
        status = ConversionResponse.Status.ERROR,
      )
    }
  }

  @VisibleForTesting
  fun getPrompt(xml: String) =
    buildPrompt(project) {
      userMessage {
        text("$PROMPT_PREFIX ${nShots.joinToString(" ")}", filesUsed = emptyList())
        code(xml, XMLLanguage.INSTANCE, filesUsed = emptyList())
      }
    }

  class Builder(val project: Project) {

    /** If set to true, [viewModelPrompt] will be included in the query. */
    private var _useViewModel = false

    /** If set to true, [customViewPrompt] will be included in the query. */
    private var _useCustomView = false

    /** If set to true, [displayDependenciesPrompt] will be included in the query. */
    private var _displayDependencies = false

    /**
     * If set to something other than [ComposeConverterDataType.UNKNOWN], additional prompts will be
     * included to specify the view model should use that particular type. These prompts are created
     * in
     */
    private var _dataType: ComposeConverterDataType = ComposeConverterDataType.UNKNOWN

    private val nShots = nShotPrompts.toMutableList()

    private fun generateDataTypePrompts() {
      ComposeConverterDataType.values().forEach {
        if (it == ComposeConverterDataType.UNKNOWN) {
          return@forEach
        }
        if (it == _dataType) {
          nShots.add(
            "The ViewModel must store data using objects of type ${it.classFqn}. The Composable" +
              " methods will use states derived from the data stored in the ViewModel."
          )
        } else {
          nShots.add("Do not use ${it.classFqn} in the ViewModel.")
        }
      }
    }

    fun useViewModel(useViewModel: Boolean): Builder {
      _useViewModel = useViewModel
      return this
    }

    fun useCustomView(useCustomView: Boolean): Builder {
      _useCustomView = useCustomView
      return this
    }

    fun displayDependencies(displayDependencies: Boolean): Builder {
      _displayDependencies = displayDependencies
      return this
    }

    fun withDataType(dataType: ComposeConverterDataType): Builder {
      _dataType = dataType
      return this
    }

    fun build(): NShotXmlToComposeConverter {
      if (_useCustomView) {
        nShots.add(customViewPrompt)
      }
      if (_useViewModel) {
        nShots.add(viewModelPrompt)
        generateDataTypePrompts()
      }
      if (_displayDependencies) {
        nShots.add(displayDependenciesPrompt)
      }

      return NShotXmlToComposeConverter(project, nShots)
    }
  }
}

/**
 * Takes a list of strings returned by Studio Bot, filters out chunks of code (in Kotlin or an
 * unspecified language), and returns the content without metadata or formatting.
 *
 * See `LlmService#sendQuery` documentation for details about StudioBot's response formatting.
 */
private fun List<String>.parseCode(): String {
  val kotlinPattern = "```kotlin\n"
  val textPattern = "```\n"
  return filter { it.startsWith(kotlinPattern) || it.startsWith(textPattern) }
    .joinToString("\n") { it.substringAfter("\n").trim('`').trim() }
}

enum class ComposeConverterDataType(val classFqn: String) {
  LIVE_DATA("androidx.lifecycle.LiveData"),
  MUTABLE_STATE("androidx.compose.runtime.MutableState"),
  STATE_FLOW("kotlinx.coroutines.flow.StateFlow"),
  UNKNOWN(""),
}
