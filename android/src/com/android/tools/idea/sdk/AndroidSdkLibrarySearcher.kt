// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ext.LibrarySearchHelper

class AndroidSdkLibrarySearcher: LibrarySearchHelper {
  override fun isLibraryExists(project: Project): Boolean {
    return AndroidSdkPathStore.getInstance().androidSdkPath != null
  }
}