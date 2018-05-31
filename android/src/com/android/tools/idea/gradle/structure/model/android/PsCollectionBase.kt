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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

abstract class PsCollectionBase<TModel : PsModel, TKey, TParent: PsModel>(val parent: TParent) : PsModelCollection<TModel> {
  abstract fun getKeys(from: TParent): Set<TKey>
  abstract fun create(key: TKey): TModel
  abstract fun update(key: TKey, model: TModel)

  protected val container: MutableMap<TKey, TModel> = mutableMapOf()
  val entries: Map<TKey, TModel> get() = container

  init {
    @Suppress("LeakingThis")
    getKeys(parent)
      .map{ key -> key to create(key).also { update(key, it) }}
      .toMap(container)
  }

  override fun forEach(consumer: Consumer<TModel>) = container.values.forEach(consumer)

  override fun forEach(consumer: (TModel) -> Unit) = container.values.forEach(consumer)

  override fun items(): List<TModel> = container.values.toList()

  fun findElement(key: TKey): TModel? = container[key]
}

abstract class PsMutableCollectionBase<TModel : PsModel, TKey, TParent : PsModel>(parent: TParent)
  : PsCollectionBase<TModel, TKey, TParent>(parent) {

  abstract fun instantiateNew(key: TKey)
  abstract fun removeExisting(key: TKey)

  fun addNew(key: TKey): TModel {
    if (container.containsKey(key)) throw IllegalArgumentException("Duplicate key: $key")
    instantiateNew(key)
    val model = create(key).also { update(key, it) }
    container[key] = model
    parent.isModified = true
    return model
  }

  fun remove(key: TKey) {
    if (!container.containsKey(key)) throw IllegalArgumentException("Key not found: $key")
    removeExisting(key)
    container.remove(key)
    parent.isModified = true
  }

  // TODO(solodkyy): support renames
}