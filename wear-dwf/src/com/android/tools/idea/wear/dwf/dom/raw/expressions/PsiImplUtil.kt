/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfigurationReference
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.xml.XmlFile

fun getReference(configuration: WFFExpressionConfiguration): PsiReference? {
  val watchFaceFile = getWatchFaceFile(configuration) ?: return null
  return UserConfigurationReference(configuration, watchFaceFile, configuration.text)
}

/**
 * Retrieves the Declarative Watch Face [XmlFile] associated with the element. If the element is
 * within an injected [WFFExpressionLanguage], then the Declarative Watch Face file is the file the
 * language is injected in. Otherwise, we attempt to use the current file.
 */
fun getWatchFaceFile(element: PsiElement): XmlFile? {
  val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)
  val psiFile = injectedLanguageManager.getTopLevelFile(element) ?: element.containingFile
  return psiFile as? XmlFile
}
