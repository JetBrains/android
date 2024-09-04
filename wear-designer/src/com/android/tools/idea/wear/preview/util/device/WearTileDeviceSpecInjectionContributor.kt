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
package com.android.tools.idea.wear.preview.util.device

import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.getFullyQualifiedNameOnWriteActionForK2
import com.android.tools.idea.preview.util.device.DeviceSpecInjectionContributor
import com.android.tools.idea.wear.preview.TILE_PREVIEW_ANNOTATION_FQ_NAME
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class WearTileDeviceSpecInjectionContributor : DeviceSpecInjectionContributor() {
  /**
   * This function is called as a part of code-format that can be also a part of template execution.
   * Since it happens right after the user updates PSIs, we cannot run analysis APIs for the PSIs in
   * advance on a background thread. In this case, we cannot avoid running analysis APIs on the
   * UI-thread. In addition, the code-format runs on a write-action. This is why this function calls
   * [KtAnnotationEntry.getFullyQualifiedNameOnWriteActionForK2]. It is known that calling analysis
   * APIs on a write-action can cause the UI-freeze, but this is a case we cannot avoid it.
   */
  override fun isInPreviewAnnotation(psiElement: PsiElement): Boolean =
    when (psiElement.language) {
      is KotlinLanguage -> {
        psiElement.parentOfType<KtAnnotationEntry>()?.let { parent ->
          if (KotlinPluginModeProvider.isK2Mode()) {
            parent.getFullyQualifiedNameOnWriteActionForK2() == TILE_PREVIEW_ANNOTATION_FQ_NAME
          } else {
            psiElement
              .parentOfType<KtAnnotationEntry>()
              ?.fqNameMatches(TILE_PREVIEW_ANNOTATION_FQ_NAME)
          }
        } ?: false
      }
      is JavaLanguage -> {
        psiElement.parentOfType<PsiAnnotation>()?.hasQualifiedName(TILE_PREVIEW_ANNOTATION_FQ_NAME)
          ?: false
      }
      else -> false
    }
}
