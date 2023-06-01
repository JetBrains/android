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

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLabels

interface MethodDiff {
  /**
   * The unqualified method name.
   */
  val name: String

  /**
   * The method descriptor. Example: ()Ljava/lang/String;
   */
  val desc: String
  fun accept(visitor: MethodVisitor)
}

interface FieldDiff {
  /**
   * The unqualified field name.
   */
  val name: String
  fun accept(visitor: FieldVisitor)
}

interface ParameterDiff {
  /**
   * The parameter's position in the ordered list of method parameters.
   */
  val index: Int
  fun accept(visitor: ParameterVisitor)
}

interface TryCatchBlockDiff {
  /**
   * The instruction label indicating the start of the exception handler's scope.
   */
  val start: IrLabels.IrLabel

  /**
   * The instruction label indicating the end of the exception  handler's scope.
   */
  val end: IrLabels.IrLabel

  /**
   * The instruction label indicating the start of the exception handler's code.
   */
  val handler: IrLabels.IrLabel

  fun accept(visitor: TryCatchBlockVisitor)
}

interface LocalVariableDiff {
  /**
   * The position of the variable in the method's local variable table.
   */
  val index: Int
  fun accept(visitor: LocalVariableVisitor)
}

interface AnnotationDiff {
  /**
   * The type of the annotation. Example: Landroidx/compose/runtime/Composable;
   */
  val desc: String
  fun accept(visitor: AnnotationVisitor)
}
