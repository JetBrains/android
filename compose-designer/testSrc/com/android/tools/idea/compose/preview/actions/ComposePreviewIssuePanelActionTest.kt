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
package com.android.tools.idea.compose.preview.actions

import com.android.SdkConstants
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.error.RenderIssueProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
internal class ComposePreviewIssuePanelActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRenderErrorModel = EdtRule()

  private lateinit var issueModel: IssueModel

  @Before
  fun setUp() {
    issueModel = IssueModel.createForTesting(projectRule.testRootDisposable, projectRule.project)
  }

  private fun createIssue(level: HighlightSeverity, title: String) =
    RenderErrorModel.Issue.builder().setSeverity(level).setSummary(title).build()

  private fun buildModel(name: String) = NlModelBuilderUtil.model(projectRule, "layout", name,
                                                                  ComponentDescriptor(
                                                                    SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build().also {
    Disposer.register(projectRule.testRootDisposable, it)
  }

  @Test
  fun `verify not enabled for no issues`() {
    val model1 = buildModel("model1.xml")
    val model2 = buildModel("model2.xml")
    val model3 = buildModel("model3.xml")

    issueModel.addIssueProvider(RenderIssueProvider(
      model1,
      RenderErrorModel(
        listOf(
          createIssue(HighlightSeverity.INFORMATION, "title"),
          createIssue(HighlightSeverity.INFORMATION, "title2"),
          createIssue(HighlightSeverity.ERROR, "title2"),
        )
      )
    ))
    issueModel.addIssueProvider(RenderIssueProvider(model2, RenderErrorModel(listOf())))

    run {
      val event = TestActionEvent(MapDataContext())
      ComposePreviewIssuePanelAction({ model2 }, { issueModel })
        .update(event)
      assertFalse(event.presentation.isEnabledAndVisible)
    }

    run {
      val event = TestActionEvent(MapDataContext())
      ComposePreviewIssuePanelAction({ model3 }, { issueModel })
        .update(event)
      assertFalse(event.presentation.isEnabledAndVisible)
    }
  }

  @Test
  fun `verify highest error level is reported`() {
    val model1 = buildModel("model1.xml")
    val model2 = buildModel("model2.xml")

    issueModel.addIssueProvider(RenderIssueProvider(
      model1,
      RenderErrorModel(
        listOf(
          createIssue(HighlightSeverity.INFORMATION, "title"),
          createIssue(HighlightSeverity.INFORMATION, "title2"),
          createIssue(HighlightSeverity.ERROR, "title2"),
        )
      )
    ))
    issueModel.addIssueProvider(RenderIssueProvider(
      model2,
      RenderErrorModel(
        listOf(
          createIssue(HighlightSeverity.WARNING, "title"),
        )
      )
    ))

    run {
      val event = TestActionEvent(MapDataContext())
      ComposePreviewIssuePanelAction({ model1 }, { issueModel })
        .update(event)
      assertTrue(event.presentation.isEnabledAndVisible)
      assertEquals(StudioIcons.Common.ERROR, event.presentation.icon)
    }

    run {
      val event = TestActionEvent(MapDataContext())
      ComposePreviewIssuePanelAction({ model2 }, { issueModel })
        .update(event)
      assertTrue(event.presentation.isEnabledAndVisible)
      assertEquals(StudioIcons.Common.WARNING, event.presentation.icon)
    }
  }
}