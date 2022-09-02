package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.FloatValidator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * A [PsiPropertyItem] for Float parameters.
 *
 * Adds the Float suffix 'f' when displaying values.
 */
internal class FloatPsiCallParameter(
  project: Project,
  model: PsiCallPropertyModel,
  resolvedCall: ResolvedCall<*>,
  descriptor: ValueParameterDescriptor,
  argumentExpression: KtExpression?,
  initialValue: String?
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    resolvedCall,
    descriptor,
    argumentExpression,
    initialValue,
    FloatValidator
  ) {
  override var value: String?
    get() = super.value
    set(newValue) {
      super.value = newValue?.toFloatOrNull()?.let { "${it}f" } ?: newValue
    }
}
