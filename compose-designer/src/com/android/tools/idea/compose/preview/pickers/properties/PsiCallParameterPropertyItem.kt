package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.kotlin.tryEvaluateConstantAsText
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.core.deleteElementAndCleanParent
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * A [PsiPropertyItem] for a named parameter.
 *
 * @param project the [Project] the PSI belongs to.
 * @param model the [PsiCallPropertyModel] managing this property.
 * @param resolvedCall the parent [ResolvedCall] that contains this parameter.
 * @param descriptor the [ValueParameterDescriptor] of this parameter, containing the parameter metadata.
 * @param argumentExpression the initial [KtExpression] for the argument when this parameter was initialized.
 * @param defaultValue the default value string for the parameter, this is the value that the parameter takes when it does not have a
 *          user-assigned value
 */
internal open class PsiCallParameterPropertyItem(
  val project: Project,
  private val model: PsiCallPropertyModel,
  private val resolvedCall: ResolvedCall<*>,
  private val descriptor: ValueParameterDescriptor,
  protected var argumentExpression: KtExpression?,
  override val defaultValue: String?) : PsiPropertyItem {

  override var name: String
    get() = descriptor.name.identifier
    // We do not support editing property names.
    set(_) {}

  override fun isSameProperty(qualifiedName: String): Boolean = false

  override val namespace: String = ""

  override var value: String? = null
    get() = argumentExpression?.tryEvaluateConstantAsText()
    set(value) {
      if (value != field) {
        field = value

        writeNewValue(value, false)
      }
    }

  /**
   * Writes the [value] to the property's PsiElement, wrapped in double quotation marks when the property's type is String, unless
   * [writeRawValue] is True, in which case it will be written as is.
   */
  protected fun writeNewValue(value: String?, writeRawValue: Boolean) {
    if (value == null) {
      WriteCommandAction.runWriteCommandAction(project) {
        argumentExpression?.parent?.deleteElementAndCleanParent()
        argumentExpression = null
      }
      return
    }

    val parameterString = if (descriptor.type.nameIfStandardType == Name.identifier("String") && !writeRawValue) {
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
          resolvedCall.call.callElement.add(model.psiFactory.createCallArguments("()"))
        }
        newValueArgument = resolvedCall.call.valueArgumentList!!.addArgument(newValueArgument)
      }
      argumentExpression = newValueArgument.getArgumentExpression()
      argumentExpression?.parent?.let {
        CodeStyleManager.getInstance(it.project).reformat(it)
      }
    }
    model.firePropertyValuesChanged()
  }
}