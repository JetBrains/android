/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.connections

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorDataSource
import kotlinx.coroutines.runBlocking

/** A model that allows for the querying of [ConnectionData] based on time range. */
interface ConnectionDataModel {
  /**
   * This method will be invoked in each animation cycle of the timeline view.
   *
   * Returns a list of [ConnectionData] that fall within the [range].
   */
  fun getData(timeCurrentRangeUs: Range): List<ConnectionData>
}

class ConnectionDataModelImpl(private val dataSource: NetworkInspectorDataSource) :
  ConnectionDataModel {

  override fun getData(timeCurrentRangeUs: Range) = runBlocking {
    dataSource.queryForConnectionData(timeCurrentRangeUs).filter { events ->
      events.threads.isNotEmpty()
    }
  }
}
