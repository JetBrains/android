package com.android.tools.idea.compose.preview.pickers.properties

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * A [PsiPropertyItem] for Float parameters.
 *
 * Adds the Float suffix 'f' when displaying values.
 */
internal class FloatPsiCallParameter(project: Project,
                                     model: PsiCallPropertyModel,
                                     resolvedCall: ResolvedCall<*>,
                                     descriptor: ValueParameterDescriptor,
                                     argumentExpression: KtExpression?,
                                     initialValue: String?) : PsiCallParameterPropertyItem(project,
                                                                                           model,
                                                                                           resolvedCall,
                                                                                           descriptor,
                                                                                           argumentExpression,
                                                                                           initialValue
) {
  override var value: String?
    get() = argumentExpression?.constantValueOrNull(argumentExpression?.analyze())?.value?.let {
      "%.2f".format(it) + "f"
    } ?: super.value
    set(newValue) {
      super.value = newValue
    }
}