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
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.compilationError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlin
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedBuildSrcChange
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedRecoverableSourceModification
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedUnrecoverableSourceModification
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.idea.util.module

private const val kotlinPluginId = "org.jetbrains.kotlin"

internal fun PrebuildChecks(project: Project, changes: List<EditEvent>) {
  // Technically, we don't NEED IWI until we support persisting changes.
  checkIwiAvailable()

  // Filter out individual files or changes that are not supported.
  for (change in changes) {
    checkSupportedFiles(change.file)
    checkUnsupportedPsiEvents(change)
  }

  // Check that Jetpack Compose plugin is enabled otherwise inline linking will fail with
  // unclear BackendException
  checkJetpackCompose(project)

  // Make sure user hasn't updated to the EAP Kotlin plugin.
  checkKotlinPluginBundled()
}

internal fun checkIwiAvailable() {
  if (StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get() == StudioFlags.OptimisticInstallSupportLevel.DISABLED) {
    throw compilationError("Cannot perform Live Edit without optimistic install support", null, null)
  }
}

internal fun checkSupportedFiles(file: PsiFile) {
  if (file.virtualFile.path.contains("buildSrc")) {
    throw unsupportedBuildSrcChange(file.virtualFile.path)
  }
}

internal fun checkJetpackCompose(project: Project) {
  val pluginExtensions = IrGenerationExtension.getInstances(project)
  var found = false
  for (extension in pluginExtensions) {
    if (extension.javaClass.name == "com.android.tools.compose.ComposePluginIrGenerationExtension") {
      found = true
      break
    }
  }
  if (!found) {
    throw compilationError("Cannot find Jetpack Compose plugin in Android Studio. Is it enabled?", null, null)
  }
}

internal fun checkKotlinPluginBundled() {
  if (!isKotlinPluginBundled()) {
    throw compilationError(
      "Live Edit does not support running with this Kotlin Plugin version and will only work with the bundled Kotlin Plugin.", null, null)
  }
}

internal fun checkUnsupportedPsiEvents(change: EditEvent) {
  if (change.unsupportedPsiEvents.contains(UnsupportedPsiEvent.CONSTRUCTORS)) {
    throw unsupportedUnrecoverableSourceModification("Constructor changes", change.file)
  }

  if (change.unsupportedPsiEvents.contains(UnsupportedPsiEvent.IMPORT_DIRECTIVES)) {
    throw unsupportedRecoverableSourceModification("Updates to changes to import statement is not supported", change.file)
  }

  if (change.unsupportedPsiEvents.contains(UnsupportedPsiEvent.FIELD_CHANGES)) {
    throw unsupportedUnrecoverableSourceModification("Field changes", change.file)
  }

  if (change.unsupportedPsiEvents.contains(UnsupportedPsiEvent.NON_KOTLIN)) {
    throw nonKotlin(change.file)
  }

  if (!change.unsupportedPsiEvents.isEmpty()) {
    throw internalError("Unrecognized UnsupportedPsiEvents: " + change.unsupportedPsiEvents.joinToString(", "))
  }
}

fun isKotlinPluginBundled() =
  PluginManager.getInstance().findEnabledPlugin(PluginId.getId(kotlinPluginId))?.isBundled ?: false

internal fun ReadActionPrebuildChecks(file: PsiFile) {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  if (file.module != null) {
    if (TestArtifactSearchScopes.getInstance(file.module!!)?.isUnitTestSource(file.virtualFile) == true) {
      throw LiveEditUpdateException.unsupportedTestSrcChange(file.name)
    }
    if (TestArtifactSearchScopes.getInstance(file.module!!)?.isAndroidTestSource(file.virtualFile) == true) {
      throw LiveEditUpdateException.unsupportedTestSrcChange(file.name)
    }
  }
}