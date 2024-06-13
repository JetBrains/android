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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.SimpleTextAttributes
import icons.StudioIcons
import junit.framework.Assert
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** TODO: Needs to test the cases which use [NlComponentIssueSource] as [IssueSource]. */
class IssueNodeTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testNoNavigatableWhenNoFile() {
    val node = IssueNode(null, TestIssue(), null)
    assertNull(node.getNavigatable())
  }

  @Test
  fun testNavigatableToFile() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "content").virtualFile

    val descriptor =
      IssueNode(file, TestIssue(), CommonIssueTestParentNode(projectRule.project)).getNavigatable()
        as OpenFileDescriptor

    Assert.assertEquals(projectRule.project, descriptor.project)
    Assert.assertEquals(file, descriptor.file)
    assertNull(descriptor.rangeMarker)
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testNavigatableOfBackedVirtualFile() {
    val originalFile =
      projectRule.fixture.addFileToProject("path/to/original/file", "original content").virtualFile
    val lightVirtualFile =
      object : LightVirtualFile(), BackedVirtualFile {
        override fun getOriginFile(): VirtualFile = originalFile
      }

    val descriptor =
      IssueNode(lightVirtualFile, TestIssue(), CommonIssueTestParentNode(projectRule.project))
        .getNavigatable() as OpenFileDescriptor

    Assert.assertEquals(projectRule.project, descriptor.project)
    Assert.assertEquals(originalFile, descriptor.file)
    assertNull(descriptor.rangeMarker)
  }

  @Test
  fun testPresentation() {
    val node =
      IssueNode(
        null,
        TestIssue(summary = "Test summary", severity = HighlightSeverity.INFORMATION),
        null,
      )
    node.update()

    val expected = PresentationData()
    expected.setIcon(HighlightDisplayLevel.find(HighlightSeverity.INFORMATION)!!.icon)
    expected.addText("Test summary", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.tooltip = "Test summary"

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testSameNode() {
    val node1 =
      IssueNode(
        null,
        TestIssue(summary = "Test summary", severity = HighlightSeverity.INFORMATION),
        null,
      )
    val node2 =
      IssueNode(
        null,
        TestIssue(summary = "Test summary", severity = HighlightSeverity.INFORMATION),
        null,
      )
    Assert.assertEquals(node1, node2)
  }

  @Test
  fun testPresentationWithRegisteredCustomSeverity() {
    val customSeverity = HighlightSeverity("My Severity", HighlightSeverity.ERROR.myVal + 1)

    HighlightDisplayLevel.registerSeverity(
      customSeverity,
      TextAttributesKey.createTextAttributesKey("test"),
      StudioIcons.Common.ANDROID_HEAD,
    )

    val node =
      IssueNode(null, TestIssue(summary = "Custom severity", severity = customSeverity), null)
    node.update()

    val expected = PresentationData()
    expected.setIcon(StudioIcons.Common.ANDROID_HEAD)
    expected.addText("Custom severity", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.tooltip = "Custom severity"

    val presentation = node.presentation
    Assert.assertEquals(expected, presentation)
  }

  @Test
  fun testPresentationWithUnregisteredCustomSeverity() {
    for (severity in DESCEND_ORDER_DEFAULT_SEVERITIES) {
      val severityIcon = HighlightDisplayLevel.find(severity)?.icon ?: continue

      val customSeverity = HighlightSeverity("My Severity", severity.myVal + 1)
      val node =
        IssueNode(null, TestIssue(summary = "Custom severity", severity = customSeverity), null)
      node.update()

      val expected = PresentationData()
      expected.setIcon(severityIcon)
      expected.addText("Custom severity", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      expected.tooltip = "Custom severity"

      val presentation = node.presentation
      Assert.assertEquals(expected, presentation)
    }
  }
}
