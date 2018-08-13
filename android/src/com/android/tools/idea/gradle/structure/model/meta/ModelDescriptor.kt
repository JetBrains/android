/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModelDescriptor
import kotlin.reflect.KProperty

/**
 * A descriptor of an entity model in PSD for the purpose of property editing.
 *
 * @param ResolvedT the type of the objects representing a model resolved by Gradle
 * @param ParsedT the type of the model representing the parsed Gradle configuration
 */
interface ModelDescriptor<in ModelT, out ResolvedT, out ParsedT> {
  /**
   * Returns a resolved model.
   *
   * Returns null if the model has not yet been resolved or if the concept of resolved model does not apply to [ModelT].
   */
  fun getResolved(model: ModelT): ResolvedT?

  /**
   * Returns the model of a parsed configuration.
   *
   * Returns null if the configuration cannot be found in the build files.
   */
  fun getParsed(model: ModelT): ParsedT?
  /**
   * Notifies the PSD that a [model] has been modified and that the changes need to be saved.
   */
  fun setModified(model: ModelT)


  /**
   * Enumerates the models directly contained by [model].
   */
  fun enumerateModels(model: ModelT): Collection<PsModel> = listOf()


  /**
   * Returns the properties described by this descriptor.
   */
  val properties: Collection<ModelProperty<ModelT, *, *, *>> get() = listOf()
}


/**
 * A helper operator to implement models' descriptor property as: descriptor by Descriptor.
 */
operator fun <T : PsModel> ModelDescriptor<T, *, *>.getValue(model: T, property: KProperty<*>): PsModelDescriptor =
  object : PsModelDescriptor {
    override fun enumerateContainedModels(): Collection<PsModel> =
      enumerateModels(model).let { it + it.flatMap { contained -> contained.descriptor.enumerateContainedModels() } }

    override fun enumerateProperties(receiver: PsModelDescriptor.PropertyReceiver) {
      this@getValue.properties.forEach {
        receiver.receive(model, it)
      }
    }
  }
