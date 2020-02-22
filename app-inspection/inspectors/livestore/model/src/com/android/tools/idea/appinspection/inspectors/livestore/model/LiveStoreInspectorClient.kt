/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.livestore.model

import com.android.tools.idea.concurrency.transform
import com.android.tools.appinspection.livestore.protocol.LiveStoreCommand
import com.android.tools.appinspection.livestore.protocol.LiveStoreCommand.FetchAll
import com.android.tools.appinspection.livestore.protocol.LiveStoreDefinition
import com.android.tools.appinspection.livestore.protocol.LiveStoreEvent
import com.android.tools.appinspection.livestore.protocol.LiveStoreResponse
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.google.gson.Gson

class LiveStoreInspectorClient(messenger: CommandMessenger) : AppInspectorClient(messenger) {
  private val gson = Gson()

  private val _enumDefinitions = mutableMapOf<String, List<String>>()
  val enumDefinitions: Map<String, List<String>> get() = _enumDefinitions

  private val _stores = mutableListOf<LiveStoreDefinition>()
  val stores: List<LiveStoreDefinition> get() = _stores

  private val _storesChangedListeners = mutableListOf<() -> Unit>()
  fun addStoresChangedListener(listener: () -> Unit) = _storesChangedListeners.add(listener)

  private val _valueChangedListeners = mutableListOf<(ValueChangedEventArgs) -> Unit>()
  fun addValueChangedListener(listener: (ValueChangedEventArgs) -> Unit) = _valueChangedListeners.add(listener)

  init {
    val command = LiveStoreCommand(fetchAll = FetchAll())
    messenger.sendRawCommand(gson.toJson(command).toByteArray()).transform { response -> handleFetchAllResponse(response) }
  }

  override val eventListener = object : EventListener {
    override fun onRawEvent(eventData: ByteArray) {
      val event = gson.fromJson(String(eventData), LiveStoreEvent::class.java)!!
      event.valueUpdated?.let { valueUpdatedEvent ->
        handleValueChanged(valueUpdatedEvent.store, valueUpdatedEvent.key, valueUpdatedEvent.value)
      }
    }

    override fun onDispose() {
      _storesChangedListeners.clear()
      _valueChangedListeners.clear()
    }
  }

  /**
   * Make a request to the device to update a key/value pair on some target livestore.
   *
   * This request is asynchronous. If successful, listeners which subscribed to
   * [addValueChangedListener] will be notified.
   */
  fun requestValueUpdate(storeName: String, keyName: String, newValue: String) {
    val command = LiveStoreCommand(updateValue = LiveStoreCommand.UpdateValue(storeName, keyName, newValue))
    messenger.sendRawCommand(gson.toJson(command).toByteArray()).transform { response -> handleUpdateValueResponse(response, newValue) }
  }

  private fun handleFetchAllResponse(responseBytes: ByteArray) {
    val response: LiveStoreResponse = gson.fromJson(String(responseBytes), LiveStoreResponse::class.java)
    response.fetchAll?.let { fetchAllResponse ->
      fetchAllResponse.enums.forEach { _enumDefinitions[it.fqcn] = it.values }
      _stores.addAll(fetchAllResponse.stores)
      _storesChangedListeners.forEach { listener -> listener() }
    }
  }

  private fun handleUpdateValueResponse(responseBytes: ByteArray, newValue: String) {
    val response: LiveStoreResponse = gson.fromJson(String(responseBytes), LiveStoreResponse::class.java)
    response.updateValue?.let { updateValueResponse ->
      if (updateValueResponse.succeeded) {
        // Only update our model after the device confirmed it was set
        handleValueChanged(updateValueResponse.store, updateValueResponse.key, newValue)
      }
    }
  }

  private fun handleValueChanged(storeName: String, keyName: String, value: String) {
    val storeDefinition = stores.firstOrNull { it.name == storeName } ?: return
    val keyDefinition = storeDefinition.keyValues.firstOrNull { it.name == keyName } ?: return
    val valueDefinition = keyDefinition.value

    // Only update our model after the device confirmed it was set
    valueDefinition.value = value
    val args = ValueChangedEventArgs(storeName, keyName, value)
    _valueChangedListeners.forEach { listener -> listener(args) }
  }
}