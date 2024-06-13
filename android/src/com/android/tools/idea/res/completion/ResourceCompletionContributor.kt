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
package com.android.tools.idea.res.completion

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.parseColor
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.GutterIconCache
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.res.isAccessible
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceExpression
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * [CompletionContributor] base class for Kotlin vs. Java-agnostic transformations and filtering of
 * resources.
 *
 * Currently, does the following:
 * * Decorates [LookupElement]s for `Drawable` resources with an [Icon] rendered from the
 *   `Drawable`.
 * * Decorates [LookupElement]s for `Color` resources with an [Icon] of the appropriate color, and
 *   with tail text showing the hex code of the color.
 * * Filters out [LookupElement]s for private resources, unless they are qualified by a package that
 *   is not the current package.
 */
sealed class ResourceCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
  ) {
    val facet = AndroidFacet.getInstance(parameters.position)
    val filterPrivateResources =
      facet?.let { shouldFilterPrivateResources(parameters.position, it) } ?: false
    val transformContext =
      TransformContext(facet, parameters.position.containingFile, filterPrivateResources)
    resultSet.runRemainingContributors(parameters) {
      it.transform(transformContext)?.let(resultSet::passResult)
    }
  }

  /** Returns whether we should filter private resources in the current [position]. */
  protected abstract fun shouldFilterPrivateResources(
    position: PsiElement,
    facet: AndroidFacet,
  ): Boolean
}

/** [CompletionContributor] for Java-specific transformations and filtering of resources. */
class JavaResourceCompletionContributor : ResourceCompletionContributor() {
  override fun shouldFilterPrivateResources(position: PsiElement, facet: AndroidFacet): Boolean {
    // Filter out private resources when completing R.type.name expressions, if any.
    val r =
      (position.parent as? PsiReferenceExpression)
        ?.qualifierReferenceExpression
        ?.qualifierReferenceExpression
        ?.takeIf { it.referenceName == SdkConstants.R_CLASS } ?: return false
    // We do the filtering only on the R class of this module, users who explicitly reference other
    // R classes are assumed to know what they're doing. So if R is unqualified or is qualified by
    // the package or test package name, then filter.
    val rQualifier = r.qualifierExpression ?: return true
    val rQualifierName = (rQualifier as? PsiReferenceExpression)?.qualifiedName ?: return false
    return facet.getModuleSystem().let {
      it.getPackageName() == rQualifierName || it.getTestPackageName() == rQualifierName
    }
  }

  private val PsiReferenceExpression.qualifierReferenceExpression: PsiReferenceExpression?
    get() = qualifierExpression as? PsiReferenceExpression
}

/** [CompletionContributor] for Kotlin-specific transformations and filtering of resources. */
class KotlinResourceCompletionContributor : ResourceCompletionContributor() {
  override fun shouldFilterPrivateResources(position: PsiElement, facet: AndroidFacet): Boolean {
    // Filter out private resources when completing R.type.name expressions, if any.
    val grandparent = position.parent.parent as? KtDotQualifiedExpression ?: return false
    val r =
      (grandparent.receiverExpression as? KtDotQualifiedExpression)?.receiverExpression?.takeIf {
        it.text.substringAfterLast('.') == SdkConstants.R_CLASS
      } ?: return false
    // We do the filtering only on the R class of this module, users who explicitly reference other
    // R classes are assumed to know what they're doing. So if R is unqualified or is qualified by
    // the package or test package name, then filter.
    val rQualifierName =
      (r as? KtDotQualifiedExpression)?.receiverExpression?.text?.filterNot(Char::isWhitespace)
        ?: return true
    return facet.getModuleSystem().let {
      it.getPackageName() == rQualifierName || it.getTestPackageName() == rQualifierName
    }
  }
}

private data class TransformContext(
  val facet: AndroidFacet?,
  val containingFile: PsiFile,
  val filterPrivateResources: Boolean,
)

/** Transforms this [CompletionResult], potentially filtering it out by returning `null`. */
private fun CompletionResult.transform(context: TransformContext): CompletionResult? {
  val element = lookupElement

  if (
    context.facet != null &&
      context.filterPrivateResources &&
      isForPrivateResource(this, context.facet)
  ) {
    return null // Filter out
  }

  // If there's no PsiElement, just leave the result alone.
  val psi = element.psiElement as? ResourceLightField ?: return this

  val decorated =
    when (psi.resourceType) {
      ResourceType.DRAWABLE -> decorateDrawable(psi, context.containingFile, element)
      ResourceType.COLOR -> decorateColor(psi, element)
      else -> null
    } ?: return this

  return withLookupElement(decorated)
}

