/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.ValidatorData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.getIfSingle
import com.intellij.util.containers.isEmpty
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class NlAtfIssueTest : LayoutTestCase() {

  @Mock lateinit var mockEventListener: NlAtfIssue.EventListener
  @Mock lateinit var mockSource: NlComponentIssueSource

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)
  }

  @Test
  fun testCreate() {
    val category = "category"
    val msg = "msg"
    val srcClass = "srcClass"
    val type = ValidatorData.Type.ACCESSIBILITY
    val level = ValidatorData.Level.ERROR

    val result =
      ValidatorData.Issue.IssueBuilder()
        .setCategory(category)
        .setType(type)
        .setMsg(msg)
        .setLevel(level)
        .setSourceClass(srcClass)
        .build()
    val issueSource = mockSource

    val atfIssue = NlAtfIssue(result, issueSource)

    assertEquals("Accessibility Issue", atfIssue.summary)
    assertEquals(msg, atfIssue.description)
    assertEquals(HighlightSeverity.ERROR, atfIssue.severity)
    assertTrue(atfIssue.fixes.isEmpty())
    assertNull(atfIssue.hyperlinkListener)
    assertEquals(issueSource, atfIssue.source)
    assertEquals(srcClass, atfIssue.srcClass)
  }

  @Test
  fun testHyperlinkListener() {
    val helpfulLink = "www.google.com"
    val msg = "msg"

    val result =
      ValidatorData.Issue.IssueBuilder()
        .setCategory("category")
        .setType(ValidatorData.Type.ACCESSIBILITY)
        .setMsg(msg)
        .setLevel(ValidatorData.Level.ERROR)
        .setSourceClass("srcClass")
        .setHelpfulUrl(helpfulLink)
        .build()

    val atfIssue = NlAtfIssue(result, mockSource)

    assertTrue(atfIssue.description.contains(msg))
    assertTrue(atfIssue.description.contains("""<a href="$helpfulLink">"""))
    assertNotNull(atfIssue.hyperlinkListener)
  }

  @Test
  fun testIgnoreButton() {
    val result = ScannerTestHelper.createTestIssueBuilder().build()
    val atfIssue = NlAtfIssue(result, mockSource)

    assertEquals(0, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
    val ignore = atfIssue.suppresses.getIfSingle()!!
    assertEquals("Suppress", ignore.buttonText)
  }

  @Test
  fun testIgnoreClicked() {
    val srcClass = "SrcClass"
    val result = ScannerTestHelper.createTestIssueBuilder().setSourceClass(srcClass).build()
    val atfIssue = NlAtfIssue(result, mockSource, mockEventListener)
    val savedCalls = mutableListOf<String>()
    `when`(mockSource.setAttribute(anyString(), anyString(), anyString())).then {
      savedCalls.add(
        "set: namespace=${it.getArgument<String?>(0)}, attribute=${it.getArgument<String>(1)}, value=${it.getArgument<String?>(2)}"
      )
    }

    assertEquals(0, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
    val ignore = atfIssue.suppresses.getIfSingle()!!
    // Simulate ignore button click
    ignore.action.run()

    assertEquals(
      "set: namespace=http://schemas.android.com/tools, attribute=ignore, value=SrcClass",
      savedCalls.joinToString("\n"),
    )
  }

  @Test
  fun testIgnoreClickedIgnoreAlreadyExist() {
    val getAttrResult = "hardcodedText,someOtherLintToIgnore,test"
    val srcClass = "SrcClass"

    val result = ScannerTestHelper.createTestIssueBuilder().setSourceClass(srcClass).build()
    val atfIssue = NlAtfIssue(result, mockSource)
    val savedCalls = mutableListOf<String>()
    `when`(mockSource.getAttribute(anyString(), anyString())).thenReturn(getAttrResult)
    `when`(mockSource.setAttribute(anyString(), anyString(), anyString())).then {
      savedCalls.add(
        "set: namespace=${it.getArgument<String?>(0)}, attribute=${it.getArgument<String>(1)}, value=${it.getArgument<String?>(2)}"
      )
    }

    assertEquals(0, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
    val ignore = atfIssue.suppresses.getIfSingle()!!

    // Simulate ignore button click
    ignore.action.run()

    assertEquals(
      "set: namespace=http://schemas.android.com/tools, attribute=ignore, value=hardcodedText,someOtherLintToIgnore,test,SrcClass",
      savedCalls.joinToString("\n"),
    )
  }

  @Test
  fun testFixClickedWithSetViewAttributeFix() {
    val attributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val fixDescription = "Set this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", attributeName)
    val setAttributeFix =
      ValidatorData.SetViewAttributeFix(viewAttribute, suggestedValue, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(setAttributeFix).build()
    val atfIssue = NlAtfIssue(result, mockSource, mockEventListener)

    // Both fix button and ignore button are displayed
    assertEquals(1, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
    val fix = atfIssue.fixes.getIfSingle()!!
    // Simulate fix button click
    fix.action.run()
    verify(mockEventListener).onApplyFixButtonClicked(result)
  }

  @Test
  fun applySetViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val fixDescription = "Set this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", attributeName)
    val setAttributeFix =
      ValidatorData.SetViewAttributeFix(viewAttribute, suggestedValue, fixDescription)

    applyFixImpl(setAttributeFix, testSrc)

    assertEquals(
      "set: namespace=http://schemas.android.com/apk/res/android, attribute=textColor, value=#FFFFFF",
      testSrc.callsString(),
    )
  }

  @Test
  fun testFixClickedWithRemoveViewAttributeFix() {
    val attributeName = "contentDescription"
    val fixDescription = "Remove this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", attributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(viewAttribute, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(removeAttributeFix).build()
    val atfIssue = NlAtfIssue(result, mockSource)

    // Both fix button and ignore button are displayed
    assertEquals(1, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
  }

  @Test
  fun applyRemoveViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "contentDescription"
    val fixDescription = "Remove this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", attributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(viewAttribute, fixDescription)

    applyFixImpl(removeAttributeFix, testSrc)

    assertEquals(
      "remove: namespace=http://schemas.android.com/apk/res/android, name=contentDescription",
      testSrc.callsString(),
    )
  }

  @Test
  fun testFixClickedWithCompoundFix() {
    val setAttributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val setViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", setAttributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(setViewAttribute, suggestedValue, "")
    val removeAttributeName = "contentDescription"
    val removeViewAttribute =
      ValidatorData.ViewAttribute(ANDROID_URI, "android", removeAttributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(removeViewAttribute, "")
    val fixDescription =
      "Set this item's android:textColor to #FFFFFF. Remove this item's android:textColor to #FFFFFF."
    val compoundFix =
      ValidatorData.CompoundFix(listOf(setAttributeFix, removeAttributeFix), fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(compoundFix).build()
    val atfIssue = NlAtfIssue(result, mockSource)

    // Both fix button and ignore button are displayed
    assertEquals(1, atfIssue.fixes.count())
    assertEquals(1, atfIssue.suppresses.count())
  }

  @Test
  fun applyCompoundFix() {
    val testSrc = TestSource()
    val setAttributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val setViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI, "android", setAttributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(setViewAttribute, suggestedValue, "")
    val removeAttributeName = "contentDescription"
    val removeViewAttribute =
      ValidatorData.ViewAttribute(ANDROID_URI, "android", removeAttributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(removeViewAttribute, "")
    val fixDescription =
      "Set this item's android:textColor to #FFFFFF. Remove this item's android:textColor to #FFFFFF."
    val compoundFix =
      ValidatorData.CompoundFix(listOf(setAttributeFix, removeAttributeFix), fixDescription)

    applyFixImpl(compoundFix, testSrc)

    assertEquals(
      """
      set: namespace=http://schemas.android.com/apk/res/android, attribute=textColor, value=#FFFFFF
      remove: namespace=http://schemas.android.com/apk/res/android, name=contentDescription
    """
        .trimIndent(),
      testSrc.callsString(),
    )
  }

  class TestSource : IssueSource, NlAttributesHolder {
    override val files: Set<VirtualFile> = emptySet()
    override val displayText: String = "displayText"

    var getAttrResult = ""

    override fun getAttribute(namespace: String?, attribute: String): String {
      return getAttrResult
    }

    private val savedCalls = mutableListOf<String>()

    override fun setAttribute(namespace: String?, attribute: String, value: String?) {
      savedCalls.add("set: namespace=$namespace, attribute=$attribute, value=$value")
    }

    override fun removeAttribute(namespace: String, name: String) {
      savedCalls.add("remove: namespace=$namespace, name=$name")
    }

    fun callsString(): String = savedCalls.joinToString("\n")
  }
}
