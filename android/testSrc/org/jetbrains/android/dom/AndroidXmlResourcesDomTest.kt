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

package org.jetbrains.android.dom

import com.android.SdkConstants
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.util.ArrayUtil
import org.jetbrains.android.refactoring.setAndroidxProperties
import java.util.Arrays

/**
 * Tests for code editor features when working with resources under res/xml.
 */
class AndroidXmlResourcesDomTest : AndroidDomTestCase("dom/xml") {

  override fun providesCustomManifest(): Boolean {
    return true
  }

  override fun setUp() {
    super.setUp()
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML)
  }

  override fun getPathToCopy(testFileName: String): String {
    return "res/xml/$testFileName"
  }

  fun testSearchableRoot() {
    toTestCompletion("searchable_r.xml", "searchable_r_after.xml")
  }

  fun testSearchableAttributeName() {
    toTestCompletion("searchable_an.xml", "searchable_an_after.xml")
  }

  fun testSearchableAttributeValue() {
    doTestCompletionVariants("searchable_av.xml", "@string/welcome", "@string/welcome1")
  }

  fun testSearchableTagNameCompletion() {
    toTestCompletion("searchable_tn.xml", "searchable_tn_after.xml")
  }

  fun testKeyboard() {
    doTestHighlighting("keyboard.xml")
  }

  fun testKeyboard1() {
    toTestCompletion("keyboard1.xml", "keyboard1_after.xml")
  }

  fun testDeviceAdmin() {
    doTestHighlighting("deviceAdmin.xml")
  }

  fun testDeviceAdmin1() {
    toTestCompletion("deviceAdmin1.xml", "deviceAdmin1_after.xml")
  }

  fun testDeviceAdmin2() {
    toTestCompletion("deviceAdmin2.xml", "deviceAdmin2_after.xml")
  }

  fun testDeviceAdmin3() {
    toTestCompletion("deviceAdmin3.xml", "deviceAdmin3_after.xml")
  }

  fun testPoliciesCompletion() {
    doTestCompletionVariantsContains("deviceAdmin4.xml", "limit-password", "watch-login", "reset-password", "force-lock", "wipe-data",
                                     "set-global-proxy", "expire-password", "encrypted-storage", "disable-camera",
                                     "disable-keyguard-features")
  }

  fun testAccountAuthenticator() {
    toTestCompletion("accountAuthenticator.xml", "accountAuthenticator_after.xml")
  }

  fun testAccountAuthenticator1() {
    toTestCompletion("accountAuthenticator1.xml", "accountAuthenticator1_after.xml")
  }

  fun testAppwidgetProviderConfigure() {
    copyFileToProject("MyWidgetConfigurable.java", "src/p1/p2/MyWidgetConfigurable.java")
    doTestCompletion()
  }

  fun testHtmlAsXmlResource() {
    doTestHighlighting()
  }

  fun testCustomXmlFileHighlighting() {
    doTestHighlighting()
  }

  fun testContentUrlHighlighting() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=230194
    doTestHighlighting()
  }

  fun testCustomXmlFileCompletion2() {
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElements = myFixture.lookupElements
    assertNotNull(lookupElements)

    for (element in lookupElements!!) {
      if ("http://www.w3.org/1999/xhtml" == element.lookupString) {
        return
      }
    }
    fail(Arrays.asList(*lookupElements).toString())
  }

  fun testJavaCompletion1() {
    copyFileToProject("javaCompletion.xml")
    doTestJavaCompletion("p1.p2")
  }

  fun testPathsRootCompletion() {
    toTestCompletion("paths1.xml", "paths1_after.xml")
  }

  fun testPathsChildrenCompletion() {
    toTestCompletion("paths2.xml", "paths2_after.xml")
  }

  fun testPathHighlighting() {
    doTestHighlighting("paths3.xml")
  }
}

abstract class AndroidPreferenceXmlDomBase : AndroidDomTestCase("dom/xml") {

  override fun getPathToCopy(testFileName: String?): String {
    return "res/xml/$testFileName"
  }

  override fun providesCustomManifest() = true

