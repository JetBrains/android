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
package com.android.tools.idea.ndk

import com.android.tools.idea.run.configuration.execution.ApplicationDeployListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Listen for build events so that we can respond with 16 KB messages if necessary.
 */
class PageAlignProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!PageAlignConfig.isPageAlignMessageEnabled()) return
    project.messageBus
      .connect(PageAlignDisposable.getProjectInstance(project))
      .subscribe(ApplicationDeployListener.TOPIC, PageAlignDeployListener(project))
  }
}

