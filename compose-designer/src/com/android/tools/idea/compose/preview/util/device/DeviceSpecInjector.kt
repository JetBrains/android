/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.util.TextRange
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/**
 * Injects the [DeviceSpecLanguage] for the value of the `device` parameter within Preview annotation calls.
 */
class DeviceSpecInjector : LanguageInjector {
  override fun getLanguagesToInject(host: PsiLanguageInjectionHost, injectionPlacesRegistrar: InjectedLanguagePlaces) {
    if (!StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get() ||
        host.containingFile.fileType != KotlinFileType.INSTANCE ||
        host !is KtStringTemplateExpression ||
        !host.isPreviewAnnotation()) {
      return
    }

    val argument = host.parentOfType<KtValueArgument>() ?: return
    if (argument.getArgumentName()?.text == PARAMETER_DEVICE) {
      // Inject Language within the String range
      injectionPlacesRegistrar.addPlace(DeviceSpecLanguage, TextRange.from(1, host.textLength - 1), null, null)
    }
  }
}

private fun PsiLanguageInjectionHost.isPreviewAnnotation(): Boolean {
  val annotationEntry = parentOfType<KtAnnotationEntry>() ?: return false
  return (annotationEntry.toUElement() as? UAnnotation)?.isPreviewAnnotation() ?: false
}