  override fun setUp() {
    super.setUp()
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML)
  }


  fun testPreferenceRootCompletion() {
    toTestCompletion("pref1.xml", "pref1_after.xml")
  }

  fun testPreferenceGroupChildrenCompletion() {
    toTestCompletion("pref2.xml", "pref2_after.xml")
  }

  fun testPreferenceChildrenCompletion() {
    doTestCompletionVariants("pref10.xml", *ArrayUtil.EMPTY_STRING_ARRAY)
  }

  fun testPreferenceAttributeValueCompletion() {
    doTestCompletionVariants("pref5.xml", "@string/welcome", "@string/welcome1")
  }

  fun testPreferenceCompletion7() {
    toTestCompletion("pref7.xml", "pref7_after.xml")
  }

  fun testPreferenceCompletion8() {
    val file = copyFileToProject("pref8.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).isNotEmpty()
    assertThat(lookupElementStrings).contains("CheckBoxPreference")
    assertThat(lookupElementStrings).doesNotContain("android.preference.CheckBoxPreference")
  }

  fun testPreferenceHeaders() {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java")
    doTestHighlighting()
  }

  fun testPreferenceHeaders1() {
    doTestCompletion()
  }
}

class FrameworkPreferenceXmlDomTest : AndroidPreferenceXmlDomBase() {

  fun testPreferenceCompletion6() {
    val file = copyFileToProject("pref6.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).isNotNull()
    assertThat(lookupElementStrings).contains("PreferenceScreen")
    assertThat(lookupElementStrings).doesNotContain("android.preference.PreferenceScreen")
  }

  fun testPreferenceCompletion9() {
    val file = copyFileToProject("pref9.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).isNotEmpty()
    assertThat(lookupElementStrings).contains("preference.CheckBoxPreference")
  }

  fun testPreferenceAttributeNamesCompletion1() {
    doTestCompletionVariants("pref3.xml", "summary", "summaryOn", "summaryOff")
  }

  fun testPreferenceAttributeNamesCompletion2() {
    toTestCompletion("pref4.xml", "pref4_after.xml")
  }

  fun testCustomPreference1() {
    copyFileToProject("MyPreference.java", "src/p1/p2/MyPreference.java")
    toTestCompletion("customPref1.xml", "customPref1_after.xml")
  }

  fun testCustomPreference2() {
    copyFileToProject("MyPreference.java", "src/p1/p2/MyPreference.java")
    toTestCompletion("customPref2.xml", "customPref2_after.xml")
  }

  fun testReferenceUnqualifiedRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"></PreferenceScree<caret>n>""").virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.PreferenceScreen")
  }

  fun testReferenceQualifiedFrameworkRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |</android.preference.PreferenceScree<caret>n>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.PreferenceScreen")
  }

  fun testNestedUnqualifiedTagFramework() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |<CheckBoxP<caret>reference/>
        |</android.preference.PreferenceScreen>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.CheckBoxPreference")
  }
}

class AndroidXPropertySet : AndroidXPreferenceXmlDomTest() {
  override fun setUp() {
    super.setUp()
    runWriteCommandAction(project) { (myFixture.project).setAndroidxProperties("true") }
  }
}

class AndroidXPropertyNotSet : AndroidXPreferenceXmlDomTest()

abstract class AndroidXPreferenceXmlDomTest : AndroidPreferenceXmlDomBase() {

  override fun setUp() {
    super.setUp()
    // Simulate enough of the AndroidX library to write the tests, until we fix our fixtures to work against real AARs.
    myFixture.addClass("package androidx.preference; public class Preference {}")
    myFixture.addClass("package androidx.preference; public abstract class PreferenceGroup extends Preference {}")
    myFixture.addClass("package androidx.preference; public class PreferenceScreen extends PreferenceGroup {}")
    myFixture.addClass("package androidx.preference; public class CheckBoxPreference extends Preference {}")
    myFixture.addClass("package androidx.preference; public class PreferenceCategory extends PreferenceGroup {}")

    myFixture.addFileToProject(
      "res/values/styleables.xml",
      // language=XML
      """
      <resources>
      <declare-styleable name='Preference'>
        <attr name='onlyAndroidx' format='string' />
        <attr name='key' format='string' />
        <attr name='android:key' />
      </declare-styleable>
      </resources>
      """
    )
  }

