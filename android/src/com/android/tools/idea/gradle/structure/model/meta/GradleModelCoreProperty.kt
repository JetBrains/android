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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel

/**
 * A core property bound to a Gradle model property.
 */
interface GradleModelCoreProperty<PropertyT : Any, out ModelPropertyCoreT : ModelPropertyCore<PropertyT>> {
  /**
   * Returns a new core property bound to the given [resolvedProperty]. The receiver property is used as a prototype for the binding
   * configuration.
   */
  fun rebind(resolvedProperty: ResolvedPropertyModel, modifiedSetter: () -> Unit): ModelPropertyCoreT

  fun getParsedProperty(): ResolvedPropertyModel?
}