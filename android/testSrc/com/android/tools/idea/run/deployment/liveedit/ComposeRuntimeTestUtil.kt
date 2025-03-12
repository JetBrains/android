/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import com.android.testutils.TestUtils
import com.android.tools.compose.ComposePluginIrGenerationExtension
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.registerServiceInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.fir.extensions.KotlinFirCompilerPluginConfigurationForIdeProvider

/**
 * Path to the compose-runtime jar. Note that unlike all other dependencies, we
 * don't need to load that into the test's runtime classpath. Instead, we just
 * need to make sure it is in the classpath input of the compiler invocation
 * Live Edit uses. Aside from things like references to the @Composable
 * annotation, we actually don't need anything from that runtime during the compiler.
 * The main reason to include that is because the compose compiler plugin expects
 * the runtime to be path of the classpath or else it'll throw an error.
 */
private val composeRuntimePathForK1 = TestUtils.resolveWorkspacePath(
  "tools/adt/idea/compose-ide-plugin/testData/lib/compose-runtime-1.4.0-SNAPSHOT.jar").toString()
private val composeRuntimePathForK2 = TestUtils.resolveWorkspacePath(
  "tools/adt/idea/compose-ide-plugin/testData/lib/compose-runtime-desktop-1.7.0.jar").toString()
val composeRuntimePath
  get() = if (KotlinPluginModeProvider.isK2Mode()) {
    composeRuntimePathForK2
  }
  else {
    composeRuntimePathForK1
  }

@OptIn(ExperimentalCompilerApi::class)
private val composeExtensionStorage by lazy {
  val storage = CompilerPluginRegistrar.ExtensionStorage()
  val pluginRegistrar = ComposePluginRegistrar()
  val compilerConfiguration = CompilerConfiguration() // We can add extra compiler options with .apply { .. }.
  val configurationWithComposeSpecificOptions = KotlinFirCompilerPluginConfigurationForIdeProvider.getCompilerConfigurationWithCustomOptions(
    pluginRegistrar, compilerConfiguration) ?: compilerConfiguration
  with(pluginRegistrar) {
    storage.registerExtensions(configurationWithComposeSpecificOptions)
  }
  storage
}

@OptIn(ExperimentalCompilerApi::class)
private val composeCompilerPluginProviderForTest by lazy {
  object : KotlinCompilerPluginsProvider {
    override fun <T : Any> getRegisteredExtensions(module: KaSourceModule,
                                                   extensionType: ProjectExtensionDescriptor<T>): List<T> {
      val registrars = composeExtensionStorage.registeredExtensions[extensionType] ?: return emptyList()
      @Suppress("UNCHECKED_CAST")
      return registrars as List<T>
    }

    override fun isPluginOfTypeRegistered(module: KaSourceModule,
                                          pluginType: KotlinCompilerPluginsProvider.CompilerPluginType): Boolean = false
  }
}

/**
 * Register the plugin for the given project rule. If you are using the default model via [AndroidProjectRule.inMemory],
 * you should probably use [setUpComposeInProjectFixture] which will also add the Compose Runtime dependency to the
 * project.
 */
@OptIn(ExperimentalCompilerApi::class)
fun registerComposeCompilerPlugin(project: Project) {
  // Register the compose compiler plugin much like what Intellij would normally do.
  if (KotlinPluginModeProvider.isK2Mode()) {
    if (project.getService(KotlinCompilerPluginsProvider::class.java) == composeCompilerPluginProviderForTest) return
    project.registerServiceInstance(KotlinCompilerPluginsProvider::class.java,
                                    composeCompilerPluginProviderForTest, project)
    return
  }
  if (IrGenerationExtension.getInstances(project).find { it is ComposePluginIrGenerationExtension } == null) {
    IrGenerationExtension.registerExtension(project, ComposePluginIrGenerationExtension())
  }
}

/**
 * Loads the Compose runtime into the project class path. This allows for tests using the compiler (Live Edit/FastPreview)
 * to correctly invoke the compiler as they would do in prod.
 *
 * @param exceptModuleNames For a given set of module, don't include compose runtime in their dependencies.
 */
fun <T : CodeInsightTestFixture> setUpComposeInProjectFixture(
  projectRule: AndroidProjectRule.Typed<T, Nothing>, exceptModuleNames: Set<String> = emptySet()) {
  // Load the compose runtime into the main module's library dependency.
  VfsRootAccess.allowRootAccess(projectRule.testRootDisposable, composeRuntimePath)
  LocalFileSystem.getInstance().refreshAndFindFileByPath(composeRuntimePath)
  projectRule.project.modules.forEach {
    if (!exceptModuleNames.contains(it.name)) {
      PsiTestUtil.addLibrary(it, composeRuntimePath)
    }
  }
  registerComposeCompilerPlugin(projectRule.project)

  // Since we depend on the project system to tell us certain information such as rather the module has compose or not, we need to
  // ask the test module system to pretend we have a compose module.
  val testProjectSystem = TestProjectSystem(projectRule.project).apply { usesCompose = true }
  runInEdtAndWait { testProjectSystem.useInTests() }
}
