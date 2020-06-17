/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.kotlin.tryEvaluateConstant
import com.android.tools.idea.kotlin.tryEvaluateConstantAsText
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.deleteElementAndCleanParent
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi


/**
 * A [PsiPropertyItem] for a named parameter.
 *
 * @param project the [Project] the PSI belongs to.
 * @param model the [PsiCallPropertyModel] managing this property.
 * @param resolvedCall the parent [ResolvedCall] that contains this parameter.
 * @param descriptor the [ValueParameterDescriptor] of this parameter, containing the parameter metadata.
 * @param argumentExpression the initial [KtExpression] for the argument when this parameter was initialized.
 */
class PsiCallParameterPropertyItem(
  private val project: Project,
  private val model: PsiCallPropertyModel,
  private val resolvedCall: ResolvedCall<*>,
  private val descriptor: ValueParameterDescriptor,
  private var argumentExpression: KtExpression?) : PsiPropertyItem {

  override var name: String
    get() = descriptor.name.identifier
    // We do not support editing property names.
    set(_) {}

  override val defaultValue: String? by lazy {
    (descriptor.source.getPsi() as? KtParameter)?.defaultValue?.tryEvaluateConstantAsText()
  }

  override fun isSameProperty(qualifiedName: String): Boolean = false
  override val namespace: String = ""

  override var value: String? = null
    get() = argumentExpression?.tryEvaluateConstantAsText() ?: defaultValue
    set(value) {
      if (value != field) {
        field = value

        writeNewValue(value)
        model.firePropertyValuesChanged()
      }
    }

  private fun writeNewValue(value: String?) {
    if (value == null) {
      WriteCommandAction.runWriteCommandAction(project) {
        argumentExpression?.parent?.deleteElementAndCleanParent()
        argumentExpression = null
      }
      return
    }

    val parameterString = if (descriptor.type.nameIfStandardType == Name.identifier("String")) {
      "$name = \"$value\""
    }
    else {
      "$name = $value"
    }

    WriteCommandAction.runWriteCommandAction(project) {
      var newValueArgument = model.psiFactory.createArgument(parameterString)
      val currentArgumentExpression = argumentExpression

      if (currentArgumentExpression != null) {
        newValueArgument = currentArgumentExpression.parent.replace(newValueArgument) as KtValueArgument
      }
      else {
        if (resolvedCall.call.valueArgumentList == null) {
          val newArgumentsList = model.psiFactory.createCallArguments("()").apply {
            addArgument(newValueArgument)
          }
          newValueArgument = resolvedCall.call.callElement.add(newArgumentsList) as KtValueArgument
        }
        else {
          newValueArgument = resolvedCall.call.valueArgumentList!!.addArgument(newValueArgument)
        }
      }
      argumentExpression = newValueArgument.getArgumentExpression()
    }
  }
}


/**
 * Given a resolved called, this method returns the collection of editable [PsiPropertyItem]s.
 */
private fun parserResolvedCallToPsiPropertyItems(project: Project,
                                                 model: PsiCallPropertyModel,
                                                 resolvedCall: ResolvedCall<*>): Collection<PsiPropertyItem> =
  ReadAction.compute<Collection<PsiPropertyItem>, Throwable> {
    return@compute resolvedCall.valueArguments.map { (descriptor, resolved) ->
      PsiCallParameterPropertyItem(project,
                                   model,
                                   resolvedCall,
                                   descriptor,
                                   (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression())
    }.toList()
  }

/**
 * [PsiPropertyModel] for pickers handling calls. This is common in Compose where most pickers interact with method calls.
 *
 * For example, a theme in Compose is a method call. This model allows editing those properties.
 * ```
 * val darkThemePalette = themePalette(
 *   colorPrimary = Color(12, 13, 14)
 * )
 * ```
 *
 * The same applies for annotations, where the parameters are considered by the PSI parsing as a method call.
 * ```
 * @Preview(name = "Hello", group = "group")
 * ```
 *
 * In both cases, this [PsiCallPropertyModel] will deal with the named parameters as properties.
 */
class PsiCallPropertyModel internal constructor(private val project: Project,
                                                resolvedCall: ResolvedCall<*>) : PsiPropertyModel() {
  private val psiPropertiesCollection = parserResolvedCallToPsiPropertyItems(
    project, this, resolvedCall)

  val psiFactory: KtPsiFactory by lazy { KtPsiFactory(project, true) }

  override val properties: PropertiesTable<PsiPropertyItem> = PropertiesTable.create(
    HashBasedTable.create<String, String, PsiPropertyItem>().also { table ->
      psiPropertiesCollection.forEach {
        table.put(it.namespace, it.name, it)
      }
    })

  companion object {
    fun fromPreviewElement(project: Project, previewElement: PreviewElement): PsiCallPropertyModel {
      val annotationEntry = previewElement.previewElementDefinitionPsi?.element as? KtAnnotationEntry
      val resolvedCall = annotationEntry?.getResolvedCall(annotationEntry.analyze(BodyResolveMode.FULL))!!
      return PsiCallPropertyModel(project, resolvedCall)
    }
  }
}