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
package com.android.tools.idea.gradle.project.upgrade

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.VisibleForTesting

private val LOG = Logger.getInstance(LOG_CATEGORY)

class AssistantInvokerImpl : AssistantInvoker {
  @Slow
  override fun performDeprecatedConfigurationsUpgrade(project: Project, element: PsiElement) {
    val recommended = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: recommended
    val processor = AgpUpgradeRefactoringProcessor(project, current, recommended)
    val runProcessor = showAndGetDeprecatedConfigurationsUpgradeDialog(
      processor, element, { p -> AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(p) })
    if (runProcessor) {
      DumbService.getInstance(project).smartInvokeLater { processor.run() }
    }
  }

  @Slow
  @VisibleForTesting
  fun showAndGetDeprecatedConfigurationsUpgradeDialog(
    processor: AgpUpgradeRefactoringProcessor,
    element: PsiElement,
    dialogFactory: (AgpUpgradeRefactoringProcessor) -> AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog
  ): Boolean {
    val compileRuntimeProcessor = processor.componentRefactoringProcessors
      .firstNotNullOfOrNull { it as? CompileRuntimeConfigurationRefactoringProcessor }
    if (compileRuntimeProcessor == null) {
      LOG.error("no CompileRuntimeConfiguration processor found in AGP Upgrade Processor")
    }
    processor.setCommandName("Replace Deprecated Configurations")
    val wrappedElement = WrappedPsiElement(element, compileRuntimeProcessor!!, null, "Upgrading deprecated configurations")
    processor.targets.add(wrappedElement)
    processor.ensureParsedModels()
    val runProcessor = invokeAndWaitIfNeeded(ModalityState.NON_MODAL) {
      val dialog = dialogFactory(processor)
      dialog.showAndGet()
    }
    return runProcessor
  }

  override fun maybeRecommendPluginUpgrade(project: Project, info: AndroidPluginInfo) {
    info.pluginVersion?.let { currentAgpVersion ->
      val latestKnown = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
      executeOnPooledThread {
        val published = IdeGoogleMavenRepository.getAgpVersions()
        val recommendation = shouldRecommendPluginUpgrade(project, currentAgpVersion, latestKnown, published)
        if (recommendation.upgrade) recommendPluginUpgrade(project, currentAgpVersion, recommendation.strongly)
      }
    }
  }

  override fun expireProjectUpgradeNotifications(project: Project) {
    NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType(ProjectUpgradeNotification::class.java, project)
      .forEach { it.expire() }
  }

  override fun displayForceUpdatesDisabledMessage(project: Project) {
    val msg = "Forced upgrades are disabled, errors seen may be due to incompatibilities between " +
              "the Android Gradle Plugin and the version of Android Studio.\nTo re-enable forced updates " +
              "please go to 'Tools > Internal Actions > Edit Studio Flags' and set " +
              "'${StudioFlags.DISABLE_FORCED_UPGRADES.displayName}' to 'Off'."
    val notification = AGP_UPGRADE_NOTIFICATION_GROUP.createNotification(msg, MessageType.WARNING)
    notification.notify(project)
  }
}