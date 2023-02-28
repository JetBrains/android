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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.intellij.conversion.ConversionContext
import com.intellij.conversion.ConverterProvider

class RulesPersistentStateConverterProvider : ConverterProvider() {
  override fun getConversionDescription(): String {
    return "Erase NetworkInspectorRules persisted in Flamingo Canary 6/7. Those rules did not support " +
      "being persisted across studio restart. They were stored as empty strings which were " +
      "required a Converter to convert them."
  }

  override fun createConverter(context: ConversionContext) = RulesPersistentStateConverter(context)
}
