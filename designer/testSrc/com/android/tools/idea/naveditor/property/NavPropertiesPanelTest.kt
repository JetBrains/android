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
package com.android.tools.idea.naveditor.property

import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.inspector.NavInspectorProviders
import com.android.tools.idea.res.ResourceNotificationManager
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

class NavPropertiesPanelTest : NavTestCase() {
  @Suppress("UNCHECKED_CAST")
  fun testRefresh() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
      }
    }

    val navPropertiesManager = NavPropertiesManager(myFacet, model.surface)
    Disposer.register(myRootDisposable, navPropertiesManager)
    val inspectorProviders = mock(NavInspectorProviders::class.java)
    val inspector1 = mock(InspectorComponent::class.java) as InspectorComponent<NavPropertiesManager>
    val inspector2 = mock(InspectorComponent::class.java) as InspectorComponent<NavPropertiesManager>
    `when`(inspectorProviders.createInspectorComponents(any(), any(), any())).thenReturn(listOf(inspector1, inspector2))
    val selectedItems = listOf(model.find("f1")!!)
    navPropertiesManager.myProviders = inspectorProviders;
    navPropertiesManager.propertiesPanel.setItems(selectedItems, HashBasedTable.create())

    model.resourcesChanged(setOf(ResourceNotificationManager.Reason.EDIT))
    UIUtil.dispatchAllInvocationEvents()

    verify(inspector1).refresh()
    verify(inspector1).refresh()
  }
}