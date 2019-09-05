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
package org.jetbrains.android.refactoring

import com.android.SdkConstants
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for custom [UsageTypeProvider]s from UsageTypeProviders.kt
 */
class UsageTypeProvidersTest : AndroidTestCase() {

  override fun providesCustomManifest(): Boolean {
    return true
  }

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      // language=xml
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="p1.p2">
          <uses-permission android:name="android.permission.SEND_${caret}SMS"/>
          <application android:icon="@drawable/icon">
          </application>
      </manifest>
      """.trimIndent()
    )
  }

  /**
   * Tests for [AndroidResourceReferenceInCodeUsageTypeProvider]
   */
  fun testAndroidLightFieldResource() {
    myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """<resources><color name="colorPrimary">#008577</color></resources>"""
    )
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.kt",
      //language=kotlin
      """
       package p1.p2
       class Foo {
         fun example() {
           R.color.color${caret}Primary
         }
       }
       """.trimIndent())
    checkUsageTypeText(file.virtualFile, "Resource reference in code")
  }

  fun testClsFieldImplManifest() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.kt",
      //language=kotlin
      """
       package p1.p2
       import android.Manifest
       class Foo {
         fun example() {
           Manifest.permission.ACCESS_CHECKIN_P${caret}ROPERTIES
         }
       }
       """.trimIndent())
    checkUsageTypeText(file.virtualFile, "Permission reference in code")
  }

  fun testClsFieldImplResource() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Foo.kt",
      //language=kotlin
      """
       package p1.p2
       class Foo {
         fun example() {
           android.R.color.bl${caret}ack
         }
       }
       """.trimIndent())
    checkUsageTypeText(file.virtualFile, "Resource reference in code")
  }

  /**
   * Test for [GradleUsageTypeProvider]
   */
  fun testGroovyElement() {
    val file = myFixture.addFileToProject("Foo.gradle", "class F${caret}oo {}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)
    val usageType = getUsageType(elementAtCaret!!)
    assertThat(usageType).isNotNull()
    assertThat(usageType.toString()).isEqualTo("{0} in Gradle build script")
  }

  /**
   * Tests for [AndroidDomUsageTypeProvider]
   */
  fun testResourceDomElement() {
    myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:backgroundTint="@color/colorPrimary">
        </LinearLayout>
      """.trimIndent())
    val colorsFile = myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """<resources><color name="color${caret}Primary">#008577</color></resources>""")
    checkUsageTypeText(colorsFile.virtualFile, "{0} in Android resources XML")
  }

  fun testManifestDomElement() {
    val manifestFile = myFixture.findFileInTempDir(SdkConstants.FN_ANDROID_MANIFEST_XML)
    checkUsageTypeText(manifestFile, "{0} in Android manifest")
  }

  /**
   * Test for no custom [UsageTypeProvider]
   */
  fun testDefaultMethod() {
    val file = myFixture.addFileToProject(
      "/src/p1/p2/Utils.java",
      //language=Java
      """
       package p1.p2;
       public class Utils {
         public static void testNothing() {
          callTestNothing();
         }
         public static void call${caret}TestNothing() {}
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val usageInfo = findUsages().toList()
    val usageType = getUsageType(usageInfo[0].element!!)
    assertThat(usageType).isNull()
  }

  private fun findUsages(): Collection<UsageInfo> {
    val targets = UsageTargetUtil.findUsageTargets { dataId -> (myFixture.editor as EditorEx).dataContext.getData(dataId) }
    assert(targets != null && targets.isNotEmpty() && targets[0] is PsiElementUsageTarget)
    return myFixture.findUsages((targets!![0] as PsiElementUsageTarget).element)
  }

  private fun getUsageType(element: PsiElement) : UsageType? {
    for (provider in UsageTypeProvider.EP_NAME.extensionList) {
      return provider.getUsageType(element) ?: continue
    }
    return null
  }

  private fun checkUsageTypeText(virtualFile: VirtualFile, usageText: String) {
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val usageInfo = findUsages().toList()
    val usageType = getUsageType(usageInfo[0].element!!)
    assertThat(usageType).isNotNull()
    assertThat(usageType.toString()).isEqualTo(usageText)
  }
}