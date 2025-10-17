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

import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.AssetsModel
import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.TargetModel
import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.TestSuiteModel
import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.UseJunitEngineModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.AssetsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.TargetDslElement
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.TargetsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.TestSuiteDslElement
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.UseJunitEngineDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType
import com.google.common.collect.ImmutableList

class TestSuiteModelImpl(dslElement: TestSuiteDslElement) : GradleDslBlockModel(dslElement), TestSuiteModel {

  override fun name(): String {
    return myDslElement.name
  }

  override fun useJunitEngine(): UseJunitEngineModel {
    val useJunitEngineDslElement = myDslElement.ensurePropertyElement(UseJunitEngineDslElement.USE_JUNIT_ENGINE)
    return UseJunitEngineModelImpl(useJunitEngineDslElement)
  }

  override fun targetVariants(): ResolvedPropertyModel {
    return getModelForProperty(TARGET_VARIANTS)
  }

  override fun addTargetVariant(targetVariant: String): ResolvedPropertyModel {
    val model = targetVariants()

    val existingList = model.toList()?.mapNotNull { it.valueAsString() } ?: emptyList()
    if (!existingList.contains(targetVariant)) {
      val newVariant = model.addListValue()!!
      newVariant.setValue(targetVariant)
    }

    return model
  }

  override fun assets(): AssetsModel {
    val assetsDslElement = myDslElement.ensurePropertyElement(AssetsDslElement.ASSETS)
    return AssetsModelImpl(assetsDslElement)
  }

  override fun addAssets(): AssetsModel {
    val assetsDslElement = myDslElement.ensurePropertyElement(AssetsDslElement.ASSETS)
    return AssetsModelImpl(assetsDslElement)
  }

  override fun targets(): List<TargetModel> {
    val targets = myDslElement.getPropertyElement(TargetsDslElement.TARGETS)
    return targets?.get() ?: ImmutableList.of()
  }

  override fun addTarget(targetName: String): TargetModel {
    val targetsDslElement = myDslElement.ensurePropertyElement(TargetsDslElement.TARGETS)
    val targetElement =
      targetsDslElement.ensureNamedPropertyElement(
        TargetDslElement.TARGET,
        GradleNameElement.create(targetName)
      )

    return TargetModelImpl(targetElement)
  }


  companion object {
    @JvmStatic
    val TARGET_VARIANTS: ModelPropertyDescription = ModelPropertyDescription("mTargetVariants", ModelPropertyType.MUTABLE_LIST)
  }
}