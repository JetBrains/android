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
package com.android.tools.idea.resourceExplorer.model

import javax.swing.ListModel
import javax.swing.event.EventListenerList
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * ListModel used to store the [DesignAsset] displayed in the resource explorer
 */
class DesignAssetListModel : ListModel<DesignAssetSet> {

  private var designAssets: List<DesignAssetSet> = listOf()
  private var dataListeners = EventListenerList()

  override fun removeListDataListener(listener: ListDataListener) {
    dataListeners.remove(ListDataListener::class.java, listener)
  }

  override fun getElementAt(index: Int) = if (index >= 0 && index < designAssets.size) designAssets[index] else null

  override fun getSize() = designAssets.size

  override fun addListDataListener(listener: ListDataListener) {
    removeListDataListener(listener) // Ensure we are not adding the same listener twice
    dataListeners.add(ListDataListener::class.java, listener)
  }

  fun setAssets(assets: Collection<DesignAssetSet>) {
    designAssets = assets.toList()
    fireListContentChangedEvent()
  }

  private fun fireListContentChangedEvent() {
    val dataEvent = ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, designAssets.size)
    // Guaranteed to return a non-null array
    val listeners = dataListeners.listenerList
    // Process the listeners last to first, notifying
    // those that are interested in this event
    listeners.
        filterIsInstance(ListDataListener::class.java)
        .forEach { listener: ListDataListener? -> listener?.contentsChanged(dataEvent) }
  }

  fun refresh() {
    fireListContentChangedEvent()
  }
}