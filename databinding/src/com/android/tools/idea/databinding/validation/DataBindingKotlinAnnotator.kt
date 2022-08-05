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
package com.android.tools.idea.databinding.validation

import com.android.tools.idea.databinding.DATA_BINDING_ANNOTATIONS
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * An annotator that searches for Kotlin-specific issues in data binding source files.
 */
class DataBindingKotlinAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val facet = AndroidFacet.getInstance(element) ?: return
    if (!DataBindingUtil.isDataBindingEnabled(facet)) return

    if (element is KtAnnotationEntry && DATA_BINDING_ANNOTATIONS.any { annotation -> element.text.startsWith("@$annotation") }) {
      highlightIfGradleKotlinKaptPluginNotApplied(element, holder)
    }
  }

  private fun highlightIfGradleKotlinKaptPluginNotApplied(element: PsiElement,
                                                          holder: AnnotationHolder) {
    val module = element.module ?: return

    if (!module.getModuleSystem().isKaptEnabled) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "To use data binding annotations in Kotlin, apply the 'kotlin-kapt' plugin in your module's build.gradle").create()
    }
  }
}