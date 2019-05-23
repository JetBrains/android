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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.tools.idea.templates.TemplateMetadata.*
import junit.framework.TestCase
import kotlin.test.assertFailsWith

class NewProjectExtraInfoTest : TestCase() {
  @Throws(Exception::class)
  fun testFullConstruction() {
    NewProjectExtraInfoBuilder(10, 11, "com.google.test", "location", "/sdk", "someActivity", "project", "app").build()
  }

  @Throws(Exception::class)
  fun testIncompleteConstruction() {
    assertFailsWith(IllegalStateException::class) {
      NewProjectExtraInfoBuilder(10, 11, "com.google.incomplete").build()
    }
  }

  @Throws(Exception::class)
  fun testFill() {
    val projectInfoBuilder = NewProjectExtraInfoBuilder(10)
    val values1 = mapOf(ATTR_TARGET_API to 25, ATTR_PACKAGE_NAME to "com.google.test", ATTR_TOP_OUT to "location")
    projectInfoBuilder.fill(values1)
    assertFailsWith(IllegalStateException::class) {
      projectInfoBuilder.build()
    }
    val values2 = mapOf(ATTR_SDK_DIR to "/sdk", ACTIVITY_TEMPLATE_NAME to "someActivity", ATTR_TOP_OUT to "/out/dir/", MOBILE_PROJECT_NAME to "app")
    projectInfoBuilder.fill(values2)
    projectInfoBuilder.build()
  }
}
