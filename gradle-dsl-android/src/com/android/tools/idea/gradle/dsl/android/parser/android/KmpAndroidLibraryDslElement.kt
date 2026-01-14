/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.parser.android

import com.android.tools.idea.gradle.dsl.android.model.android.AndroidModelImpl.COMPILE_SDK_VERSION
import com.android.tools.idea.gradle.dsl.android.model.android.AndroidModelImpl.COMPILE_SDK_EXTENSION
import com.android.tools.idea.gradle.dsl.android.model.android.AndroidModelImpl.COMPILE_SDK_MINOR
import com.android.tools.idea.gradle.dsl.android.model.android.AndroidModelImpl.NAMESPACE
import com.android.tools.idea.gradle.dsl.android.model.android.ProductFlavorModelImpl.MIN_SDK_VERSION
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly
import com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint
import com.google.common.collect.ImmutableMap
import java.util.stream.Stream

class KmpAndroidLibraryDslElement(parent: GradleDslElement, name: GradleNameElement) :
  GradleDslBlockElement(parent, name) {

  companion object {
    val ktsToModelNameMap: ExternalToModelMap = Stream.of(
      arrayOf("namespace", property, NAMESPACE, VAR),
      arrayOf("minSdk", property, MIN_SDK_VERSION, VAR),
      arrayOf("minSdkPreview", property, MIN_SDK_VERSION, VAR),
      arrayOf("compileSdk", property, COMPILE_SDK_VERSION, VAR),
      arrayOf("compileSdkExtension", property, COMPILE_SDK_EXTENSION, VAR),
      arrayOf("compileSdkMinor", property, COMPILE_SDK_MINOR, VAR),
      arrayOf("compileSdkPreview", property, COMPILE_SDK_VERSION, VAR),
      ).collect(ModelMapCollector.toModelMap())

    val KMP_ANDROID_LIBRARY: PropertiesElementDescription<KmpAndroidLibraryDslElement> =
      PropertiesElementDescription<KmpAndroidLibraryDslElement>(
        "androidLibrary", KmpAndroidLibraryDslElement::class.java
      ) { parent, name ->
        KmpAndroidLibraryDslElement(parent, name)
      }
  }

  override fun getChildPropertiesElementsDescriptionMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> {
    return ImmutableMap.of("compileSdk", CompileSdkBlockDslElement.COMPILE_SDK)
  }

  override fun getExternalToModelMap(converter: GradleDslNameConverter): ExternalToModelMap {
    return getExternalToModelMap(
      converter, ExternalToModelMap.empty, ktsToModelNameMap)
  }
}