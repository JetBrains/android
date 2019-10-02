/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.model

import com.android.tools.idea.lang.databinding.psi.PsiDbExpr

/**
 * An interface for references that can be resolved to a model class (a data class that can be
 * bound to and referenced by data binding expressions).
 *
 * The meaning for how a reference resolves depends on the reference type. For example, a reference
 * to a class in a databinding expression will resolve to the class directly, while a field will
 * resolve to its type, and a method will resolve to its return type, etc.
 */
interface ModelClassResolvable {
  val resolvedType: PsiModelClass?
  // TODO (b/141383218): Revisit isStatic field in ModelClassResolvable.
  val isStatic: Boolean
}

/**
 * Check if a generic data binding expression can be converted to a [ModelClassResolvable], or
 * `null` if
 */
fun PsiDbExpr.toModelClassResolvable(): ModelClassResolvable? {
  return references.filterIsInstance<ModelClassResolvable>().firstOrNull()
}
