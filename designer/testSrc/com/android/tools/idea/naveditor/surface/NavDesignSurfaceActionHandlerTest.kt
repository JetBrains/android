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
package com.android.tools.idea.naveditor.surface

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.ide.impl.DataManagerImpl

class NavDesignSurfaceActionHandlerTest : NavTestCase() {
  fun testDelete() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
        }
        fragment("fragment2") {
          action("a2", destination = "fragment3")
        }
        fragment("fragment3") {
          action("a3", destination = "fragment1")
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    val handler = NavDesignSurfaceActionHandler(surface)
    val context = DataManagerImpl.MyDataContext(model.surface)
    assertFalse(handler.canDeleteElement(context))

    surface.selectionModel.setSelection(listOf(model.find("fragment2")))
    assertTrue(handler.canDeleteElement(context))

    handler.deleteElement(context)

    assertSameElements(model.flattenComponents().toArray(),
                       model.components[0], model.find("fragment1"), model.find("fragment3"), model.find("a3"))
  }
}