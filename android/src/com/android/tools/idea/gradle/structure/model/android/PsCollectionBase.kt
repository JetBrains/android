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
  protected abstract fun getKeys(from: TParent): Set<TKey>
  protected abstract fun create(key: TKey): TModel
  protected abstract fun update(key: TKey, model: TModel)

  var entries: Map<TKey, TModel> ; protected set

  init {
    @Suppress("LeakingThis")
    entries = getKeys(parent)
      .map{ key -> key to create(key).also { update(key, it) }}
      .toMap()
  }

  override fun forEach(consumer: Consumer<TModel>) = entries.values.forEach(consumer)

  override fun forEach(consumer: (TModel) -> Unit) = entries.values.forEach(consumer)

  override fun items(): Collection<TModel> = entries.values

  fun findElement(key: TKey): TModel? = entries[key]

  fun refresh() {
    entries = getKeys(parent).map { key -> key to (entries[key] ?: create(key)).also { update(key, it) } }.toMap()
  }
}

abstract class PsMutableCollectionBase<TModel : PsModel, TKey, TParent : PsModel>(parent: TParent)
  : PsCollectionBase<TModel, TKey, TParent>(parent) {

  protected abstract fun instantiateNew(key: TKey)
  protected abstract fun removeExisting(key: TKey)

  fun addNew(key: TKey): TModel {
    if (entries.containsKey(key)) throw IllegalArgumentException("Duplicate key: $key")
    instantiateNew(key)
    val model = create(key).also { update(key, it) }
    entries += (key to model)
    parent.isModified = true
    return model
  }

  fun remove(key: TKey) {
    if (!entries.containsKey(key)) throw IllegalArgumentException("Key not found: $key")
    removeExisting(key)
    entries -= key
    parent.isModified = true
  }

  // TODO(b/111739005): support renames
}