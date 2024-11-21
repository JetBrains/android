// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.projectsystem.gradle.sync

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project

/**
 * Provides an extension point for a custom compile target failure notification handling.
 */
interface AndroidModuleDataServiceExtension {
  fun onFailedToFindCompileTarget(project: Project, compileTarget: String, moduleNames: List<String>)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<AndroidModuleDataServiceExtension> = create("com.android.gradle.androidModuleDataService")
  }
}
