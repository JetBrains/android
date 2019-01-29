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
package org.jetbrains.android.facet

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

class AndroidFacetConfigurationTest : AndroidTestCase() {

  fun testChangingModelFiresEvent() {
    val module = myFixture.module
    val configuration = AndroidFacet.getInstance(module)!!.configuration
    val model = mock(AndroidModuleModel::class.java)

    val connection = module.messageBus.connect()

    var eventReceived = false

    connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerAdapter() {
      override fun facetConfigurationChanged(facet: Facet<*>) {
        eventReceived = true
      }
    })

    configuration.model = model
    connection.deliverImmediately()
    connection.disconnect()

    assertTrue("An event should have been fired", eventReceived)
  }

}