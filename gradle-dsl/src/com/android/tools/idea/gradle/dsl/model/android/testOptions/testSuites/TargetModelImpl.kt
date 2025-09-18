/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android.testOptions.testSuites

import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.TargetModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.android.testOptions.testSuites.TestSuiteModelImpl.Companion.TARGET_VARIANTS
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.TargetDslElement

class TargetModelImpl(dslElement: TargetDslElement) : GradleDslBlockModel(dslElement), TargetModel {
  override fun name(): String {
    return myDslElement.name
  }

  override fun addVariant(variant: String): ResolvedPropertyModel {
    val model = getModelForProperty(TARGET_VARIANTS)
    val newVariant = model.addListValue()!!
    newVariant.setValue(variant)
    return model
  }
}
