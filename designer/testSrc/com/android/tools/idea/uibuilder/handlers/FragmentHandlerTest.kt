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

import com.android.SdkConstants.ATTR_DEFAULT_NAV_HOST
import com.android.SdkConstants.ATTR_NAV_GRAPH
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.XmlElementFactory
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.eq
import java.util.EnumSet

class FragmentHandlerTest : LayoutTestCase() {
  // http://b/242129835
  fun ignore_testActivateNavFragment() {
    myFixture.addFileToProject("res/navigation/nav.xml", "<navigation/>")
    val model = model(
      "model.xml",
      component("LinearLayout")
        .id("@+id/outer")
        .withBounds(0, 0, 100, 100)
        .children(
          component("fragment")
            .id("@+id/navhost")
            .withAttribute(AUTO_URI, ATTR_NAV_GRAPH, "@navigation/nav")
            .withBounds(0, 0, 100, 50),
          component("fragment")
            .id("@+id/regular")
            .withBounds(0, 50, 100, 50)
        )
    ).build()

    val surface = NlDesignSurface.build(project, project)
    surface.model = model
    val editorManager = FileEditorManager.getInstance(project)

    surface.notifyComponentActivate(model.find("regular")!!, 10, 60)
    AndroidTestCase.assertEmpty(editorManager.openFiles)

    surface.notifyComponentActivate(model.find("navhost")!!, 10, 10)
    AndroidTestCase.assertEquals("nav.xml", editorManager.openFiles[0].name)
  }

  fun testCreateNavHost() {
    val model = model(
        "model.xml",
        component("LinearLayout")
          .id("@+id/outer")
          .withBounds(0, 0, 100, 100))
      .build()

    val tag = XmlElementFactory.getInstance(getProject()).createTagFromText(
        "    <fragment\n" +
        "        android:id=\"@+id/fragment\"\n" +
        "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n/>")

    mockStatic<ViewEditor>(testRootDisposable).whenever<String> {
      ViewEditor.displayResourceInput(eq(model), eq("Navigation Graphs"), eq(EnumSet.of(ResourceType.NAVIGATION)))
    }.thenReturn("@navigation/testNav")

    WriteCommandAction.runWriteCommandAction(model.project, null, null,
                                             { model.createComponent(tag, model.find("outer"), null, InsertType.CREATE) }, model.file)
    val newFragment = model.find("fragment")!!
    assertEquals("@navigation/testNav", newFragment.getAttribute(AUTO_URI, ATTR_NAV_GRAPH))
    assertEquals("true", newFragment.getAttribute(AUTO_URI, ATTR_DEFAULT_NAV_HOST))
  }
}