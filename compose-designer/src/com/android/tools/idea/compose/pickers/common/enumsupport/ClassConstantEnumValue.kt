/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.enumsupport

import com.android.tools.property.panel.api.EnumValue

/**
 * [EnumValue] that sets the parameter value to a constant of an specific class. While importing the
 * needed class.
 *
 * E.g: For `MyClass.MY_CONSTANT`
 *
 * `import package.of.MyClass`
 *
 * `parameterName = MyClass.MY_CONSTANT`
 */
internal interface ClassConstantEnumValue : BaseClassEnumValue {
  val classConstant: String

  private val className: String
    get() = fqClass.substringAfterLast('.', fqClass)

  override val valueToWrite: String
    get() = "$className.$classConstant"

  override val fqFallbackValue: String
    get() = "$fqClass.$classConstant"
}
