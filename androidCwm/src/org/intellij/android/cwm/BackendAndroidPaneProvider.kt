// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.android.cwm

import com.intellij.openapi.project.Project
import com.jetbrains.rdserver.projectView.BackendProjectViewAsyncPaneProvider

// "AndroidView" is the same as com.android.tools.idea.navigator.AndroidProjectViewPane.ID.
// This code cannot depend on android-plugin due to classloader constraints and duplicate kotlin classes in the platform and kotlin plugin.
class BackendAndroidPaneProvider(project: Project) : BackendProjectViewAsyncPaneProvider(project, panelId){
  companion object {
    const val panelId = "AndroidView"
  }
}