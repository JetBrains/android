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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.res.isGradleFile
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.gradleBuildFile
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.iwiDisabled
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.kotlinEap
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.noComposePlugIn
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlin
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlinIsJava
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlinIsXml
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedBuildSrcChange
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.virtualFileNotExist
import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile

private const val kotlinPluginId = "org.jetbrains.kotlin"

internal fun prebuildChecks(project: Project, changedFiles: List<PsiFile>) {
  // Technically, we don't NEED IWI until we support persisting changes.
  checkIwiAvailable()

  // Filter out individual files or changes that are not supported.
  for (file in changedFiles) {
    checkSupportedFiles(file)
  }

  // Check that Jetpack Compose plugin is enabled otherwise inline linking will fail with
  // unclear BackendException
  checkJetpackCompose(project)

  // Make sure user hasn't updated to the EAP Kotlin plugin.
  checkKotlinPluginBundled()
}

internal fun checkIwiAvailable() {
  if (StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get() == StudioFlags.OptimisticInstallSupportLevel.DISABLED) {
    throw iwiDisabled()
  }
}

internal fun checkSupportedFiles(file: PsiFile) {
  val virtualFile = file.virtualFile ?: return // Extremely unlikely, but possible.

  // Filter out Gradle files first. KTS files are ktfiles so we don't want them to fall into NON_KOLTIN
  // Also, we would like to see how often build modifications stop Live Edit.
  if (isGradleFile(file)) {
    throw gradleBuildFile(file)
  }

  if (file.language == JavaLanguage.INSTANCE) {
    throw nonKotlinIsJava(file)
  }

  if (file is XmlFile) {
    throw nonKotlinIsXml(file)
  }

  // Lastly we filter out all non-kotlin file that isn't known file type first before we pick out what specific things a given
  // Kotlin file is doing for it to not be supported.
  if (file !is KtFile) {
    throw nonKotlin(file)
  }

  if (virtualFile.path.contains("buildSrc")) {
    throw unsupportedBuildSrcChange(file.virtualFile.path)
  }

  if (!virtualFile.exists()) {
    throw virtualFileNotExist(virtualFile, file)
  }

}

internal fun checkJetpackCompose(project: Project) {
  // K2 uses compose compiler plugin provided by `KotlinCompilerPluginsProvider`.
  // It returns the compose compiler plugin only when the project has
  // `-Xplugin=.. compose compiler plugin ..` option.
  if (KotlinPluginModeProvider.isK2Mode()) return

  val pluginExtensions = project.extensionArea.getExtensionPoint<IrGenerationExtension>(IrGenerationExtension.name).extensions
  var found = false
  for (extension in pluginExtensions) {
    if (extension.javaClass.name == "com.android.tools.compose.ComposePluginIrGenerationExtension") {
      found = true
      break
    }
  }
  if (!found) {
    throw noComposePlugIn()
  }
}

internal fun checkKotlinPluginBundled() {
  if (!isKotlinPluginBundled()) {
    throw kotlinEap()
  }
}

fun isKotlinPluginBundled() =
  PluginManager.getInstance().findEnabledPlugin(PluginId.getId(kotlinPluginId))?.isBundled ?: false

internal fun readActionPrebuildChecks(project: Project, file: PsiFile) {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  if (!file.isValid) {
    throw LiveEditUpdateException.fileNotValid(file)
  }

  file.module?.let { module ->
    // Module.getModuleTestSourceScope() doesn't work as intended and tracked on IJPL-482 for this reason ModuleScope(false) is used
    val isTestSource = !module.getModuleScope(false).accept(file.virtualFile)
    val isAndroidSpecificTestSource = TestArtifactSearchScopes.getInstance(module)?.isTestSource(file.virtualFile) == true
    if (isAndroidSpecificTestSource || isTestSource) {
      throw LiveEditUpdateException.unsupportedTestSrcChange(file.name)
    }

    if (module.isDisposed) {
      throw LiveEditUpdateException.moduleIsDisposed(module)
    }
  }

}