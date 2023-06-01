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
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation

interface ParameterVisitor {
  fun visitName(old: String?, new: String?) {}
  fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {}

  /**
   * Visits the changed annotations of this parameter. Will not be called if no annotations were added, removed, or modified.
   * @param added the annotations that were added. Can be an empty list.
   * @param removed the annotations that were removed. Can be an empty list.
   * @param modified the diffs of annotations that were modified. Can be an empty list.
   */
  fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>)
}