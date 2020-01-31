/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.AnalyticsSettings
import com.android.utils.NullLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx

class UserSentimentProjectComponent(val project: Project) : AbstractProjectComponent(project) {
  override fun projectOpened() {
    super.projectOpened()

    if (!AnalyticsSettings.optedIn) {
      return
    }

    val statusBar = WindowManager.getInstance().getStatusBar(project) as StatusBarEx
    val positive = UserSentimentPanel(project, true)
    val negative = UserSentimentPanel(project, false)

    statusBar.addWidget(positive, "after ReadOnlyAttribute")
    statusBar.addWidget(negative, "after " + positive.ID())

    Disposer.register(project, Disposable {
      statusBar.removeWidget(positive.ID())
      statusBar.removeWidget(negative.ID())
    })
  }
}