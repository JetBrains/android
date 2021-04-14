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
import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.ValidatorData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.containers.isEmpty
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class NlAtfIssueTest : LayoutTestCase() {

  @Mock
  lateinit var mockEventListener:NlAtfIssue.EventListener
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

    val result = ValidatorData.Issue.IssueBuilder()
      .setCategory(category)
      .setType(type)
      .setMsg(msg)
      .setLevel(level)
      .setSourceClass(srcClass)
      .build()
    val issueSource = IssueSource.NONE

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

    val result = ValidatorData.Issue.IssueBuilder()
      .setCategory("category")
      .setType(ValidatorData.Type.ACCESSIBILITY)
      .setMsg(msg)
      .setLevel(ValidatorData.Level.ERROR)
      .setSourceClass("srcClass")
      .setHelpfulUrl(helpfulLink)
      .build()

    val atfIssue = NlAtfIssue(result, IssueSource.NONE)

    assertTrue(atfIssue.description.contains(msg))
    assertTrue(atfIssue.description.contains("""<a href="$helpfulLink">"""))
    assertNotNull(atfIssue.hyperlinkListener)
  }

  @Test
  fun testIgnoreButton() {
    val result = ScannerTestHelper.createTestIssueBuilder().build()
    val atfIssue = NlAtfIssue(result, TestSource())

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      assertEquals("Ignore", ignore.buttonText)
    }
  }

  @Test
  fun testIgnoreClicked() {
    val testSrc = TestSource()
    val srcClass = "SrcClass"
    val result = ScannerTestHelper.createTestIssueBuilder()
      .setSourceClass(srcClass)
      .build()
    val atfIssue = NlAtfIssue(result, testSrc, mockEventListener)

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      // Simulate ignore button click
      ignore.runnable.run()

      assertEquals(TOOLS_URI, testSrc.setAttrArgCaptor.namespace)
      assertEquals(ATTR_IGNORE, testSrc.setAttrArgCaptor.attribute)
      assertEquals(srcClass, testSrc.setAttrArgCaptor.value)
      verify(mockEventListener).onIgnoreButtonClicked(result)
    }
  }

  @Test
  fun testIgnoreClickedIgnoreAlreadyExist() {
    val testSrc = TestSource()
    val getAttrResult = "hardcodedText,someOtherLintToIgnore,test"
    val srcClass = "SrcClass"

    testSrc.getAttrResult = getAttrResult
    val result = ScannerTestHelper.createTestIssueBuilder()
      .setSourceClass(srcClass)
      .build()
    val atfIssue = NlAtfIssue(result, testSrc)

    assertEquals(1, atfIssue.fixes.count())
    atfIssue.fixes.forEach {  ignore ->
      // Simulate ignore button click
      ignore.runnable.run()

      assertEquals(TOOLS_URI, testSrc.setAttrArgCaptor.namespace)
      assertEquals(ATTR_IGNORE, testSrc.setAttrArgCaptor.attribute)
      val expected = "$getAttrResult,$srcClass"
      assertEquals(expected, testSrc.setAttrArgCaptor.value)
    }
  }

  @Test
  fun testFixClickedWithSetViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val fixDescription = "Set this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", attributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(viewAttribute, suggestedValue, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(setAttributeFix).build()
    val atfIssue = NlAtfIssue(result, testSrc, mockEventListener)

    // Both fix button and ignore button are displayed
    assertEquals(2, atfIssue.fixes.count())
    atfIssue.fixes.filter{it.buttonText == "Fix"}.forEach {  fix ->
      // Simulate fix button click
      fix.runnable.run()

      verify(mockEventListener).onApplyFixButtonClicked(result)
    }
  }

  @Test
  fun applySetViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val fixDescription = "Set this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", attributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(viewAttribute, suggestedValue, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(setAttributeFix).build()
    val atfIssue = NlAtfIssue(result, testSrc, mockEventListener)

    val component = Mockito.mock(NlComponent::class.java)
    atfIssue.applyFixImpl(setAttributeFix, component)

    verify(component).setAttribute(ANDROID_URI, attributeName, suggestedValue)
  }


  @Test
  fun testFixClickedWithRemoveViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "contentDescription"
    val fixDescription = "Remove this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", attributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(viewAttribute, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(removeAttributeFix).build()
    val atfIssue = NlAtfIssue(result, testSrc)

    // Both fix button and ignore button are displayed
    assertEquals(2, atfIssue.fixes.count())
  }

  @Test
  fun applyRemoveViewAttributeFix() {
    val testSrc = TestSource()
    val attributeName = "contentDescription"
    val fixDescription = "Remove this item's android:textColor to #FFFFFF"
    val viewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", attributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(viewAttribute, fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(removeAttributeFix).build()
    val atfIssue = NlAtfIssue(result, testSrc)

    val component = Mockito.mock(NlComponent::class.java)
    atfIssue.applyFixImpl(removeAttributeFix, component)

    verify(component).removeAttribute(ANDROID_URI, attributeName)
  }

  @Test
  fun testFixClickedWithCompoundFix() {
    val testSrc = TestSource()
    val setAttributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val setViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", setAttributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(setViewAttribute, suggestedValue, "")
    val removeAttributeName = "contentDescription"
    val removeViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", removeAttributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(removeViewAttribute, "")
    val fixDescription = "Set this item's android:textColor to #FFFFFF. Remove this item's android:textColor to #FFFFFF."
    val compoundFix = ValidatorData.CompoundFix(listOf(setAttributeFix, removeAttributeFix), fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(compoundFix).build()
    val atfIssue = NlAtfIssue(result, testSrc)

    // Both fix button and ignore button are displayed
    assertEquals(2, atfIssue.fixes.count())
  }

  @Test
  fun applyCompoundFix() {
    val testSrc = TestSource()
    val setAttributeName = "textColor"
    val suggestedValue = "#FFFFFF"
    val setViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", setAttributeName)
    val setAttributeFix = ValidatorData.SetViewAttributeFix(setViewAttribute, suggestedValue, "")
    val removeAttributeName = "contentDescription"
    val removeViewAttribute = ValidatorData.ViewAttribute(ANDROID_URI,"android", removeAttributeName)
    val removeAttributeFix = ValidatorData.RemoveViewAttributeFix(removeViewAttribute, "")
    val fixDescription = "Set this item's android:textColor to #FFFFFF. Remove this item's android:textColor to #FFFFFF."
    val compoundFix = ValidatorData.CompoundFix(listOf(setAttributeFix, removeAttributeFix), fixDescription)

    val result = ScannerTestHelper.createTestIssueBuilder(compoundFix).build()
    val atfIssue = NlAtfIssue(result, testSrc)

    val component = Mockito.mock(NlComponent::class.java)
    atfIssue.applyFixImpl(compoundFix, component)

    verify(component).setAttribute(ANDROID_URI, setAttributeName, suggestedValue)
    verify(component).removeAttribute(ANDROID_URI, removeAttributeName)
  }

  class TestSource : IssueSource, NlAttributesHolder {
    override val displayText: String = "displayText"
    override val onIssueSelected: (DesignSurface) -> Unit
      get() = TODO("Not yet implemented")

    var getAttrResult = ""
    override fun getAttribute(namespace: String?, attribute: String): String{
      return getAttrResult
    }

    data class SetAttributeArgumentCaptor(var namespace: String? = null, var attribute: String = "", var value: String? = null)
    val setAttrArgCaptor = SetAttributeArgumentCaptor()
    override fun setAttribute(namespace: String?, attribute: String, value: String?) {
      setAttrArgCaptor.namespace = namespace
      setAttrArgCaptor.attribute = attribute
      setAttrArgCaptor.value = value
    }
  }
}