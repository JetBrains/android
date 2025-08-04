/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.adtui.compose

import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.jewel.markdown.processing.MarkdownParserFactory
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.commonmark.parser.Parser
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import kotlin.jvm.functions.Function1

/**
 * A factory for creating [MarkdownProcessor] instances using reflection.
 *
 * This is a workaround for binary compatibility issues (`NoSuchMethodError`)
 * that can occur if your code is compiled against an older version (2025.1.1) of the
 * `jewel-markdown`  library but runs against a newer version (2025.1.4) where the
 * [MarkdownProcessor] constructor has changed.
 */
@ExperimentalJewelApi
public object MarkdownProcessorReflectiveFactory {

  /**
   * Creates a [MarkdownProcessor] instance by reflectively calling its
   * constructor. This is intended to bypass `NoSuchMethodError` when the
   * constructor signature has changed in 2025.1.4+ IDEs.
   */
  @JvmOverloads
  public fun create(
    extensions: List<MarkdownProcessorExtension> = emptyList(),
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    commonMarkParser: Parser = MarkdownParserFactory.create(optimizeEdits = markdownMode is MarkdownMode.EditorPreview, extensions),
  ): MarkdownProcessor {
    return try {
      MarkdownProcessor(extensions, markdownMode, commonMarkParser)
    } catch (e: NoSuchMethodError) {
      thisLogger().warn("Cannot use default MarkdownProcessor constructor, trying reflection.", e)
      try {
        val processorClass = MarkdownProcessor::class.java
        val constructor = processorClass.getConstructor(
          List::class.java,
          MarkdownMode::class.java,
          Parser::class.java,
          Function1::class.java
        )

        // Recreate the default lambda for languageRecognizer
        val languageRecognizer =
          { langName: String -> MimeType.Known.fromMarkdownLanguageName(langName) }

        constructor.newInstance(
          extensions,
          markdownMode,
          commonMarkParser,
          languageRecognizer
        ) as MarkdownProcessor
      } catch (e: Exception) {
        throw RuntimeException(
          "Failed to create MarkdownProcessor via reflection. This is likely due to an incompatible library version.",
          e
        )
      }
    }
  }
}