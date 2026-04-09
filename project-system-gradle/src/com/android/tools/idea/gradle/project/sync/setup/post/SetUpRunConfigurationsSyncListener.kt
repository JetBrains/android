/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.idea.project.AndroidRunConfigurationsManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

class SetUpRunConfigurationsSyncListener(private val project: Project) : ProjectSystemSyncManager.AndroidModelsUpdatedListener {
  override fun androidModelsUpdated() {
    val callable = Callable {
      if (project.isDisposed) return@Callable
      AndroidRunConfigurationsManager.getInstance(project).createProjectRunConfigurations()
    }

    // create any run configurations, even if indexing hasn't finished yet.
    ReadAction.nonBlocking(callable).submit(AppExecutorUtil.getAppExecutorService())

    // once indexing is finished, potentially create more run configurations
    ReadAction.nonBlocking(callable).inSmartMode(project).coalesceBy(this).submit(AppExecutorUtil.getAppExecutorService())
  }
}
