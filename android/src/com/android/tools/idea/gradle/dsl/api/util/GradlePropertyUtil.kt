/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.util

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*

/**
 * Replaces an [old] value with a [new] one in a list property. This method will only replace the first occurrence of
 * the [old] element and will do nothing if the [old] element does not exist.
 * This method can ONLY be called on a property of type [GradlePropertyModel.ValueType.LIST]
 */
fun GradlePropertyModel.replaceListValue(old: Any, new: Any) {
  (getValue(LIST_TYPE) ?: throw IllegalStateException("replaceListValue called an type: ${valueType}, must be LIST!"))
      .find { it.getValue(OBJECT_TYPE) == old }?.setValue(new)
}