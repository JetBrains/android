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
package com.android.tools.idea.compose.preview

import com.android.sdklib.SdkVersionInfo
import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.util.MAX_HEIGHT
import com.android.tools.idea.compose.preview.util.MAX_WIDTH
import com.android.tools.idea.compose.preview.util.isInUnitTestFile
import com.android.tools.idea.compose.preview.util.isValidPreviewLocation
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.findValueArgument
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/**
 * Base class for inspection that depend on methods and annotation classes annotated with
 * `@Preview`, or with a MultiPreview.
 */
abstract class BasePreviewAnnotationInspection : AbstractKotlinInspection() {
  /**
   * Will be true if the inspected file imports the `@Preview` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isPreviewFile: Boolean = false
  /**
   * Will be true if the inspected file imports the `@Composable` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isComposableFile: Boolean = false

  override fun getGroupDisplayName() = message("inspection.group.name")

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating a function.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param function The function that was annotated with `@Preview` or with a MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   * @param isMultiPreview true when [previewAnnotation] is a MultiPreview
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  )

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating an annotation
   * class.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param annotationClass The annotation class that was annotated with `@Preview` or with a
   *   MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   * @param isMultiPreview true when [previewAnnotation] is a MultiPreview
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  )

  final override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor =
    if (session.file.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode) {
      object : KtVisitorVoid() {
        override fun visitImportDirective(importDirective: KtImportDirective) {
          super.visitImportDirective(importDirective)

          isPreviewFile =
            isPreviewFile ||
              COMPOSE_PREVIEW_ANNOTATION_FQN == importDirective.importedFqName?.asString()
          isComposableFile =
            isComposableFile ||
              COMPOSABLE_FQ_NAMES.contains(importDirective.importedFqName?.asString())
        }

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
          super.visitAnnotationEntry(annotationEntry)

          isPreviewFile =
            isPreviewFile || annotationEntry.fqNameMatches(COMPOSE_PREVIEW_ANNOTATION_FQN)
          isComposableFile = isComposableFile || annotationEntry.fqNameMatches(COMPOSABLE_FQ_NAMES)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
          super.visitNamedFunction(function)

          if (!isPreviewFile && !isComposableFile) {
            return
          }

          function.annotationEntries.forEach {
            when {
              // Preview
              it.fqNameMatches(COMPOSE_PREVIEW_ANNOTATION_FQN) ->
                visitPreviewAnnotation(holder, function, it, false)
              // MultiPreview
              (it.toUElement() as? UAnnotation).isMultiPreviewAnnotation() ->
                visitPreviewAnnotation(holder, function, it, true)
              // Other
              else -> return@forEach // do nothing
            }
          }
        }

        override fun visitClass(klass: KtClass) {
          super.visitClass(klass)

          if (!klass.isAnnotation() || !StudioFlags.COMPOSE_MULTIPREVIEW.get()) return

          klass.annotationEntries.forEach {
            when {
              // Preview
              it.fqNameMatches(COMPOSE_PREVIEW_ANNOTATION_FQN) ->
                visitPreviewAnnotation(holder, klass, it, false)
              // MultiPreview
              (it.toUElement() as? UAnnotation).isMultiPreviewAnnotation() ->
                visitPreviewAnnotation(holder, klass, it, true)
              // Other
              else -> return@forEach // do nothing
            }
          }
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
}

/**
 * Returns whether the [KtParameter] can be used in the preview. This will return true if the
 * parameter has a default value or a value provider.
 */
private fun KtParameter.isAcceptableForPreview(): Boolean =
  hasDefaultValue() ||
    // We also accept parameters with the @PreviewParameter annotation
    annotationEntries.any { it.fqNameMatches(COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN) }

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, does
 * not have parameters.
 */
class PreviewAnnotationInFunctionWithParametersInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    if (function.valueParameters.any { !it.isAcceptableForPreview() }) {
      holder.registerProblem(
        previewAnnotation.psiOrParent as PsiElement,
        message("inspection.no.parameters.or.provider.description"),
        ProblemHighlightType.ERROR
      )
    }
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, has
 * at most one `@PreviewParameter`.
 */
class PreviewMultipleParameterProvidersInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // Find the second PreviewParameter annotation if any
    val secondPreviewParameter =
      function.valueParameters
        .mapNotNull {
          it.annotationEntries.firstOrNull { annotation ->
            annotation.fqNameMatches(COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN)
          }
        }
        .drop(1)
        .firstOrNull()
        ?: return

    // Flag the second annotation as the error
    holder.registerProblem(
      secondPreviewParameter as PsiElement,
      message("inspection.no.multiple.preview.provider.description"),
      ProblemHighlightType.ERROR
    )
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, is
 * also annotated with `@Composable`.
 */
class PreviewNeedsComposableAnnotationInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    val nonComposable = function.annotationEntries.none { it.fqNameMatches(COMPOSABLE_FQ_NAMES) }
    if (nonComposable) {
      holder.registerProblem(
        previewAnnotation.psiOrParent as PsiElement,
        message("inspection.no.composable.description"),
        ProblemHighlightType.ERROR
      )
    }
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, is a
 * top level function. This is to avoid `@Preview` methods to be instance methods of classes that we
 * can not instantiate.
 */
class PreviewMustBeTopLevelFunction : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    if (function.isValidPreviewLocation()) return

