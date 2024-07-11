/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.DataMap
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.idea.util.findAnnotation as findAnnotationK1

/** Returns the [PsiFile] associated with a given lint [Context]. */
fun Context.getPsiFile(): PsiFile? {
  val request = driver.request
  val project = (request as LintIdeRequest).project
  if (project.isDisposed) {
    return null
  }
  val file = VfsUtil.findFileByIoFile(file, false) ?: return null
  return file.getPsiFileSafely(project)
}

/** Checks if this [KtProperty] has a backing field or implements get/set on its own. */
@OptIn(KaAllowAnalysisOnEdt::class)
internal fun KtProperty.hasBackingField(): Boolean {
  allowAnalysisOnEdt {
    @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
    allowAnalysisFromWriteAction {
      analyze(this) {
        val propertySymbol =
          this@hasBackingField.symbol as? KaPropertySymbol ?: return false
        return propertySymbol.hasBackingField
      }
    }
  }
}

/**
 * Looks up the [PsiFile] for a given [VirtualFile] in a given [Project], in a safe way (meaning it
 * will acquire a read lock first, and will check that the file is valid
 */
fun VirtualFile.getPsiFileSafely(project: Project): PsiFile? {
  return ApplicationManager.getApplication()
    .runReadAction(
      (Computable {
        when {
          project.isDisposed -> null
          isValid -> PsiManager.getInstance(project).findFile(this)
          else -> null
        }
      })
    )
}

// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
@OptIn(KaAllowAnalysisOnEdt::class)
fun KtAnnotated.findAnnotation(fqName: FqName): KtAnnotationEntry? =
  if (KotlinPluginModeProvider.isK2Mode()) {
    allowAnalysisOnEdt {
      @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
      allowAnalysisFromWriteAction {
        analyze(this) {
          val annotatedSymbol =
            (this@findAnnotation as? KtDeclaration)?.symbol as? KaAnnotated
          val annotations = annotatedSymbol?.let { it.annotations[ClassId.topLevel(fqName)] }
          annotations?.singleOrNull()?.psi as? KtAnnotationEntry
        }
      }
    }
  } else {
    findAnnotationK1(fqName)
  }

/** Gets and removes the single [DataMap] fix from [incident], if there is one. */
fun getAndRemoveMapFix(incident: Incident): DataMap? {
  val (updatedFix, dataMap) = getAndRemoveMapFix(incident.fix)
  incident.fix = updatedFix
  return dataMap
}

/**
 * Gets the single [DataMap] fix from [fix], if there is one, and returns a new [LintFix] without
 * the [DataMap] fix, and the [DataMap] fix. Intended for when [fix] can be a [LintFixGroup] and you
 * want to remove the [DataMap] fix from the group. Other cases are also handled.
 */
fun getAndRemoveMapFix(fix: LintFix?): Pair<LintFix?, DataMap?> {
  return when (fix) {
    null -> null to null
    is LintFixGroup -> {
      val numMaps = fix.fixes.count { it is DataMap }
      if (numMaps == 1) {
        val dataMap = fix.fixes.single { it is DataMap } as DataMap
        LintFixGroup(
          fix.getDisplayName(),
          fix.getFamilyName(),
          fix.type,
          fix.fixes.filter { it !is DataMap },
          fix.robot,
          fix.independent,
        ) to dataMap
      } else {
        fix to null
      }
    }
    is DataMap -> null to fix
    else -> fix to null
  }
}
