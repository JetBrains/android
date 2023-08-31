/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import java.nio.file.Files

fun getComposePluginTestDataPath():String {
  val adtPath = resolveWorkspacePath("tools/adt/idea/compose-ide-plugin/testData")
  return if (Files.exists(adtPath)) adtPath.toString()
         else PathManagerEx.findFileUnderCommunityHome("plugins/android-compose-ide-plugin").path
}

// Kotlin compiler diagnostics to suppress
// When testing Compose compiler plugin checkers, let's test diagnostics from plugin checkers,
// not those from built-in compiler checkers.
private val SUPPRESSION = listOf("UNUSED_PARAMETER", "UNUSED_VARIABLE")

internal val suppressAnnotation: String
  get() = SUPPRESSION.joinToString(prefix = "@file:Suppress(", postfix = ")") { "\"$it\"" }

private val composeCompilerPluginPath by lazy {
  resolveWorkspacePath("tools/adt/idea/compose-ide-plugin/lib/compiler-hosted.jar")
}

private val suppressKotlinVersionCheckOption = "plugin:${
  ComposeCommandLineProcessor.PLUGIN_ID
}:${
  ComposeCommandLineProcessor.SUPPRESS_KOTLIN_VERSION_CHECK_ENABLED_OPTION.optionName
}=true"

/**
 * TODO(298705216): When we have APIs to add compiler options via [AndroidProjectRule], replace this function with the APIs.
 */
internal fun setUpCompilerArgumentsForComposeCompilerPlugin(project: Project) {
  if (isK2Plugin()) {
    KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
      this.pluginClasspaths = arrayOf(composeCompilerPluginPath.toString())
      this.pluginOptions = arrayOf(suppressKotlinVersionCheckOption)
    }
  }
}