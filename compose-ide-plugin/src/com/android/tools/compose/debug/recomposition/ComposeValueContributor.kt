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
package com.android.tools.compose.debug.recomposition

import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.debug.recomposition.StateObject.Parameter
import com.android.tools.compose.debug.recomposition.StateObject.ThisObject
import com.android.tools.compose.isComposableFunction
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
import com.intellij.debugger.engine.CompoundPositionManager
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.suggested.startOffset
import com.intellij.xdebugger.frame.XNamedValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrameValueContributor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.CancellationException

private const val COMPOSER_VAR = "\$composer"
private const val CHANGED_VAR = "\$changed"
private const val DIRTY_VAR = "\$dirty"

/**
 * Contributes Compose specific information to the debugger variable view.
 */
internal class ComposeValueContributor : KotlinStackFrameValueContributor {
  override fun contributeValues(
    frame: KotlinStackFrame,
    context: EvaluationContextImpl,
    variables: List<LocalVariableProxyImpl>,
  ): List<XNamedValue> {
    return try {
      doContributeValues(frame, context, variables)
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      thisLogger().warn("Unexpected error building a Compose state node", t)
      emptyList()
    }
  }

  private fun doContributeValues(
    frame: KotlinStackFrame,
    context: EvaluationContextImpl,
    variables: List<LocalVariableProxyImpl>,
  ): List<XNamedValue> {
    if (!Registry.`is`("androidx.compose.debugger.recomposition.node")) {
      return emptyList()
    }
    val composer = frame.findComposer() ?: return emptyList()

    val values = mutableListOf<XNamedValue>()
    val variableMap = variables.associateBy { it.name() }

    // The composer variable may have been optimized out of an inline frame.
    if (!variableMap.containsKey(COMPOSER_VAR)) {
      val nodeManager = context.debugProcess.xdebugProcess?.nodeManager
      if (nodeManager == null) {
        thisLogger().warn("Unable to add $COMPOSER_VAR. nodeManager is null")
      } else {
        values.add(JavaValue.create(null, LocalVariableDescriptorImpl(context.project, composer), context, nodeManager, false))
      }
    }

    val changed = variableMap[CHANGED_VAR] ?: return emptyList()
    val dirty = variableMap[DIRTY_VAR]
    val forced = (dirty ?: changed).intValue(frame) and 0b1 != 0

    val states = getParamStates(frame, variables)

    val stateObjects = mutableListOf<StateObject>()

    // ComposeValueContributor.contributeValues() is called with "variables == thisVariables + otherVariables" so if there is a "this"
    // variable, it's going to be the first one.
    val firstParameter = variables.first()
    if (firstParameter.name() == "this") {
      val name = "this@${firstParameter.typeName().substringAfterLast(".")}"
      stateObjects.add(Parameter(states.first(), name, firstParameter))
    }

    try {
      val functionInfo = getFunctionInfo(context.debugProcess.positionManager, frame.stackFrameProxy.location())
      // Named parameters
      functionInfo.parameters.zip(states.drop(stateObjects.size)).forEach { (param, state) ->
        stateObjects.add(Parameter(state, param, variableMap[param]))
      }

      // This object
      if (frame.descriptor.thisObject != null) {
        stateObjects.add(ThisObject(states[stateObjects.size]))
      }
      values.add(ComposeStateNode(context, forced, functionInfo.description, stateObjects))
    }
    catch (e: IllegalStateException) {
      thisLogger().error("Error fetching parameters for $frame", e)
      values.add(ErrorNode(ComposeBundle.message("recomposition.state.missing.parameters")))
    }
    return values
  }

  /**
   * Finds the parameter names of a function.
   *
   * Inspired by [org.jetbrains.kotlin.idea.debugger.coroutine.KotlinVariableNameFinder.findVariableNames].
   */
  private fun getFunctionInfo(positionManager: CompoundPositionManager, location: Location): FunctionInfo {
    return runReadAction {
      val element = positionManager.getSourcePosition(location)?.elementAt ?: throw IllegalStateException("Unable to get source position")
      val function = element.parentOfType<KtFunction>(withSelf = true) ?: throw IllegalStateException("Unable to find KtFunction element")
      val parameters = function.valueParameters.mapNotNull { it.name }
      FunctionInfo(getDescription(function), parameters)
    }
  }

  private class FunctionInfo(val description: String, val parameters: List<String>)
}

private fun getDescription(function: KtFunction): String {
  return when (function) {
    is KtFunctionLiteral -> ComposeBundle.message("recomposition.state.function.description.lambda", getLambdaName(function))
    else -> ComposeBundle.message("recomposition.state.function.description.function", function.nameAsSafeName.asString())
  }
}

/**
 * Search parent hierarchy for either a declaration of a composable function or a call to a composable function.
 */
private fun getLambdaName(lambda: KtFunctionLiteral): String {
  var element: PsiElement = lambda
  while (true) {
    element = element.parentOfTypes(KtNamedFunction::class, KtCallExpression::class) ?: return "lambda"
    when {
      element is KtNamedFunction && element.isComposableFunction() -> return element.getLambdaName()
      element is KtCallExpression && element.isTargetComposable() -> return element.getLambdaName()
    }
  }
}

private fun KtNamedFunction.getLambdaName() = "lambda@${nameAsSafeName.asString()}"

private fun KtCallExpression.getLambdaName() = calleeExpression?.let { "lambda@${it.text}" } ?: "lambda"

private fun KtCallExpression.isTargetComposable(): Boolean {
  val editor = findExistingEditor() ?: return false
  val target = TargetElementUtil.getInstance().findTargetElement(editor, REFERENCED_ELEMENT_ACCEPTED, startOffset) ?: return false
  return target.isComposableFunction()
}

private fun getParamStates(frame: KotlinStackFrame, variables: List<LocalVariableProxyImpl>): List<ParamState> {
  val vars = (variables.filterByPrefix(DIRTY_VAR).takeIf { it.isNotEmpty() } ?: variables.filterByPrefix(CHANGED_VAR))
  return ParamState.decode(vars.map { it.intValue(frame) })
}

private fun LocalVariableProxyImpl.intValue(frame: KotlinStackFrame) = (frame.stackFrameProxy.getValue(this) as IntegerValue).value()

private fun List<LocalVariableProxyImpl>.filterByPrefix(prefix: String) = filter { it.name().startsWith(prefix) }.sortedBy { it.name() }

private fun KotlinStackFrame.findComposer() = stackFrameProxy.visibleVariables().find { it.name() == COMPOSER_VAR }
