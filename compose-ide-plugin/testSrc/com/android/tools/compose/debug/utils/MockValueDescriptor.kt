/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.debug.utils

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.sun.jdi.Type
import com.sun.jdi.Value

class MockValueDescriptor(project: Project, value: Value) : ValueDescriptorImpl(project, value) {
  var myValueText: String = value.toString()

  override fun getType(): Type = value.type()

  override fun getDescriptorEvaluation(context: DebuggerContext?): PsiExpression? = null

  override fun calcValue(evaluationContext: EvaluationContextImpl?): Value = value

  override fun getValueText(): String = myValueText

  override fun setValueLabel(label: String) {
    myValueText = label
  }
}
