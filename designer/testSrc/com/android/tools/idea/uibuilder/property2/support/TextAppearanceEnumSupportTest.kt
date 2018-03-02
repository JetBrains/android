/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.EnumValueUtil
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth
import org.jetbrains.android.AndroidTestCase

private const val APPCOMPAT_TEXT_APPEARANCES = """
<resources>
    <style name="Base.TextAppearance.AppCompat" parent="android:TextAppearance"/>
    <style name="TextAppearance.AppCompat" parent="Base.TextAppearance.AppCompat"/>
    <style name="TextAppearance.AppCompat.Small"/>
    <style name="TextAppearance.AppCompat.Medium"/>
    <style name="TextAppearance.AppCompat.Large"/>
    <style name="TextAppearance.AppCompat.Body1"/>
    <style name="TextAppearance.AppCompat.Body2"/>
    <style name="TextAppearance.AppCompat.Display1"/>
    <style name="TextAppearance.AppCompat.Display2"/>
    <style name="TextAppearance.AppCompat.Display3"/>
    <style name="TextAppearance.AppCompat.Display4"/>
</resources>"""

private const val PROJECT_TEXT_APPEARANCES = """
<resources>
    <style name="MyTextStyle" parent="android:TextAppearance"/>
    <style name="TextAppearance.Blue" parent="MyTextStyle"/>
</resources>"""

class TextAppearanceEnumSupportTest: AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("res/values/project_styles.xml", PROJECT_TEXT_APPEARANCES)
  }

  fun testTextViewTextAppearanceWithAppCompat() {
    // setup
    myFixture.addFileToProject("res/values/appcompat_styles.xml", APPCOMPAT_TEXT_APPEARANCES)
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE)
    EnumValueUtil.patchLibraryNameOfAllAppCompatStyles(property)

    // test
    val support = TextAppearanceEnumSupport(property)
    val values = support.values
    val expectedProjectValues = listOf("@style/TextAppearance.Blue", "@style/MyTextStyle")
    val expectedProjectDisplayValues = listOf("Blue", "MyTextStyle")
    val expectedAppCompatValues = listOf(
        "@style/TextAppearance.AppCompat.Body1",
        "@style/TextAppearance.AppCompat.Body2",
        "@style/TextAppearance.AppCompat.Display1",
        "@style/TextAppearance.AppCompat.Display2",
        "@style/TextAppearance.AppCompat.Display3",
        "@style/TextAppearance.AppCompat.Display4",
        "@style/TextAppearance.AppCompat.Large",
        "@style/TextAppearance.AppCompat.Medium",
        "@style/TextAppearance.AppCompat.Small")
    val expectedAppCompatDisplayValues = listOf(
        "Body1",
        "Body2",
        "Display1",
        "Display2",
        "Display3",
        "Display4",
        "Large",
        "Medium",
        "Small")
    var index = 0
    index = EnumValueUtil.checkSection(values, index, PROJECT_HEADER, 2, expectedProjectValues, expectedProjectDisplayValues)
    index = EnumValueUtil.checkSection(values, index, APPCOMPAT_HEADER, 9, expectedAppCompatValues, expectedAppCompatDisplayValues)
    Truth.assertThat(index).isEqualTo(-1)
  }

  fun testTextViewTextAppearanceWithoutAppCompat() {
    // setup
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE)

    // test
    val support = TextAppearanceEnumSupport(property)
    val values = support.values
    val expectedProjectValues = listOf("@style/TextAppearance.Blue", "@style/MyTextStyle")
    val expectedProjectDisplayValues = listOf("Blue", "MyTextStyle")
    val expectedAndroidValues = listOf(
        "@android:style/TextAppearance.Material.Body1",
        "@android:style/TextAppearance.Material.Body2",
        "@android:style/TextAppearance.Material.Display1",
        "@android:style/TextAppearance.Material.Display2",
        "@android:style/TextAppearance.Material.Display3",
        "@android:style/TextAppearance.Material.Display4",
        "@android:style/TextAppearance.Material.Large",
        "@android:style/TextAppearance.Material.Medium",
        "@android:style/TextAppearance.Material.Small")
    val expectedAndroidDisplayValues = listOf(
        "Body1",
        "Body2",
        "Display1",
        "Display2",
        "Display3",
        "Display4",
        "Large",
        "Medium",
        "Small")
    var index = 0
    index = EnumValueUtil.checkSection(values, index, PROJECT_HEADER, 2, expectedProjectValues, expectedProjectDisplayValues)
    index = EnumValueUtil.checkSection(values, index, ANDROID_HEADER, 9, expectedAndroidValues, expectedAndroidDisplayValues)
    Truth.assertThat(index).isEqualTo(-1)
  }
}
