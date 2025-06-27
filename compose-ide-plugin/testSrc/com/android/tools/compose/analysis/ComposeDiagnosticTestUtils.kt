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
package com.android.tools.compose.analysis

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder

private val composeCompilerPluginPath by lazy {
  PathManager.getJarForClass(ComposePluginRegistrar::class.java)
}

private val suppressKotlinVersionCheckOption =
  "plugin:${
    ComposeCommandLineProcessor.PLUGIN_ID
  }:${
    ComposeCommandLineProcessor.SUPPRESS_KOTLIN_VERSION_CHECK_ENABLED_OPTION.optionName
  }=true"

internal fun setUpCompilerArgumentsForComposeCompilerPlugin(project: Project) {
  if (KotlinPluginModeProvider.isK2Mode()) {
    KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
      this.pluginClasspaths = arrayOf(composeCompilerPluginPath.toString())
      this.pluginOptions = arrayOf(suppressKotlinVersionCheckOption)
    }
  }
}
