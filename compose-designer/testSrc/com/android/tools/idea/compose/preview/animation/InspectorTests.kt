/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.runInEdtAndGet
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule

/** A base for tests creating inspector. */
open class InspectorTests {

  lateinit var psiFilePointer: SmartPsiElementPointer<PsiFile>

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  lateinit var parentDisposable: Disposable

  lateinit var surface: NlDesignSurface

  @Before
  open fun setUp() {
    parentDisposable = projectRule.fixture.testRootDisposable
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
        )
        .build()
    }
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
    surface.addModelWithoutRender(model)

    val psiFile =
      projectRule.fixture.addFileToProject(
        "src/main/Test.kt",
        """
      fun main() {}
    """.trimIndent()
      )
    invokeAndWaitIfNeeded { psiFilePointer = SmartPointerManager.createPointer(psiFile) }
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATE_X_AS_STATE.override(true)
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATED_CONTENT.override(true)
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_INFINITE_TRANSITION.override(true)
  }

  @After
  open fun tearDown() {
    ComposePreviewAnimationManager.closeCurrentInspector()
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATE_X_AS_STATE.clearOverride()
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATED_CONTENT.clearOverride()
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_INFINITE_TRANSITION.clearOverride()
  }

  fun createAndOpenInspector(): AnimationPreview {
    Assert.assertFalse(ComposePreviewAnimationManager.isInspectorOpen())
    ComposePreviewAnimationManager.createAnimationInspectorPanel(
      surface,
      parentDisposable,
      psiFilePointer
    ) {}
    Assert.assertTrue(ComposePreviewAnimationManager.isInspectorOpen())
    return ComposePreviewAnimationManager.currentInspector!!
  }
}
