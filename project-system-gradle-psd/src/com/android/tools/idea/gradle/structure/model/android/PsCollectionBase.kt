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

import com.android.tools.idea.gradle.structure.model.ChangeDispatcher
import com.android.tools.idea.gradle.structure.model.PsKeyedModelCollection
import com.android.tools.idea.gradle.structure.model.PsModel
import com.intellij.openapi.Disposable

abstract class PsCollectionBase<TModel , TKey, TParent>
protected constructor(val parent: TParent) :
  PsKeyedModelCollection<TKey, TModel> {
  private val changedDispatcher = ChangeDispatcher()
  private var batchChangeLevel = 0
  private var batchHasPendingChangeNotifications = false

  protected abstract fun getKeys(from: TParent): Set<TKey>
  protected abstract fun create(key: TKey): TModel
  protected abstract fun update(key: TKey, model: TModel)

  override var entries: Map<TKey, TModel> = mapOf(); protected set

  override fun forEach(consumer: (TModel) -> Unit) = entries.values.forEach(consumer)

  override val items: Collection<TModel> get() = entries.values

  override fun findElement(key: TKey): TModel? = entries[key]

  fun refresh() {
    entries = getKeys(parent).map { key -> key to (entries[key] ?: create(key)) }.toMap()
    entries.forEach { (key, value) -> update(key, value) }
    notifyChanged()
  }

  override fun onChange(disposable: Disposable, listener: () -> Unit) = changedDispatcher.add(disposable, listener)

  protected fun notifyChanged() {
    if (batchChangeLevel == 0) changedDispatcher.changed() else batchHasPendingChangeNotifications = true
  }

  protected fun <T> batchChange(block: () -> T): T {
    beginChange()
    try {
      return block()
    }
    finally {
      endChange()
    }
  }

  private fun beginChange() {
    batchChangeLevel++
  }

  private fun endChange() {
    batchChangeLevel--
    if (batchChangeLevel == 0){
      if (batchHasPendingChangeNotifications) {
        batchHasPendingChangeNotifications = false
        notifyChanged()
      }
    }
  }
}

abstract class PsMutableCollectionBase<TModel : PsModel, TKey, TParent : PsModel> protected constructor(parent: TParent)
  : PsCollectionBase<TModel, TKey, TParent>(parent) {
  // return escaped key
  protected abstract fun instantiateNew(key: TKey)
  protected abstract fun removeExisting(key: TKey)

  protected open fun checkIfCanAddNew(key: TKey):String? =
    if (entries.containsKey(key))  ("Duplicate key: $key")
  else
    null

  fun addNew(key: TKey): TModel {
    val message = checkIfCanAddNew(key)
    if (message != null) throw IllegalArgumentException(message)

    instantiateNew(key)
    val model = create(key).also { update(key, it) }
    entries = entries + (key to model)
    parent.isModified = true
    notifyChanged()
    return model
  }

  fun remove(key: TKey) {
    if (!entries.containsKey(key)) throw IllegalArgumentException("Key not found: $key")
    removeExisting(key)
    entries = entries - key
    parent.isModified = true
    notifyChanged()
  }

  protected fun renamed(model: TModel, newKey: TKey) {
    entries = entries.entries.map { (k, v) -> if (v === model) newKey to v else k to v}.toMap()
    update(newKey, model)
    parent.isModified = true
    notifyChanged()
  }
}
