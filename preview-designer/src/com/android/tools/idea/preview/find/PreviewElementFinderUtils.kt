/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.find

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsMethodImpl
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.tryResolve

/** Helper method that returns a map with all the default values of a preview annotation */
fun UAnnotation.findPreviewDefaultValues(): Map<String, String?> =
  try {
    when (val resolvedImplementation = this.resolve()) {
      is ClsClassImpl ->
        resolvedImplementation.methods.associate { psiMethod ->
          Pair(psiMethod.name, (psiMethod as ClsMethodImpl).defaultValue?.text?.trim('"'))
        }
      is KtLightClass ->
        resolvedImplementation.methods.associate { psiMethod ->
          Pair(psiMethod.name, (psiMethod as KtLightMethod).defaultValue?.text?.trim('"'))
        }
      else -> mapOf()
    }
  } catch (_: IndexNotReadyException) {
    // UAnnotation#resolve needs the index to be ready, if called during indexing, we simply do not
    // return any default values.
    mapOf()
  }

/** Helper getter that returns a qualified name for a given [UMethod] */
val UMethod.qualifiedName: String
  get() = "${(this.uastParent as UClass).qualifiedName}.${this.name}"

/** Helper method that creates a smart PSI pointer from a given [UElement] */
fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

/**
 * Returns the number of preview annotations attached to this element. This method does not count
 * preview annotations that are indirectly referenced through the annotation graph.
 */
suspend fun UElement.directPreviewChildrenCount(
  isPreviewAnnotation: suspend UElement?.() -> Boolean
) = getUAnnotations().count { it.isPreviewAnnotation() }

/**
 * Class that helps to build a Preview's name and parameter name.
 *
 * @see create to create the helper
 * @see buildPreviewName
 * @see buildParameterName
 */
class AnnotationPreviewNameHelper
private constructor(
  private val methodName: String,
  private val parentAnnotationInfo: ParentAnnotationInfo?,
) {
  private data class ParentAnnotationInfo(
    val annotationName: String?,
    val traversedPreviewChildrenCount: Int,
    val directPreviewChildrenCount: Int,
  )

  /**
   * Create the name to be displayed for a Preview by using the [methodName] and the [nameParameter]
   * when available, or otherwise trying to use some information from the [ParentAnnotationInfo].
   */
  fun buildPreviewName(nameParameter: String? = null): String {
    return if (nameParameter != null) "$methodName - $nameParameter"
    else buildParentAnnotationInfo()?.let { "$methodName - $it" } ?: methodName
  }

  /**
   * Create the name to be displayed for a Preview by using the [nameParameter] when available, or
   * otherwise trying to use some information from the [ParentAnnotationInfo].
   */
  fun buildParameterName(nameParameter: String? = null): String? =
    nameParameter ?: buildParentAnnotationInfo()

  private fun buildParentAnnotationInfo(): String? =
    parentAnnotationInfo?.let {
      "${it.annotationName} ${it.traversedPreviewChildrenCount.toString().padStart(it.directPreviewChildrenCount.toString().length, '0')}"
    }

  companion object {
    /**
     * Method that builds a [AnnotationPreviewNameHelper] from a given [NodeInfo], [methodName] and
     * [isPreviewAnnotation].
     *
     * @param node contains the [NodeInfo] that will be used to retrieve extra information that will
     *   be used to build the preview and parameter name whenever a nameParameter is not available
     * @param methodName the name of the method that is annotated with the preview annotation
     * @param isPreviewAnnotation a method used to identify which [UElement]s are considered
     *   previews. The method is `suspendable` to allow for non-blocking read actions and can be
     *   slow.
     * @see AnnotationPreviewNameHelper
     */
    suspend fun create(
      node: NodeInfo<UAnnotationSubtreeInfo>?,
      methodName: String,
      isPreviewAnnotation: suspend UElement?.() -> Boolean,
    ): AnnotationPreviewNameHelper {
      val parentAnnotationInfo =
        node?.parent?.let { parent ->
          val parentAnnotation = parent.element as? UAnnotation ?: return@let null
          ParentAnnotationInfo(
            annotationName = readAction { (parentAnnotation.tryResolve() as PsiClass).name },
            traversedPreviewChildrenCount =
              parent.subtreeInfo?.children?.count { it.element.isPreviewAnnotation() } ?: 0,
            directPreviewChildrenCount =
              parentAnnotation.directPreviewChildrenCount(isPreviewAnnotation),
          )
        }
      return AnnotationPreviewNameHelper(
        methodName = methodName,
        parentAnnotationInfo = parentAnnotationInfo,
      )
    }
  }
}
