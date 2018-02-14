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

import com.android.SdkConstants.ATTR_NAV_GRAPH
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.fileEditor.FileEditorManager
import org.jetbrains.android.AndroidTestCase

class FragmentHandlerTest : LayoutTestCase() {
  fun testActivateNavFragment() {
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

    val surface = NlDesignSurface(project, false, project)
    surface.model = model
    val editorManager = FileEditorManager.getInstance(project)

    surface.notifyComponentActivate(model.find("regular")!!, 10, 60)
    AndroidTestCase.assertEmpty(editorManager.openFiles)

    surface.notifyComponentActivate(model.find("navhost")!!, 10, 10)
    AndroidTestCase.assertEquals("nav.xml", editorManager.openFiles[0].name)
  }

}