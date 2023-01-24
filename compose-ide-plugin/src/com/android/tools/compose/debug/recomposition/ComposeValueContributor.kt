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
import com.intellij.debugger.engine.CompoundPositionManager
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.parentOfType
import com.intellij.xdebugger.frame.XNamedValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrameValueContributor
import org.jetbrains.kotlin.psi.KtFunction
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
      }
      else {
        values.add(JavaValue.create(null, LocalVariableDescriptorImpl(context.project, composer), context, nodeManager, false))
      }
    }

    val changed = variableMap[CHANGED_VAR] ?: return emptyList()
    val dirty = variableMap[DIRTY_VAR]
    val forced = (dirty ?: changed).intValue(frame) and 0b1 != 0

    val states = getParamStates(frame, variables)
    val stateObjects = mutableListOf<StateObject>()

    val firstParameter = variables.minWithOrNull { v1, v2 ->
      v1.variable.compareTo(v2.variable)
    } ?: throw IllegalStateException("Empty variable list") // Should not happen. Variable list at least has `$compose` item.
    if (firstParameter.name() == "this") {
      stateObjects.add(Parameter(states.first(), firstParameter.variable.name(), firstParameter))
    }

    // Named parameters
    try {
      val parameters = findNamedParameters(context.debugProcess.positionManager, frame.stackFrameProxy.location())
      parameters.zip(states.drop(stateObjects.size)).forEach { (param, state) ->
        stateObjects.add(Parameter(state, param, variableMap[param]))
      }
      // This object
      if (frame.descriptor.thisObject != null) {
        stateObjects.add(ThisObject(states[stateObjects.size]))
      }
      values.add(ComposeStateNode(context, forced, stateObjects))

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
  private fun findNamedParameters(positionManager: CompoundPositionManager, location: Location): List<String> {
    val position = positionManager.getSourcePosition(location) ?: throw IllegalStateException("Unable to get source position")
    return runReadAction {
      val function = position.elementAt.parentOfType<KtFunction>(withSelf = true)
                     ?: throw IllegalStateException("Unable to find KtFunction element")
      function.valueParameters.mapNotNull { it.name }
    }
  }
}

private fun getParamStates(frame: KotlinStackFrame, variables: List<LocalVariableProxyImpl>): List<ParamState> {
  val vars = (variables.filterByPrefix(DIRTY_VAR).takeIf { it.isNotEmpty() } ?: variables.filterByPrefix(CHANGED_VAR))
  return ParamState.decode(vars.map { it.intValue(frame) })
}

private fun LocalVariableProxyImpl.intValue(frame: KotlinStackFrame) = (frame.stackFrameProxy.getValue(this) as IntegerValue).value()

private fun List<LocalVariableProxyImpl>.filterByPrefix(prefix: String) = filter { it.name().startsWith(prefix) }.sortedBy { it.name() }

private fun KotlinStackFrame.findComposer() = stackFrameProxy.visibleVariables().find { it.name() == COMPOSER_VAR }
