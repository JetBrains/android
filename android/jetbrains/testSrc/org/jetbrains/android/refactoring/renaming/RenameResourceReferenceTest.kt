/*
 * Copyright (C) 2022 The Android Open Source Project
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
package org.jetbrains.android.refactoring.renaming

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.resources.ResourceUrl
import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RenameResourceReferenceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
  }

  @Test
  fun isFileBased_simpleFileBased() {
    projectRule.fixture.copyFileToProject("resourceRepository/layout_ids1.xml", "res/layout-land/layout_ids1.xml")
    projectRule.fixture.addFileToProject("res/anim/some_anim.xml", "<layoutAnimation/>")

    assertResourceIsFileBased("@layout/layout_ids1", expectedIsFileBased = true)
    assertResourceIsFileBased("@anim/some_anim", expectedIsFileBased = true)
  }

  @Test
  fun isFileBased_simpleNotFileBased() {
    projectRule.fixture.copyFileToProject("resourceRepository/strings.xml", "res/values/strings.xml")
    projectRule.fixture.copyFileToProject("dom/res/values/dimens.xml", "res/values/dimens.xml")
    projectRule.fixture.copyFileToProject("resourceRepository/layout_ids1.xml", "res/layout-land/layout_ids1.xml")

    assertResourceIsFileBased("@string/app_name", expectedIsFileBased = false)
    assertResourceIsFileBased("@dimen/myDimen", expectedIsFileBased = false)
    assertResourceIsFileBased("@id/my_id", expectedIsFileBased = false)
  }

  @Test
  fun isFileBased_color() {
    projectRule.fixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml")
    projectRule.fixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml")

    assertResourceIsFileBased("@color/myColor", expectedIsFileBased = false)
    assertResourceIsFileBased("@color/my_state_list", expectedIsFileBased = true)
  }

  @Test
  fun isFileBased_drawable() {
    projectRule.fixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png")
    projectRule.fixture.copyFileToProject("dom/resources/overlayable_example.xml", "res/values/values.xml")

    assertResourceIsFileBased("@drawable/ic_delete", expectedIsFileBased = true)
    assertResourceIsFileBased("@drawable/car_ui_activity_background", expectedIsFileBased = false)
  }

  private fun assertResourceIsFileBased(resourceName: String, expectedIsFileBased: Boolean) {
    val context = projectRule.fixture.loadNewFile("com/example/Hello.java", "package com.example; public class Hello {}")

    val fileBased = runReadAction {
      isFileBased(ResourceUrl.parse(resourceName)!!.resolve(RES_AUTO, ResourceNamespace.Resolver.EMPTY_RESOLVER)!!, context)
    }

    assertThat(fileBased).isEqualTo(expectedIsFileBased)
  }
}
