/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.android

import com.android.resources.ResourceType.COLOR
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.MIPMAP
import com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType.FRAMEWORK
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.GutterIconRenderer
import com.android.tools.idea.res.resolveColor
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.AndroidAnnotatorUtil.ColorRenderer
import org.jetbrains.android.AndroidAnnotatorUtil.findResourceValue
import org.jetbrains.android.AndroidAnnotatorUtil.pickConfiguration
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.psi.KtReferenceExpression


class AndroidResourceReferenceAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Gutter icon annotator is run by {@link org.jetbrains.kotlin.android.AndroidKotlinResourceExternalAnnotator} when flag is set.
        if (StudioFlags.GUTTER_ICON_ANNOTATOR_IN_BACKGROUND_ENABLED.get()) return
        val reference = element as? KtReferenceExpression ?: return
        val androidFacet = AndroidFacet.getInstance(element) ?: return
        val referenceTarget = reference.getResourceReferenceTargetDescriptor() ?: return
        val resourceType = referenceTarget.getAndroidResourceType() ?: return

        if (resourceType != COLOR && resourceType != DRAWABLE && resourceType != MIPMAP) {
            return
        }

        val referenceType = referenceTarget.getResourceReferenceType()
        val configuration = pickConfiguration(element.containingFile, androidFacet) ?: return
        val resourceValue = findResourceValue(
            resourceType,
            reference.text,
            referenceType == FRAMEWORK,
            androidFacet.module,
            configuration
        ) ?: return

        val resourceResolver = configuration.resourceResolver ?: return

        if (resourceType == COLOR) {
            val color = resourceResolver.resolveColor(resourceValue, element.project)
            if (color != null) {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.gutterIconRenderer = ColorRenderer(element, color, false, configuration)
            }
        }
        else {
            val iconFile =
                AndroidAnnotatorUtil.resolveDrawableFile(resourceValue, resourceResolver, androidFacet)
            if (iconFile != null) {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.gutterIconRenderer = GutterIconRenderer(resourceResolver, androidFacet, iconFile, configuration)
            }
        }
    }

    private fun KtReferenceExpression.getResourceReferenceTargetDescriptor(): JavaPropertyDescriptor? =
            resolveToCall()?.resultingDescriptor as? JavaPropertyDescriptor
}
