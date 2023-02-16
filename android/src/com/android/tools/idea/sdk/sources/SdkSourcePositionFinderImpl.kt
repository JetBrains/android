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

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Production implementation of [SdkSourcePositionFinder]
 */
internal class SdkSourcePositionFinderImpl(val project: Project) : SdkSourcePositionFinder {
  @GuardedBy("itself")
  private val finders = mutableMapOf<Int, SdkSourceFinderForApiLevel>()

  @UiThread
  override fun getSourcePosition(apiLevel: Int, file: PsiFile, lineNumber: Int): SourcePosition {
    val finder = synchronized(finders) {
      finders.computeIfAbsent(apiLevel) { SdkSourceFinderForApiLevel(project, apiLevel) }
    }
    return finder.getSourcePosition(file, lineNumber)
  }
}