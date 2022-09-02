package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.BooleanValidator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/** A [PsiPropertyItem] for three state Boolean parameters. */
internal class BooleanPsiCallParameter(
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
    BooleanValidator
  )