    holder.registerProblem(
      previewAnnotation.psiOrParent as PsiElement,
      message("inspection.top.level.function"),
      ProblemHighlightType.ERROR
    )
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}

/**
 * Inspection that checks that `@Preview` width parameter doesn't go higher than [MAX_WIDTH], and
 * the height parameter doesn't go higher than [MAX_HEIGHT].
 */
class PreviewDimensionRespectsLimit : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkMaxWidthAndHeight(holder, previewAnnotation, isMultiPreview)
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkMaxWidthAndHeight(holder, previewAnnotation, isMultiPreview)
  }

  private fun checkMaxWidthAndHeight(
    holder: ProblemsHolder,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // MultiPreview parameters don't affect the Previews
    if (isMultiPreview) return

    previewAnnotation.findValueArgument(PARAMETER_WIDTH_DP)?.let {
      if (it.exceedsLimit(MAX_WIDTH)) {
        holder.registerProblem(
          it.psiOrParent as PsiElement,
          message("inspection.width.limit.description", MAX_WIDTH),
          ProblemHighlightType.WARNING
        )
      }
    }

    previewAnnotation.findValueArgument(PARAMETER_HEIGHT_DP)?.let {
      if (it.exceedsLimit(MAX_HEIGHT)) {
        holder.registerProblem(
          it.psiOrParent as PsiElement,
          message("inspection.height.limit.description", MAX_HEIGHT),
          ProblemHighlightType.WARNING
        )
      }
    }
  }
}

/** Inspection that checks if `@Preview` fontScale parameter is not positive. */
class PreviewFontScaleMustBeGreaterThanZero : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkMinFontScale(holder, previewAnnotation, isMultiPreview)
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkMinFontScale(holder, previewAnnotation, isMultiPreview)
  }

  private fun checkMinFontScale(
    holder: ProblemsHolder,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // MultiPreview parameters don't affect the Previews
    if (isMultiPreview) return

    previewAnnotation.findValueArgument(PARAMETER_FONT_SCALE)?.let {
      val argumentExpression = it.getArgumentExpression() ?: return
      val fontScale =
        (ConstantExpressionEvaluator.getConstant(argumentExpression, argumentExpression.analyze())
          ?.getValue(TypeUtils.DONT_CARE) as? Float)
          ?: return

      if (fontScale <= 0) {
        holder.registerProblem(
          it.psiOrParent as PsiElement,
          message("inspection.preview.font.scale.description"),
          ProblemHighlightType.ERROR
        )
      }
    }
  }
}

/** Inspection that checks if `@Preview` apiLevel is valid. */
class PreviewApiLevelMustBeValid : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkApiLevelIsValid(holder, previewAnnotation, isMultiPreview)
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    checkApiLevelIsValid(holder, previewAnnotation, isMultiPreview)
  }

  private fun checkApiLevelIsValid(
    holder: ProblemsHolder,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // MultiPreview parameters don't affect the Previews
    if (isMultiPreview) return

    val supportedApiLevels =
      previewAnnotation.module?.let { module ->
        ConfigurationManager.findExistingInstance(module)
          ?.targets
          ?.filter { ConfigurationManager.isLayoutLibTarget(it) }
          ?.map { it.version.apiLevel }
      }
        ?: listOf(SdkVersionInfo.LOWEST_COMPILE_SDK_VERSION, SdkVersionInfo.HIGHEST_SUPPORTED_API)

    val (min, max) = supportedApiLevels.minOrNull()!! to supportedApiLevels.maxOrNull()!!

    previewAnnotation.findValueArgument(PARAMETER_API_LEVEL)?.let {
      val argumentExpression = it.getArgumentExpression() ?: return
      val apiLevel =
        (ConstantExpressionEvaluator.getConstant(argumentExpression, argumentExpression.analyze())
          ?.getValue(TypeUtils.DONT_CARE) as? Int)
          ?: return

      if (apiLevel < min || apiLevel > max) {
        holder.registerProblem(
          it.psiOrParent as PsiElement,
          message("inspection.preview.api.level.description", min, max),
          ProblemHighlightType.ERROR
        )
      }
    }
  }
}

/**
 * Inspection that checks that functions annotated with `@Preview`, or with a MultiPreview, are not
 * in a unit test file
 */
class PreviewNotSupportedInUnitTestFiles : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // If the annotation is not in a unit test file, then this inspection has nothing to do
    if (!function.isInUnitTestFile()) return

    holder.registerProblem(
      previewAnnotation.psiOrParent as PsiElement,
      message("inspection.unit.test.files"),
      ProblemHighlightType.ERROR
    )
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
    isMultiPreview: Boolean
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}

private fun KtValueArgument.exceedsLimit(limit: Int): Boolean {
  val argumentExpression = getArgumentExpression() ?: return false
  val dimension =
    (ConstantExpressionEvaluator.getConstant(argumentExpression, argumentExpression.analyze())
      ?.getValue(TypeUtils.DONT_CARE) as? Int)
      ?: return false
  return dimension > limit
}
