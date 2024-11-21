// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.util.LocalProperties
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SdkSync {
  fun syncIdeAndProjectAndroidSdks(localProperties: LocalProperties, project: Project?)

  companion object {
    @JvmStatic
    fun getInstance(): SdkSync = service()
  }
}
