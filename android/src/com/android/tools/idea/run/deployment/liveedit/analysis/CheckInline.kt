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

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import org.jetbrains.kotlin.load.java.JvmAbi

/**
 * Checks if a method is an inline method.
 *
 * The kotlin compiler adds a synthetic variable to every inline method, consisting of a prefix followed by the method's name. The variable
 * will be scoped to the entire body of the method, from the second label to the last label in the method. The scope must be checked
 * because a similarly named variable may be present if the method calls another inline method with the same name.
 */
fun IrMethod.isInline(): Boolean {
  val inlineName = "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION}$name"
  return localVariables.filter { it.name == inlineName }.any { it.start.index <= 1 && it.end.index == instructions.labels.size - 1 }
}