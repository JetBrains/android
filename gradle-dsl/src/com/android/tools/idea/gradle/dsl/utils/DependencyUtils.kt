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
package com.android.tools.idea.gradle.dsl.utils

import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile

/**
 * Returns true if the given [element] is contained within a [GradleVersionCatalogFile].
 *
 * @param element The element to check.
 * @return True if the element is in a version catalog file, false otherwise.
 */
fun isInVersionCatalogFile(element: GradleDslElement): Boolean {
  return element.getDslFile() is GradleVersionCatalogFile
}

/**
 * Resolves the given [GradleDslElement] by following any references.
 *
 * @param element The element to resolve.
 * @return The resolved [GradleDslElement]. If the element is not a reference, the original element is returned.
 */
fun resolveElement(element: GradleDslElement): GradleDslElement {
  var resolved = element
  val foundElement = PropertyUtil.followElement(element)
  if (foundElement is GradleDslExpression) {
    resolved = foundElement
  }
  return resolved
}
