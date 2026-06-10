/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidGradleProjectStartupService
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI dialog to show at project opening on Android Studio explaining the project parallel import behavior change since Quail 1. */
class ParallelSyncMigrationActivity : ProjectActivity {

  @Service(Service.Level.PROJECT)
  class StartupService(private val project: Project) : AndroidGradleProjectStartupService<Unit>() {

    suspend fun performMigrationCheck() {
      runInitialization {
        if (!StudioFlags.SHOW_PARALLEL_SYNC_PROPERTY_MIGRATION_WINDOW.get()) return@runInitialization

        // Check if the user has opted out for THIS SPECIFIC project
        if (!PropertiesComponent.getInstance(project).getBoolean(SHOW_MIGRATION_DIALOG_KEY, true)) return@runInitialization

        // Check if the user wants to ALWAYS migrate (IDE-wide setting)
        val alwaysMigrate = GradleExperimentalSettings.getInstance().ALWAYS_ENABLE_MIGRATION_TO_PARALLEL_SYNC

        // Run the check on IO thread
        val needsMigration = withContext(Dispatchers.IO) { needsMigration(project) }

        if (needsMigration) {
          if (alwaysMigrate) {
            withContext(Dispatchers.IO) { applyMigration(project) }
            return@runInitialization
          }

          // Use EDT to show the dialog.
          withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            ParallelSyncMigrationDialog(project).show()
          }
        }
      }
    }
  }

  companion object {
    const val SHOW_MIGRATION_DIALOG_KEY = "gradle.sync.parallel.migration.suppressed"

    fun applyMigration(project: Project) {
      val projectDir = project.basePath ?: return
      val gradlePropertiesFile = File(projectDir, "gradle.properties")

      try {
        gradlePropertiesFile.appendText("\n# Enabled parallel sync for Gradle 9.4+\norg.gradle.tooling.parallel=true\n")

        // Refresh VFS to see the change immediately
        ApplicationManager.getApplication()
          .invokeLater(
            {
              val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gradlePropertiesFile)
              if (virtualFile != null) {
                VfsUtil.markDirtyAndRefresh(true, true, false, virtualFile)
              }
            },
            ModalityState.nonModal(),
          )
      } catch (_: Exception) {}
    }

    fun needsMigration(project: Project): Boolean {
      val projectDir = project.basePath ?: return false
      val gradlePropertiesFile = File(projectDir, "gradle.properties")
      if (!gradlePropertiesFile.exists() || !gradlePropertiesFile.isFile) return false

      val properties = Properties()
      try {
        gradlePropertiesFile.inputStream().use { properties.load(it) }
      } catch (_: Exception) {
        return false
      }

      val hasLegacyFlag = properties.getProperty("org.gradle.parallel") == "true"
      val hasNewFlag = properties.getProperty("org.gradle.tooling.parallel") != null

      return hasLegacyFlag && !hasNewFlag
    }
  }

  override suspend fun execute(project: Project) {
    project.service<StartupService>().performMigrationCheck()
  }
}
