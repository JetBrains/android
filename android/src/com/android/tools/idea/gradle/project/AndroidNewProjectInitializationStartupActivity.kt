/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import org.jetbrains.android.util.AndroidBundle.message

/**
 * A helper startup activity which is supposed to run first after the project initialization is completed.
 *
 * It is intended to be used to populate the project's directory with content while the project hasn't been yet completely loaded
 * and Android and other plugins haven't yet seen the project.
 *
 * Note: This needs to implement [StartupActivity] in order to ensure correct ordering with AndroidGradleProjectStartupActivity.
 * See b/280359788 and IJPL-90 for details.
 */
class AndroidNewProjectInitializationStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val initializationRunnable = project.getUserData(INITIALIZER_KEY)
    if (initializationRunnable != null) {
      log.info("Scheduling new project initialization.")

      // This runs on EDT and it needs to be blocking, but our new project generation requires background thread.
      // We should try to migrate this not to be an activity; tracked in http://b/287942576.
      runModalTask(
        title = message("android.compile.messages.generating.r.java.content.name"), project = project, cancellable = false
      ) { initializationRunnable() }
      project.removeUserData(INITIALIZER_KEY)
    }
  }
  companion object {
    fun setProjectInitializer(project: Project, initializer: () -> Unit) {
      assert(project.getUserData(INITIALIZER_KEY) == null)
      project.putUserData(INITIALIZER_KEY, initializer)
    }
  }
}

private val INITIALIZER_KEY = Key.create<() -> Unit>("ANDROID_INIT")
private val log = logger<AndroidNewProjectInitializationStartupActivity>()