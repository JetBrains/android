/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ParameterDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstructionList
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrParameter

class SyntheticClassVisitor(val className: String) : ClassVisitor {
  var requiresReinit = false
    private set
  var modifiedMethods: List<MethodDiff> = emptyList()
    private set

  override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
    // This will catch changes to constructor signatures due to capture changes and invoke() calls due to interface changes
    if (added.isNotEmpty() || removed.isNotEmpty()) {
      requiresReinit = true
    }
    val methods = mutableListOf<MethodDiff>()
    for (method in modified) {
      val visitor = SupportMethodVisitor()
      method.accept(visitor)
      if (visitor.hasNonSourceInfoChanges) {
        methods.add(method)
      }
    }
    modifiedMethods = methods
  }
}

private class SupportMethodVisitor : MethodVisitor {
  var hasNonSourceInfoChanges: Boolean = false
    private set

  override fun visitInstructions(old: IrInstructionList, new: IrInstructionList) {
    // Filter out the debugging info that compose modifies on every change
    hasNonSourceInfoChanges = !onlyComposeDebugConstantChanges(old, new)
  }
}