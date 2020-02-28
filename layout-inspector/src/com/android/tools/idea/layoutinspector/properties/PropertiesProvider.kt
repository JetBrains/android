/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future

/**
 * A [PropertiesProvider] provides properties to registered listeners..
 */
interface PropertiesProvider {

  /**
   * Listeners for [PropertiesProvider] results.
   */
  val resultListeners: MutableList<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>

  /**
   * Requests properties for the specified [view].
   *
   * This is potentially an asynchronous request. The associated [InspectorPropertiesModel]
   * is notified when the table is ready.
   */
  fun requestProperties(view: ViewNode): Future<*>
}

object EmptyPropertiesProvider : PropertiesProvider {

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    return Futures.immediateFuture(null)
  }
}
