/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import javax.swing.Icon

interface PsModel {
  /**
   * A model descriptor to inspect the content of the model in a generic manner.
   */
  val descriptor: PsModelDescriptor get() = PsModelDescriptor.None

  val parent: PsModel?

  var isModified: Boolean

  val name: String

  val isDeclared: Boolean

  val icon: Icon? get() = null

  val path: PsPath? get() = null
}

interface PsModelDescriptor {
  /**
   * Returns a collection of models contained in the given instance (including transitively-contained ones).
   */
  fun enumerateContainedModels(): Collection<PsModel> = listOf()

  /**
   * Enumerates properties defined on the given instance in a type-safe manner.
   */
  fun enumerateProperties(receiver: PropertyReceiver) = Unit

  object None : PsModelDescriptor

  @FunctionalInterface
  interface PropertyReceiver {
    fun <T: PsModel> receive(model: T, property: ModelProperty<T, *, *, *>)
  }
}