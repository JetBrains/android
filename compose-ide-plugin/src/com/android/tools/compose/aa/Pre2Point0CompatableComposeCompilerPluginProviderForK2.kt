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
package com.android.tools.compose.aa

import com.intellij.openapi.project.Project
import java.nio.file.Path
import org.jetbrains.kotlin.idea.fir.extensions.CompilerPluginRegistrarUtils
import org.jetbrains.kotlin.idea.fir.extensions.KotlinBundledFirCompilerPluginProvider
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins

/**
 * This is a class of [KotlinBundledFirCompilerPluginProvider] to handle the old compose compiler
 * plugin. The default [KotlinBundledFirCompilerPluginProvider] handles the compose compiler plugin
 * containing `androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar` (see
 * [KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN]), but it returns `null` for the old
 * compose compiler plugin that has the old registrar
 * (`androidx.compose.compiler.plugins.kotlin.ComposeComponentRegistrar`). The default
 * [KotlinBundledFirCompilerPluginProvider] checks the registrar service based on the string match
 * with the new one, but the old one does not have the new registrar. It results in a backward
 * compatibility issue i.e., IntelliJ does not substitute/load the compose compiler plugin if the
 * user specified an old one. This class substitutes the old one based on the string match with
 * `androidx.compose.compiler.plugins.kotlin.ComposeComponentRegistrar`.
 */
class Pre2Point0CompatableComposeCompilerPluginProviderForK2 :
  KotlinBundledFirCompilerPluginProvider {
  companion object {
    private const val OLD_COMPOSE_COMPILER_REGISTRAR =
      "androidx.compose.compiler.plugins.kotlin.ComposeComponentRegistrar"
  }

  override fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? {
    val compilerPluginRegistrarContent =
      CompilerPluginRegistrarUtils.readRegistrarContent(userSuppliedPluginJar) ?: return null
    if (OLD_COMPOSE_COMPILER_REGISTRAR !in compilerPluginRegistrarContent) return null
    return KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN.bundledJarLocation
  }
}
