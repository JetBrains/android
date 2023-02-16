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
package com.android.tools.idea.sdk.sources

import com.android.annotations.concurrency.UiThread
import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Finds the [SourcePosition] for a framework code reference in the proper API level */
interface SdkSourcePositionFinder {
  @UiThread
  fun getSourcePosition(apiLevel: Int, file: PsiFile, lineNumber: Int): SourcePosition

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SdkSourcePositionFinder = project.getService(SdkSourcePositionFinder::class.java)
  }
}
