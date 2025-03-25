/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.settings

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap
import java.util.stream.Stream

class DefaultsDslElement(parent: GradleDslElement, name: GradleNameElement): GradleDslBlockElement(parent, name) {
  companion object {
    @JvmField
    val DEFAULTS_DSL_ELEMENT = PropertiesElementDescription("defaults",
                                                            DefaultsDslElement::class.java) {
      parent, name -> DefaultsDslElement(parent, name)
    }


    val CHILD_PROPERTIES_ELEMENT_MAP = Stream.of(*arrayOf(
      arrayOf("androidApp", AndroidDslElement.ANDROID_APP),
      arrayOf("androidLibrary", AndroidDslElement.ANDROID_LIBRARY),
    )).collect(ImmutableMap.toImmutableMap({ data: Array<*> -> data[0] as String },
                                           { data: Array<*> -> data[1] as PropertiesElementDescription<*> }))

  }
  override fun getChildPropertiesElementsDescriptionMap(
    kind: GradleDslNameConverter.Kind?
  ): ImmutableMap<String?, PropertiesElementDescription<*>?> {
    return CHILD_PROPERTIES_ELEMENT_MAP
  }
}

