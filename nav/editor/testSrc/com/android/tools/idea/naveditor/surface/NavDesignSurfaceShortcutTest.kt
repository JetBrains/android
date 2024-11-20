/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer

class NavDesignSurfaceShortcutTest : NavTestCase() {

  fun testNavDesignSurfaceProvideTheZoomableContext() {
    // Simply test NavDesignSurface provide data for ZOOMABLE_KEY
    val surface = NavDesignSurface(project).also { Disposer.register(myFixture.testRootDisposable, it) }
    val event = AnActionEvent.createFromDataContext(
      "", null, DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface))
    assertNotNull(event.getData(ZOOMABLE_KEY))
  }
}
