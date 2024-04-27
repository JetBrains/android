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
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.EnclosingMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod

/**
 * Visitor to traverse a class diff. Can be passed to [ClassDiff.accept] to inspect the contents of the diff.
 */
interface ClassVisitor {
  fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {}
  fun visitSignature(old: String?, new: String?) {}
  fun visitSuperName(old: String?, new: String?) {}
  fun visitInterfaces(added: Set<String>, removed: Set<String>) {}
  fun visitVersion(old: Int, new: Int) {}
  fun visitEnclosingMethod(old: EnclosingMethod?, new: EnclosingMethod?) {}

  /**
   * Visits the changed methods of this class. Will not be called if no methods were added, removed, or modified.
   * @param added the methods that were added. Can be an empty list.
   * @param removed the methods that were removed. Can be an empty list.
   * @param modified the diffs of methods that were modified. Can be an empty list.
   */
  fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {}

  /**
   * Visits the changed fields of this class. Will not be called if no fields were added, removed, or modified.
   * @param added the fields that were added. Can be an empty list.
   * @param removed the fields that were removed. Can be an empty list.
   * @param modified the diffs of fields that were modified. Can be an empty list.
   */
  fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {}

  /**
   * Visits the changed annotations of this class. Will not be called if no annotations were added, removed, or modified.
   * @param added the annotations that were added. Can be an empty list.
   * @param removed the annotations that were removed. Can be an empty list.
   * @param modified the diffs of annotations that were modified. Can be an empty list.
   */
  fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {}
}