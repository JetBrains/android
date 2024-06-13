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
package com.android.tools.idea.common.error

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.visual.ConfigurationSet
import com.android.tools.idea.uibuilder.visual.TestVisualizationContentProvider
import com.android.tools.idea.uibuilder.visual.VisualizationTestToolWindowManager
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertInstanceOf
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class VisualLintIssueNodeTest {
  @JvmField @Rule val rule = AndroidProjectRule.withSdk().onEdt()

  @RunsInEdt
  @Test
  fun testNavigatable() {
    val model =
      NlModelBuilderUtil.model(
          rule.projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              ComponentDescriptor(SdkConstants.TEXT_VIEW)
                .width("100dp")
                .height("20dp")
                .children(
                  ComponentDescriptor(SdkConstants.TEXT_VIEW)
                    .width("100dp")
                    .height("20dp")
                    .withAttribute(
                      SdkConstants.ANDROID_URI,
                      SdkConstants.ATTR_LAYOUT_MARGIN,
                      "-10dp",
                    )
                )
            ),
        )
        .build()

    val issueProvider = ViewVisualLintIssueProvider(rule.testRootDisposable)
    val issue =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.BOUNDS,
        model.components.first().children,
        issueProvider,
      )
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    val navigation = node.getNavigatable()
    assertNotNull(navigation)

    // This navigation should open Validation Tool and set configuration set to
    // ConfigurationSet.WindowSizeDevices
    val toolManager =
      VisualizationTestToolWindowManager(rule.project, rule.fixture.testRootDisposable)
    rule.projectRule.replaceProjectService(ToolWindowManager::class.java, toolManager)
    val toolWindow =
      ToolWindowManager.getInstance(rule.project)
        .getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID)!!
    TestVisualizationContentProvider.createVisualizationForm(rule.project, toolWindow)
    toolWindow.isAvailable = true

    val content = VisualizationToolWindowFactory.getVisualizationContent(rule.project)!!
    content.setConfigurationSet(ConfigurationSet.LargeFont)

    navigation.navigate(false)
    assertEquals(ConfigurationSet.WindowSizeDevices, content.getConfigurationSet())
  }

  @RunsInEdt
  @Test
  fun testNotNavigateToComponentWhenSuppressed() {
    val errorType = VisualLintErrorType.BOUNDS
    val model =
      NlModelBuilderUtil.model(
          rule.projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              ComponentDescriptor(SdkConstants.TEXT_VIEW)
                .width("100dp")
                .height("20dp")
                .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN, "-10dp")
                .withAttribute(
                  SdkConstants.TOOLS_URI,
                  SdkConstants.ATTR_IGNORE,
                  errorType.ignoredAttributeValue,
                )
            ),
        )
        .build()

    val issueProvider = ViewVisualLintIssueProvider(rule.testRootDisposable)
    val issue =
      createTestVisualLintRenderIssue(errorType, model.components.first().children, issueProvider)
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    assertInstanceOf<SelectWindowSizeDevicesNavigatable>(node.getNavigatable())
  }

  @RunsInEdt
  @Test
  fun testNavigatableForWear() {
    val configurationManager = ConfigurationManager.getOrCreateInstance(rule.projectRule.module)
    val model =
      NlModelBuilderUtil.model(
          rule.projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(ComponentDescriptor(SdkConstants.TEXT_VIEW).width("100dp").height("20dp")),
        )
        .setDevice(RenderTestUtil.findDeviceById(configurationManager, "wearos_rect"))
        .build()

    val issueProvider = ViewVisualLintIssueProvider(rule.testRootDisposable)
    val issue =
      createTestVisualLintRenderIssue(
        VisualLintErrorType.WEAR_MARGIN,
        model.components.first().children,
        issueProvider,
      )
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    val navigation = node.getNavigatable()
    assertNotNull(navigation)

    // This navigation should open Validation Tool and set configuration set to
    // ConfigurationSet.WearDevices
    val toolManager =
      VisualizationTestToolWindowManager(rule.project, rule.fixture.testRootDisposable)
    rule.projectRule.replaceProjectService(ToolWindowManager::class.java, toolManager)
    val toolWindow =
      ToolWindowManager.getInstance(rule.project)
        .getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID)!!
    TestVisualizationContentProvider.createVisualizationForm(rule.project, toolWindow)
    toolWindow.isAvailable = true

    val content = VisualizationToolWindowFactory.getVisualizationContent(rule.project)!!
    content.setConfigurationSet(ConfigurationSet.LargeFont)

    navigation.navigate(false)
    assertEquals(ConfigurationSet.WearDevices, content.getConfigurationSet())
  }

  @RunsInEdt
  @Test
  fun testNotNavigateToComponentWhenSuppressedForWear() {
    val configurationManager = ConfigurationManager.getOrCreateInstance(rule.projectRule.module)
    val errorType = VisualLintErrorType.WEAR_MARGIN
    val model =
      NlModelBuilderUtil.model(
          rule.projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              ComponentDescriptor(SdkConstants.TEXT_VIEW)
                .width("100dp")
                .height("20dp")
                .withAttribute(
                  SdkConstants.TOOLS_URI,
                  SdkConstants.ATTR_IGNORE,
                  errorType.ignoredAttributeValue,
                )
            ),
        )
        .setDevice(RenderTestUtil.findDeviceById(configurationManager, "wearos_rect"))
        .build()

    val issueProvider = ViewVisualLintIssueProvider(rule.testRootDisposable)
    val issue =
      createTestVisualLintRenderIssue(errorType, model.components.first().children, issueProvider)
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    assertInstanceOf<SelectWearDevicesNavigatable>(node.getNavigatable())
  }

  @RunsInEdt
  @Test
  fun testForCompose() {
    val file = rule.fixture.createFile("Compose.kt", "Compose file")
    val model = mock(NlModel::class.java)
    val navigatable = OpenFileDescriptor(rule.project, file)
    val component = NlComponent(model, 651L).apply { setNavigatable(navigatable) }
    val issue =
      VisualLintRenderIssue.builder()
        .summary("")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(model)
        .components(mutableListOf(component))
        .type(VisualLintErrorType.BOUNDS)
        .build()
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    assertEquals(0, node.getChildren().size)
    assertEquals(0, navigatable.compareTo(node.getNavigatable() as OpenFileDescriptor))
  }

  @Test
  fun testEquality() {
    val file = rule.fixture.createFile("Compose.kt", "Compose file")
    val model = mock(NlModel::class.java)
    val navigatable1 = OpenFileDescriptor(rule.project, file, 100)
    val navigatable2 = OpenFileDescriptor(rule.project, file, 100)
    val component1 = NlComponent(model, 651L).apply { setNavigatable(navigatable1) }
    val component2 = NlComponent(model, 651L).apply { setNavigatable(navigatable2) }
    val issue1 =
      VisualLintRenderIssue.builder()
        .summary("")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(model)
        .components(mutableListOf(component1))
        .type(VisualLintErrorType.BOUNDS)
        .build()
    val issue2 =
      VisualLintRenderIssue.builder()
        .summary("")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(model)
        .components(mutableListOf(component2))
        .type(VisualLintErrorType.BOUNDS)
        .build()
    val parentNode = CommonIssueTestParentNode(rule.projectRule.project)
    val node1 = VisualLintIssueNode(issue1, parentNode)
    val node2 = VisualLintIssueNode(issue2, parentNode)
    assertTrue(node1 == node2)
  }
}
