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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.java.IJavaElementType
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * A [RunLineMarkerContributor] that displays a "Run" gutter icon next to classes that extend
 * Wear Services (Watch Face, Tiles and Complications).
 * The icon can be used to create new [AndroidWearConfiguration] or run an existing configuration.
 */
class AndroidWearRunMarkerContributor : RunLineMarkerContributor() {
  override fun getInfo(e: PsiElement): Info? {
    if (!StudioFlags.ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER.get()) {
      return null
    }

    val elementType = e.node.elementType
    if (!(elementType is KtToken && elementType == KtTokens.CLASS_KEYWORD) // do not force loading of KtTokens in Java files
        && !(elementType is IJavaElementType && elementType == JavaTokenType.CLASS_KEYWORD)) {
      return null
    }

    val psiClass = e.getPsiClass() ?: return null
    if (psiClass.isValidWatchFaceService() || psiClass.isValidTileService() || psiClass.isValidComplicationService()) {
      val serviceName = psiClass.name ?: return null
      return Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions()) {
        AndroidBundle.message("android.run.configuration.run", JavaExecutionUtil.getPresentableClassName(serviceName)!!)
      }
    }
    return null
  }
}
