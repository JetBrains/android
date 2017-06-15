/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.experimental.callgraph

import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastBinaryOperator

// Helper functions for destructuring UAST nodes.
// E.g., `val (left, op, right) = binaryExpression`

operator fun UBinaryExpression.component1(): UExpression = this.leftOperand
operator fun UBinaryExpression.component2(): UastBinaryOperator = this.operator
operator fun UBinaryExpression.component3(): UExpression = this.rightOperand
