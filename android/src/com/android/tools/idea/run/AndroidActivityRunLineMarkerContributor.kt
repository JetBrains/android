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
package com.android.tools.idea.run

import com.android.SdkConstants
import com.android.tools.idea.run.configuration.getPsiClass
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Identifies classes that implement an Android Activity and adds a gutter icon to run them.
 * (The details of the run configuration are provided by AndroidConfigurationProducer.)
 */
class AndroidActivityRunLineMarkerContributor : RunLineMarkerContributor() {
  override fun getInfo(e: PsiElement): Info? {
    if (!e.isClassToken()) {
      return null
    }

    val psiClass = e.getPsiClass() ?: return null
    if (psiClass.isAndroidActivitySubclass()) {
      val activityName = psiClass.name ?: return null
      return Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions()) {
        AndroidBundle.message(
          "android.run.configuration.run",
          JavaExecutionUtil.getPresentableClassName(activityName)!!
        )
      }
    }
    return null
  }

  private fun PsiElement.isClassToken() =
    node.elementType == KtTokens.CLASS_KEYWORD || node.elementType == JavaTokenType.CLASS_KEYWORD
  private fun PsiClass.isAndroidActivitySubclass() =
    InheritanceUtil.isInheritor(this, SdkConstants.CLASS_ACTIVITY)
}
