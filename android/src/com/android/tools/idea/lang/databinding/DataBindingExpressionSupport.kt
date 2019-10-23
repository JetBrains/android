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
@file:JvmName("DataBindingExpressionUtil")

package com.android.tools.idea.lang.databinding

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.xml.XmlAttribute

/**
 * Extension point which providing methods related to extracting information from data binding
 * expressions.
 */
interface DataBindingExpressionSupport {
  /**
   * A data binding expression can optionally provide a default value, e.g. `@{..., default = xyz}`
   * Given an entire expression (including the surrounding `"@{}"`), return the default value, if
   * any was specified, or `null` otherwise.
   */
  fun getBindingExprDefault(expr: String): String?

  /**
   * Similar to the [getBindingExprDefault] which takes a [String] parameter, but which instead
   * receives the a PSI [XmlAttribute] that should be assigned to a data binding expression. If
   * no default value was specified, or if no expression is assigned to this attribute, `null`
   * is returned.
   */
  fun getBindingExprDefault(psiAttribute: XmlAttribute): String?
}

/**
 * Returns null if no extension point implementation is found.
 */
fun getDataBindingExpressionSupport(): DataBindingExpressionSupport? {
  val extensionPoint: ExtensionPointName<DataBindingExpressionSupport> = ExtensionPointName(
    "com.android.tools.idea.lang.databinding.dataBindingExpressionSupport")
  return extensionPoint.extensionList.firstOrNull()
}