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
import com.android.ide.common.gradle.Version
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlDependencyManager
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.XmlType
import com.android.tools.idea.uibuilder.util.MockNlComponent
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test


class BottomAppBarHandlerTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory().onEdt()
  
  private val projectRule
    get() = androidProjectRule.projectRule

  @Test
  fun testGetXml() {
    val expected = """<com.google.android.material.bottomappbar.BottomAppBar
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom" />
"""
    val handler = BottomAppBarHandler()
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION)).isEqualTo(expected)
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.DRAG_PREVIEW)).isEqualTo(expected)
  }

  @RunsInEdt
  @Test
  fun testOnCreate() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val manager: NlDependencyManager = mock()
    whenever(manager.getModuleDependencyVersion(GoogleMavenArtifactId.ANDROIDX_DESIGN, facet)).thenReturn(Version.parse("1.4.9"))
    projectRule.replaceService(NlDependencyManager::class.java, manager)

    val handler = BottomAppBarHandler()
    val component = WriteCommandAction.runWriteCommandAction(projectRule.project, Computable {
      val component = createComponent(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION))
      handler.onCreate(null, component, InsertType.CREATE)
      component
    })
    val expected = """
      <com.google.android.material.bottomappbar.BottomAppBar
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_gravity="bottom"
          xmlns:android="http://schemas.android.com/apk/res/android"
          style="@style/Widget.MaterialComponents.BottomAppBar.Colored"/>
      """.trim().replace("\\s+".toRegex()," ")
    assertThat(component.tag!!.text.replace("\\s+".toRegex()," ")).isEqualTo(expected)
  }

  @RunsInEdt
  @Test
  fun testOnCreateWithMaterial3() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val manager: NlDependencyManager = mock()
    whenever(manager.getModuleDependencyVersion(GoogleMavenArtifactId.ANDROIDX_DESIGN, facet)).thenReturn(Version.parse("1.5.0"))
    projectRule.replaceService(NlDependencyManager::class.java, manager)

    val handler = BottomAppBarHandler()
    val component = WriteCommandAction.runWriteCommandAction(projectRule.project, Computable {
      val component = createComponent(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION))
      handler.onCreate(null, component, InsertType.CREATE)
      component
    })
    val expected = """
      <com.google.android.material.bottomappbar.BottomAppBar
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_gravity="bottom"
          xmlns:android="http://schemas.android.com/apk/res/android"/>
      """.trim().replace("\\s+".toRegex()," ")
    assertThat(component.tag!!.text.replace("\\s+".toRegex()," ")).isEqualTo(expected)
  }

  private fun createViewEditor(): ViewEditor {
    val editor: ViewEditor = mock()
    val model: NlModel = mock()
    whenever(model.facet).thenReturn(AndroidFacet.getInstance(projectRule.module))
    whenever(editor.model).thenReturn(model)
    return editor
  }

  private fun createComponent(text: String): NlComponent {
    val tag = XmlTagUtil.createTag(projectRule.project, text)
    tag.putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
    return MockNlComponent.create(tag)
  }
}
