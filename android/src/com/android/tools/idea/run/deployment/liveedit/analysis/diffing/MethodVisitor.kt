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
package com.android.tools.idea.run.deployment.liveedit.analysis.diffing

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstructionList
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrParameter
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrTryCatchBlock

interface MethodVisitor {
  fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {}
  fun visitSignature(old: String?, new: String?) {}
  fun visitInstructions(old: IrInstructionList, new: IrInstructionList) {}

  /**
   * Visits the changed parameters of this method. Will not be called if no parameters were added, removed, or modified.
   * @param added the parameters that were added. Can be an empty list.
   * @param removed the parameters that were removed. Can be an empty list.
   * @param modified the diffs of parameters that were modified. Can be an empty list.
   */
  fun visitParameters(added: List<IrParameter>, removed: List<IrParameter>, modified: List<ParameterDiff>) {}

  /**
   * Visits the changed annotations of this method. Will not be called if no annotations were added, removed, or modified.
   * @param added the annotations that were added. Can be an empty list.
   * @param removed the annotations that were removed. Can be an empty list.
   * @param modified the diffs of annotations that were modified. Can be an empty list.
   */
  fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {}

  /**
   * Visits the changed local variables of this method. Will not be called if no local variables were added, removed, or modified.
   * @param added the local variables that were added. Can be an empty list.
   * @param removed the local variables that were removed. Can be an empty list.
   * @param modified the diffs of local variables that were modified. Can be an empty list.
   */
  fun visitLocalVariables(added: List<IrLocalVariable>, removed: List<IrLocalVariable>, modified: List<LocalVariableDiff>) {}

  /**
   * Visits the changed try/catch blocks of this method. Will not be called if no local variables were added, removed, or modified.
   * @param added the local variables that were added. Can be an empty list.
   * @param removed the local variables that were removed. Can be an empty list.
   * @param modified the diffs of local variables that were modified. Can be an empty list.
   */
  fun visitTryCatchBlocks(added: List<IrTryCatchBlock>, removed: List<IrTryCatchBlock>, modified: List<TryCatchBlockDiff>) {}
}