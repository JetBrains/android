/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass

object WearBaseClasses {
  val WATCH_FACES = arrayOf(SdkConstants.CLASS_WATCHFACE_WSL, SdkConstants.CLASS_WATCHFACE_ANDROIDX)
  val COMPLICATIONS = arrayOf(SdkConstants.CLASS_COMPLICATION_SERVICE_ANDROIDX, SdkConstants.CLASS_COMPLICATION_SERVICE_WSL)
  val TILES = arrayOf(SdkConstants.CLASS_TILE_SERVICE)
}

internal val Executor.isDebug: Boolean
  get() = DefaultDebugExecutor.EXECUTOR_ID == this.id

@OptIn(KaAllowAnalysisOnEdt::class)
internal fun PsiElement?.isSubtypeOf(baseClassName: String): Boolean {
  return when (this) {
    is KtClass -> {
      val ktClass = this
      if (KotlinPluginModeProvider.isK2Mode()) {
        allowAnalysisOnEdt {
          analyze(ktClass) {
            val symbol = ktClass.classSymbol ?: return false
            val type = buildClassType(symbol)
            // android.app.Activity -> android/app/Activity
            val classId = ClassId.fromString(baseClassName.replace(".", "/"), isLocal = false)
            val superType = buildClassType(classId)
            type.isSubtypeOf(superType)
          }
        }
      } else {
        ktClass.toLightClass()?.let { ulc ->
          InheritanceUtil.isInheritor(ulc, baseClassName)
        } == true
      }
    }
    is PsiClass -> InheritanceUtil.isInheritor(this, baseClassName)
    else -> false
  }
}

internal fun PsiElement?.getClassQualifiedName(): String? {
  return when (val parent = this?.parent) {
    is KtClass -> parent.fqName?.asString()
    is PsiClass -> parent.qualifiedName
    else -> null
  }
}
