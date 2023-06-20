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
package com.android.tools.idea.glance.preview

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GlanceScreenViewProviderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: NlDesignSurface

  @Before
  fun setUp() {
    val model = invokeAndWaitIfNeeded {
      NlModelBuilderUtil.model(
          projectRule.module.androidFacet!!,
          projectRule.fixture,
          SdkConstants.FD_RES_LAYOUT,
          "model.xml",
          ComponentDescriptor("LinearLayout")
        )
        .build()
    }
    surface = NlDesignSurface.build(projectRule.project, projectRule.testRootDisposable)
    surface.model = model
  }

  @Test
  fun testNoSecondarySceneView() {
    Assert.assertNull(
      GLANCE_SCREEN_VIEW_PROVIDER.createSecondarySceneView(surface, surface.sceneManager!!)
    )
  }

  @Test
  fun testNotResizable() {
    Assert.assertFalse(
      GLANCE_SCREEN_VIEW_PROVIDER.createPrimarySceneView(surface, surface.sceneManager!!)
        .isResizeable
    )
  }
}
