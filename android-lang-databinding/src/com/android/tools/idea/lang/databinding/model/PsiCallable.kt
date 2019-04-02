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

import android.databinding.tool.BindableCompat


/**
 * For Callable that could be either Method or Field.
 * see [android.databinding.tool.reflection.Callable]
 */
class PsiCallable(val type: Type, val name: String, val setterName: String?, private val resolvedType: PsiModelClass?,
                  val parameterCount: Int, private val mFlags: Int, val method: PsiModelMethod?, val bindable: BindableCompat?) {

  val typeCodeName: String?
    get() = resolvedType?.toJavaCode()

  val isDynamic: Boolean
    get() = mFlags and DYNAMIC != 0

  val isStatic: Boolean
    get() = mFlags and STATIC != 0

  val minApi: Int
    get() = 1

  enum class Type {
    METHOD,
    FIELD
  }

  private fun canBeInvalidated(): Boolean {
    return mFlags and CAN_BE_INVALIDATED != 0
  }

  override fun toString(): String {
    return "Callable{" +
           "type=" + type +
           ", name='" + name + '\''.toString() +
           ", resolvedType=" + resolvedType +
           ", isDynamic=" + isDynamic +
           ", canBeInvalidated=" + canBeInvalidated() +
           ", static=" + isStatic +
           ", method=" + method +
           '}'.toString()
  }

  companion object {
    val DYNAMIC = 1
    val CAN_BE_INVALIDATED = 1 shl 1
    val STATIC = 1 shl 2
  }
}
