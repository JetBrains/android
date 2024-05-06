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
package org.jetbrains.kotlin.android

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.android.AndroidResourceExternalAnnotatorBase
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Annotator which puts colors and image icons in the editor gutter when referenced in Kotlin files.
 */
abstract class AndroidKotlinResourceExternalAnnotatorBase : AndroidResourceExternalAnnotatorBase() {
    override fun collectInformation(file: PsiFile, editor: Editor): FileAnnotationInfo? {
        if (EssentialHighlightingMode.isEnabled()) return null;
        val facet = file.androidFacet ?: return null
        val annotationInfo = FileAnnotationInfo(facet, file, editor)
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                val reference = element as? KtNameReferenceExpression ?: return
                val resourceReference = reference.resolveToResourceReference() ?: return
                annotationInfo.elements.add(FileAnnotationInfo.AnnotatableElement(resourceReference, element))
            }
        })
        return annotationInfo.takeIf { annotationInfo.elements.isNotEmpty() }
    }

    abstract fun KtNameReferenceExpression.resolveToResourceReference(): ResourceReference?

    companion object {
        @JvmStatic
        protected val AndroidPsiUtils.ResourceReferenceType.namespace: ResourceNamespace
            get() =
                if (this == AndroidPsiUtils.ResourceReferenceType.FRAMEWORK) {
                    ResourceNamespace.ANDROID
                } else {
                    ResourceNamespace.RES_AUTO
                }
    }
}