  fun testPreferenceGroupChildrenCompletion_androidx() {
    val file = copyFileToProject("pref2_androidx.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).contains("Preference")
    assertThat(lookupElementStrings).contains("PreferenceScreen")
    assertThat(lookupElementStrings).doesNotContain("androidx.preference.PreferenceGroup")
    assertThat(lookupElementStrings).doesNotContain("PreferenceGroup")
  }


  fun testPreferenceAttributeNamesCompletion_androidX() {
    val file = copyFileToProject("pref3_androidx.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).contains("app:key")
    assertThat(lookupElementStrings).contains("android:key")
    assertThat(lookupElementStrings).contains("app:onlyAndroidx")
  }

  fun testPreferenceCompletion6() {
    val file = copyFileToProject("pref6.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).contains("PreferenceScreen")
    assertThat(lookupElementStrings).contains("CheckBoxPreference")
    assertThat(lookupElementStrings).doesNotContain("RingtonePreference")
    assertThat(lookupElementStrings).doesNotContain("android.preference.PreferenceScreen")
    assertThat(lookupElementStrings).doesNotContain("androidx.preference.PreferenceScreen")
  }

  fun testPreferenceCompletion9() {
    val file = copyFileToProject("pref9_androidx.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).isNotEmpty()
    assertThat(lookupElementStrings).contains("preference.CheckBoxPreference")
  }

  fun testCustomPreference1() {
    copyFileToProject("MyPreferenceAndroidX.java", "src/p1/p2/MyPreference.java")
    toTestCompletion("customPref1.xml", "customPref1_after.xml")
  }

  fun testCustomPreference2() {
    copyFileToProject("MyPreferenceAndroidX.java", "src/p1/p2/MyPreference.java")
    toTestCompletion("customPref2.xml", "customPref2_after.xml")
  }

  fun testReferenceUnqualifiedRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"></PreferenceScree<caret>n>""").virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("androidx.preference.PreferenceScreen")
  }

  fun testReferenceQualifiedAndroidXRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<androidx.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |</androidx.preference.PreferenceScree<caret>n>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("androidx.preference.PreferenceScreen")
  }

  fun testReferenceQualifiedFrameworkRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |</android.preference.PreferenceScree<caret>n>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.PreferenceScreen")
  }

  fun testNestedUnqualifiedTagAndroidX() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<androidx.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |<CheckBoxP<caret>reference/>
        |</androidx.preference.PreferenceScreen>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("androidx.preference.CheckBoxPreference")
  }

  fun testNestedUnqualifiedTagFramework() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |<CheckBoxP<caret>reference/>
        |</android.preference.PreferenceScreen>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.CheckBoxPreference")
  }
}

class SupportLibraryPreferenceDomTest : AndroidPreferenceXmlDomBase() {

  override fun setUp() {
    super.setUp()
    // Simulate enough of the Android Support library to write the tests.
    myFixture.addClass("package android.support.v7.preference; public class Preference {}")
    myFixture.addClass("package android.support.v7.preference; public abstract class PreferenceGroup extends Preference {}")
    myFixture.addClass("package android.support.v7.preference; public class PreferenceScreen extends PreferenceGroup {}")
    myFixture.addClass("package android.support.v7.preference; public class CheckBoxPreference extends Preference {}")
    myFixture.addClass("package android.support.v7.preference; public class PreferenceCategory extends PreferenceGroup {}")
    myFixture.addClass(
      "package android.support.v14.preference; public class SwitchPreference extends android.support.v7.preference.Preference {}")
  }

  fun testReferenceUnqualifiedRootv7() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"></PreferenceScree<caret>n>""").virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.support.v7.preference.PreferenceScreen")
  }

  fun testReferenceQualifiedSupportLibraryRootv7() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.support.v7.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |</android.support.v7.preference.PreferenceScree<caret>n>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.support.v7.preference.PreferenceScreen")
  }

  fun testReferenceUnqualifiedRootv14() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<SwitchPreference xmlns:android="http://schemas.android.com/apk/res/android"></SwitchPreferenc<caret>e>""").virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.support.v14.preference.SwitchPreference")
  }

  fun testReferenceQualifiedSupportLibraryRootv14() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.support.v14.preference.SwitchPreference xmlns:android="http://schemas.android.com/apk/res/android">
        |</android.support.v14.preference.SwitchPreferenc<caret>e>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.support.v14.preference.SwitchPreference")
  }

  fun testReferenceQualifiedFrameworkRoot() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |</android.preference.PreferenceScree<caret>n>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.PreferenceScreen")
  }

  fun testNestedUnqualifiedTagSupportLibrary() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.support.v7.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |<CheckBoxP<caret>reference/>
        |</android.support.v7.preference.PreferenceScreen>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.support.v7.preference.CheckBoxPreference")
  }

  fun testNestedUnqualifiedTagFramework() {
    val file = myFixture.addFileToProject(
      "res/xml/preference.xml",
      """<android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        |<CheckBoxP<caret>reference/>
        |</android.preference.PreferenceScreen>""".trimMargin()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat((myFixture.elementAtCaret as PsiClass).qualifiedName).isEqualTo("android.preference.CheckBoxPreference")
  }
}