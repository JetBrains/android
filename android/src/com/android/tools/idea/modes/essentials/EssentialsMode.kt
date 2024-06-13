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
package com.android.tools.idea.modes.essentials

import com.android.flags.Flag
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EssentialsModeEvent
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import org.jetbrains.kotlin.idea.core.util.toPsiFile

@Service
class EssentialsMode {
  init {
    essentialsModeLogger.info("Essentials mode isEnabled on start-up: ${isEnabled()}")
  }

  companion object {
    private const val REGISTRY_KEY = "ide.essentials.mode"
    private val essentialsModeLogger = logger<EssentialsMode>()

    // keeping Essential Highlighting separable from Essentials Mode if it's determined at a future
    // date that most users would prefer this feature not bundled with Essentials Mode
    private val essentialHighlightingEnabled: Flag<Boolean> = StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE

    @JvmStatic
    fun isEnabled(): Boolean {
      return RegistryManager.getInstance().`is`(REGISTRY_KEY)
    }

    @JvmStatic
    fun setEnabled(value: Boolean, project: Project?) {
      val beforeSet = isEnabled()
      RegistryManager.getInstance().get(REGISTRY_KEY).setValue(value)
      if (essentialHighlightingEnabled.get()) EssentialHighlightingMode.setEnabled(value)
      // send message if the value changed
      if (beforeSet != value) {
        service<EssentialsModeMessenger>().sendMessage()
        essentialsModeLogger.info("Essentials mode isEnabled set to $value")
        updateUI(value)

        project?.let {
          doHighlightPass(project)
          it.service<EssentialsModeNotifier>().notifyProject()
        }

        trackEvent(value)
      }
    }

    // Do a single highlight pass to update editor elements (e.g. gutter icons) that depend on Essentials Mode status.
    private fun doHighlightPass(project: Project) {
      FileEditorManager.getInstance(project).selectedEditors.forEach {
        it.file.toPsiFile(project)?.let { file ->
          DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
      }
    }

    // update the widget across projects, and if the mode was disabled remove, get rid of stale notifications
    private fun updateUI(value: Boolean) {
      for (project in ProjectManager.getInstance().openProjects) {
        project.service<StatusBarWidgetsManager>().updateWidget(EssentialsModeWidgetFactory::class.java)
        if (!value) {
          // potentially can get duplicate notifications across multiple projects, in this case remove them
          for (essentialsModeNotification in NotificationsManager.getNotificationsManager().getNotificationsOfType(
            EssentialsModeNotifier.EssentialsModeNotification::class.java, project)) {
            essentialsModeNotification.expire()
          }
        }
      }
    }

    fun trackEvent(value: Boolean) {
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.ESSENTIALS_MODE_EVENT)
                         .setEssentialsModeEvent(EssentialsModeEvent.newBuilder().setEnabled(value)))
    }
  }
}
