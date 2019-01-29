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
package com.android.tools.idea.ui.resourcechooser

import com.android.resources.ResourceType
import junit.framework.TestCase

class ResourceChooserItemTest : TestCase() {

  fun testGetResourceUrl() {
    // Test project resource
    val projectItem = ResourceChooserItem.createProjectItem(ResourceType.LAYOUT, "my_project_layout.xml", emptyList())
    assertEquals("@layout/my_project_layout.xml", projectItem.resourceUrl)

    // Test framework resource
    val frameworkItem = ResourceChooserItem.createFrameworkItem(ResourceType.LAYOUT, "my_framework_layout.xml", emptyList())
    assertEquals("@android:layout/my_framework_layout.xml", frameworkItem.resourceUrl)
  }
}