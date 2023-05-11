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
package com.android.tools.idea.sdk

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.psi.PsiFile

/**
 * Suppresses error highlighting in Android SDK sources
 * (since android.jar has many false-positive unresolved references).
 */
class SdkHighlightFilter : HighlightInfoFilter {

  override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
    return file == null || highlightInfo.severity != HighlightSeverity.ERROR || !IS_IN_ANDROID_SDK.getValue(file)
  }

  companion object {
    /** Memoization for AndroidSdks.isInAndroidSdk(). The result is stored in the user data of the given PsiFile. */
    private val IS_IN_ANDROID_SDK = NotNullLazyKey.createLazyKey<Boolean, PsiFile>("IS_IN_ANDROID_SDK") { file ->
      AndroidSdks.getInstance().isInAndroidSdk(file)
    }
  }
}
