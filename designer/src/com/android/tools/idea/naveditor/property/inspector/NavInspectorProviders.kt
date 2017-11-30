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
package com.android.tools.idea.naveditor.property.inspector

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.common.property.inspector.InspectorProviders
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.intellij.openapi.Disposable

/**
 * Creates the [InspectorProvider]s for navigation editor elements.
 */
// Open for mocking in tests only
open class NavInspectorProviders(propertiesManager: NavPropertiesManager, parentDisposable: Disposable) : InspectorProviders<NavPropertiesManager>(propertiesManager, parentDisposable) {
  private val myProviders: List<InspectorProvider<NavPropertiesManager>>
  private val myNullProvider: InspectorProvider<NavPropertiesManager>

  init {
    val provider = NavMainPropertiesInspectorProvider()
    myNullProvider = provider
    myProviders = listOf(provider,
        NavActivityPropertiesInspectorProvider(),
        NavSetStartProvider(),
        NavDestinationArgumentsInspectorProvider(),
        NavActionArgumentsInspectorProvider(),
        NavActionPopInspectorProvider(),
        NavActionLaunchOptionsInspectorProvider(),
        NavigationActionsInspectorProvider(),
        NavigationDeeplinkInspectorProvider())
  }

  @VisibleForTesting
  public override fun getProviders(): List<InspectorProvider<NavPropertiesManager>> {
    return myProviders
  }

  override fun getNullProvider(): InspectorProvider<NavPropertiesManager> {
    return myNullProvider
  }
}
