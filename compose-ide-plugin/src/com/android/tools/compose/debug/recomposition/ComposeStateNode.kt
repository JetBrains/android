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
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import icons.StudioIcons
import kotlin.collections.Map.Entry

/**
 *  A [XNamedValue] representing the recomposition state of the enclosing Composable function/lambda
 *
 *  The node looks something like this:
 *
 *  ```
 *  + Recomposition State = Arguments: Different: [arg1] Same: [arg2, this]
 *    + arg1 = Different
 *      + value = <value of arg1>
 *    + arg2 = Same
 *      + value = <value of arg2>
 *    + this = Same
 *      + value = <value of this>
 *  ```
 *  Which means, for this recomposition, the value `arg1` has changed from the last time the Composable was composed. `arg2` & `this` have
 *  not changed.
 */
internal class ComposeStateNode(
  private val evaluationContext: EvaluationContextImpl,
  private val forced: Boolean,
  private val stateObjects: List<StateObject>,
) : XNamedValue(ComposeBundle.message("recomposition.state.label")) {
  // Create the child nodes early because we are known to be on the correct thread.
  private val childNodes = stateObjects.map { it.toXValue(evaluationContext) }

  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    val summary = when {
      forced -> ComposeBundle.message("recomposition.state.forced")
      stateObjects.isEmpty() -> ComposeBundle.message("recomposition.state.composing")
      else -> getStateSummary()
    }
    node.setPresentation(StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION, null, summary, true)
  }

  override fun computeChildren(node: XCompositeNode) {
    val children = XValueChildrenList(childNodes.size)
    childNodes.forEach { children.add(it) }
    node.addChildren(children, true)
  }

  private fun getStateSummary(): String {
    fun Entry<ParamState, List<StateObject>>.toSummary() = "${key.name}: [${value.joinToString { it.name }}]"

    val summary = stateObjects.groupBy { it.state }.entries.joinToString { it.toSummary() }
    return ComposeBundle.message("recomposition.state.summary", summary)
  }
}
