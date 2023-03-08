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
package org.jetbrains.kotlin.android

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.android.AndroidResourceExternalAnnotatorBase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtJavaFieldSymbol
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Annotator which puts colors and image icons in the editor gutter when referenced in Kotlin files.
 */
class AndroidKotlinResourceExternalAnnotator : AndroidResourceExternalAnnotatorBase() {
    override fun collectInformation(file: PsiFile, editor: Editor): FileAnnotationInfo? {
        val facet = file.androidFacet ?: return null
        val annotationInfo = FileAnnotationInfo(facet, file, editor)
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                val reference = element as? KtNameReferenceExpression ?: return
                val resourceReference = if (isK2Plugin()) {
                    reference.resourceReferenceK2()
                } else {
                    reference.resourceReference()
                } ?: return
                annotationInfo.elements.add(FileAnnotationInfo.AnnotatableElement(resourceReference, element))
            }

            private fun AndroidPsiUtils.ResourceReferenceType.namespace() = if (this == AndroidPsiUtils.ResourceReferenceType.FRAMEWORK) {
                ResourceNamespace.ANDROID
            } else {
                ResourceNamespace.RES_AUTO
            }

            private fun KtNameReferenceExpression.resourceReference(): ResourceReference? {
                val referenceTarget = resolveToCall()?.resultingDescriptor as? JavaPropertyDescriptor ?: return null
                val type = referenceTarget.getAndroidResourceType() ?: return null
                if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
                    val referenceType = referenceTarget.getResourceReferenceType()
                    return ResourceReference(referenceType.namespace(), type, getReferencedName())
                }
                return null
            }

            private fun KtNameReferenceExpression.resourceReferenceK2(): ResourceReference? = analyze(this) {
                val javaFieldSymbol = mainReference.resolveToSymbol() as? KtJavaFieldSymbol ?: return@analyze null
                val type = getAndroidResourceType(javaFieldSymbol) ?: return null
                if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
                    val referenceType = getResourceReferenceType(javaFieldSymbol)
                    return@analyze ResourceReference(referenceType.namespace(), type, getReferencedName())
                }
                null
            }
        })
        return annotationInfo.takeIf { annotationInfo.elements.isNotEmpty() }
    }
}