/** Returns a [LookupElementDecorator] for decorating the `Color` [LookupElement]. */
private fun decorateColor(
  resourceLightField: ResourceLightField,
  original: LookupElement,
): LookupElement? {
  if (!StudioFlags.RENDER_COLORS_IN_AUTOCOMPLETE_ENABLED.get()) return null
  return computeColor(resourceLightField)?.let { ColorResourceLookupElement(original, it) }
}

/** Computes the [Color] associated with the resource represented by [resourceLightField]. */
private fun computeColor(resourceLightField: ResourceLightField): Color? {
  val module = ModuleUtilCore.findModuleForPsiElement(resourceLightField) ?: return null
  return resourceLightField.getResourceItems(module)?.firstNotNullOfOrNull {
    it.resourceValue?.value?.let(::parseColor)
  }
}

private class ColorResourceLookupElement(original: LookupElement, private val color: Color) :
  LookupElementDecorator<LookupElement>(original) {

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.tailText = " (#${Integer.toHexString(color.rgb).uppercase()})"
    presentation.icon = ColorIcon(JBUI.scale(16), color, false)
  }
}

/**
 * Returns a [LookupElementDecorator] for decorating the `Drawable` [LookupElement].
 *
 * If the [Icon] for the Drawable is already available, this will be a
 * [FastDrawableResourceLookupElement], otherwise it will be a [SlowDrawableResourceLookupElement]
 * that actually renders the [Icon] on a background thread via [LookupElement.getExpensiveRenderer].
 */
private fun decorateDrawable(
  resourceLightField: ResourceLightField,
  psiFile: PsiFile,
  original: LookupElement,
): LookupElement? {
  if (!StudioFlags.RENDER_DRAWABLES_IN_AUTOCOMPLETE_ENABLED.get()) return null
  val module = ModuleUtilCore.findModuleForPsiElement(resourceLightField) ?: return null
  val file =
    resourceLightField.getResourceItems(module)?.firstNotNullOfOrNull {
      it.getSourceAsVirtualFile()
    } ?: return null
  GutterIconCache.getInstance(module.project).getIconIfCached(file)?.let {
    return FastDrawableResourceLookupElement(original, it)
  }

  val facet = module.androidFacet ?: return null
  val resolver =
    AndroidAnnotatorUtil.pickConfiguration(psiFile.originalFile, facet)?.resourceResolver
      ?: return null
  return SlowDrawableResourceLookupElement(original, file, resolver, facet)
}

private class FastDrawableResourceLookupElement(original: LookupElement, private val icon: Icon) :
  LookupElementDecorator<LookupElement>(original) {
  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.icon = icon
  }
}

private class SlowDrawableResourceLookupElement(
  original: LookupElement,
  private val file: VirtualFile,
  private val resolver: ResourceResolver,
  private val facet: AndroidFacet,
) : LookupElementDecorator<LookupElement>(original) {
  override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement> =
    object : LookupElementRenderer<SlowDrawableResourceLookupElement>() {
      override fun renderElement(
        element: SlowDrawableResourceLookupElement,
        presentation: LookupElementPresentation,
      ) {
        this@SlowDrawableResourceLookupElement.renderElement(presentation)
        GutterIconCache.getInstance(facet.module.project)
          .getIcon(file, resolver, facet)
          ?.let(presentation::setIcon)
      }
    }
}

private fun ResourceLightField.getResourceItems(module: Module) =
  StudioResourceRepositoryManager.getInstance(module)
    ?.appResources
    ?.getResources(ResourceNamespace.RES_AUTO, resourceType, resourceName)

/**
 * Returns true iff this result is the `bar` in something of the form `R.foo.bar` and this resource
 * is private.
 */
private fun isForPrivateResource(result: CompletionResult, facet: AndroidFacet): Boolean {
  // First get the field itself, e.g. R.foo.bar
  val psiField = result.lookupElement.psiElement as? PsiField ?: return false
  // Now extract the type "foo", provided it is under "R".
  val resourceType =
    psiField.containingClass
      ?.takeIf {
        it.containingClass?.name == SdkConstants.R_CLASS
      } // Don't bother if it's not under R.
      ?.name
      ?.let { ResourceType.fromClassName(it) }
      ?: return false // If there's no type, we can't look it up, so return false.
  val namespace = StudioResourceRepositoryManager.getInstance(facet).namespace
  return !isAccessible(namespace, resourceType, psiField.name, facet)
}
