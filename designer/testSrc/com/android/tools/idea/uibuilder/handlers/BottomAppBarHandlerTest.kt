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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.api.XmlType
import com.android.tools.idea.uibuilder.util.MockNlComponent
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

class BottomAppBarHandlerTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory().onEdt()

  private val projectRule
    get() = androidProjectRule.projectRule

  @Test
  fun testGetXml() {
    val expected =
      """<com.google.android.material.bottomappbar.BottomAppBar
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom" />
"""
    val handler = BottomAppBarHandler()
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION))
      .isEqualTo(expected)
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.DRAG_PREVIEW))
      .isEqualTo(expected)
  }

  @RunsInEdt
  @Test
  fun testOnCreate() {
    ExtensionTestUtil.maskExtensions(
      UIBuilderHandlerToken.EP_NAME,
      listOf(),
      projectRule.testRootDisposable,
    )
    val handler = BottomAppBarHandler()
    val component =
      WriteCommandAction.runWriteCommandAction(
        projectRule.project,
        Computable {
          val component =
            createComponent(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION))
          handler.onCreate(null, component, InsertType.CREATE)
          component
        },
      )
    val expected =
      """
      <com.google.android.material.bottomappbar.BottomAppBar
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_gravity="bottom"
          xmlns:android="http://schemas.android.com/apk/res/android"
          style="@style/Widget.MaterialComponents.BottomAppBar.Colored"/>
      """
        .trim()
        .replace("\\s+".toRegex(), " ")
    assertThat(component.tag!!.text.replace("\\s+".toRegex(), " ")).isEqualTo(expected)
  }

  @RunsInEdt
  @Test
  fun testOnCreateWithMaterial3() {
    val token =
      object : UIBuilderHandlerToken<AndroidProjectSystem> {
        override fun isApplicable(projectSystem: AndroidProjectSystem) = true

        override fun getBottomAppBarStyle(
          projectSystem: AndroidProjectSystem,
          newChild: NlComponent,
        ) = null
      }
    ExtensionTestUtil.maskExtensions(
      UIBuilderHandlerToken.EP_NAME,
      listOf(token),
      projectRule.testRootDisposable,
    )
    val handler = BottomAppBarHandler()
    val component =
      WriteCommandAction.runWriteCommandAction(
        projectRule.project,
        Computable {
          val component =
            createComponent(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION))
          handler.onCreate(null, component, InsertType.CREATE)
          component
        },
      )
    val expected =
      """
      <com.google.android.material.bottomappbar.BottomAppBar
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_gravity="bottom"
          xmlns:android="http://schemas.android.com/apk/res/android"/>
      """
        .trim()
        .replace("\\s+".toRegex(), " ")
    assertThat(component.tag!!.text.replace("\\s+".toRegex(), " ")).isEqualTo(expected)
  }

  private fun createComponent(text: String): NlComponent {
    val tag = XmlTagUtil.createTag(projectRule.project, text)
    tag.putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
    return MockNlComponent.create(tag)
  }
}
