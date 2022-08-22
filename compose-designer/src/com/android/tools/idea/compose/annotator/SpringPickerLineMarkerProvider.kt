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
package com.android.tools.idea.compose.annotator

import com.android.SdkConstants
import com.android.tools.idea.compose.pickers.PsiPickerManager
import com.android.tools.idea.compose.pickers.spring.model.SpringPickerPropertiesModel
import com.android.tools.idea.compose.preview.DECLARATION_FLOAT_SPEC
import com.android.tools.idea.compose.preview.DECLARATION_SPRING
import com.android.tools.idea.compose.preview.DECLARATION_SPRING_SPEC
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.CommonClassNames.JAVA_LANG_FLOAT
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType

private val SpringTypesFqNames =
  setOf(
    // TODO(b/190058778): Support more types for Spring
    JAVA_LANG_FLOAT
  )

class SpringPickerLineMarkerProvider : LineMarkerProviderDescriptor() {
  private val log = Logger.getInstance(this.javaClass)

  override fun getName(): String = message("picker.spring.annotator.name")

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element !is LeafPsiElement) return null
    if (element.tokenType != KtTokens.IDENTIFIER) return null
    if (!element.isValid) return null
    if (element.getModuleSystem()?.isSpringPickerEnabled() != true) return null

    val hasGenericType =
      when (element.text) {
        DECLARATION_SPRING_SPEC, DECLARATION_SPRING -> true
        DECLARATION_FLOAT_SPEC -> false
        else -> return null
      }

    val module = element.module ?: return null

    val callElement = element.parentOfType<KtCallElement>() ?: return null
    val resolvedCall = callElement.getResolvedCall(callElement.analyze(BodyResolveMode.PARTIAL))
    val resolvedPackage = resolvedCall?.candidateDescriptor?.containingPackage()
    if (resolvedPackage == null) {
      log.warn("Unable to resolve package for SpringSpec call")
      return null
    }
    if (!resolvedPackage.startsWith(Name.identifier(SdkConstants.PACKAGE_COMPOSE_ANIMATION)))
      return null

    if (hasGenericType) {
      val uCallElement = callElement.toUElementOfType<UCallExpression>() ?: return null
      val resolvedType =
        (uCallElement.getExpressionType() as? PsiClassType)?.parameters?.firstOrNull() as?
          PsiClassType
          ?: return null
      val qualifiedName =
        (resolvedType as? PsiClassReferenceType)?.reference?.qualifiedName ?: return null
      if (!SpringTypesFqNames.contains(qualifiedName)) {
        log.debug("Unsupported SpringSpec type: $qualifiedName")
        return null
      }
    }
    val model =
      SpringPickerPropertiesModel(
        project = module.project,
        module = module,
        resolvedCall = resolvedCall
      )

    return LineMarkerInfo<PsiElement>(
      element,
      element.textRange,
      AllIcons.Actions.InlayGear,
      { message("picker.spring.annotator.tooltip") },
      { mouseEvent, _ ->
        PsiPickerManager.show(
          location = RelativePoint(mouseEvent.component, mouseEvent.point).screenPoint,
          displayTitle = message("picker.spring.title"),
          model = model
        )
      },
      GutterIconRenderer.Alignment.LEFT,
      { message("picker.spring.annotator.tooltip") }
    )
  }
}
