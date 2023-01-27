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

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.xdebugger.frame.XNamedValue

/**
 * Represents an object whose state is reported in the `$changed/$dirty` state vector.
 *
 * There are 2 types of objects, parameters (including an extension receiver) and a `this` pointer. A [StateObject] can be converted into an
 * [XNamedValue] which can then be added to the [ComposeStateNode].
 */
internal sealed class StateObject(val state: ParamState, val name: String) {
  abstract fun toXValue(context: EvaluationContextImpl): XNamedValue

  class Parameter(state: ParamState, name: String, val param: LocalVariableProxyImpl?) : StateObject(state, name) {
    override fun toXValue(context: EvaluationContextImpl): XNamedValue = ParameterNode(context, name, param, state)
  }

  class ThisObject(state: ParamState) : StateObject(state, "this") {
    override fun toXValue(context: EvaluationContextImpl): XNamedValue = ThisObjectNode(context, state)
  }
}
