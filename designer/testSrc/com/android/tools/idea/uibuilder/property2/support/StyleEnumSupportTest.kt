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

import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.BUTTON
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.EnumValueUtil.checkSection
import com.android.tools.idea.uibuilder.property2.testutils.EnumValueUtil.patchLibraryNameOfAllAppCompatStyles
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

private const val APPCOMPAT_STYLES = """
<resources>
    <style name="Base.Widget.AppCompat.Button" parent="android:Widget.Material.Button"/>
    <style name="Widget.AppCompat.Button" parent="Base.Widget.AppCompat.Button"/>
    <style name="Widget.AppCompat.Button.Colored"/>
</resources>"""

private const val PROJECT_STYLES = """
<resources>
    <style name="MyButtonStyle" parent="Widget.AppCompat.Button"/>
    <style name="MyButtonStyle.Blue"/>
</resources>"""

class StyleEnumSupportTest: AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("res/values/appcompat_styles.xml", APPCOMPAT_STYLES)
    myFixture.addFileToProject("res/values/project_styles.xml", PROJECT_STYLES)
  }

  fun testButtonStyles() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, BUTTON)
    val property = util.makeProperty("", ATTR_STYLE, NelePropertyType.STYLE)
    patchLibraryNameOfAllAppCompatStyles(property)
    val support = StyleEnumSupport(property)

    val values = support.values
    val expectedProjectValues = listOf("@style/MyButtonStyle", "@style/MyButtonStyle.Blue")
    val expectedProjectDisplayValues = listOf("MyButtonStyle", "MyButtonStyle.Blue")
    val expectedAppCompatValues = listOf("@style/Widget.AppCompat.Button", "@style/Widget.AppCompat.Button.Colored")
    val expectedAppCompatDisplayValues = listOf("Widget.AppCompat.Button", "Widget.AppCompat.Button.Colored")
    val expectedAndroidValues = listOf(
        "@android:style/Widget.Button",
        "@android:style/Widget.Button.Inset",
        "@android:style/Widget.Button.Small",
        "@android:style/Widget.Button.Toggle")
    val expectedAndroidDisplayValues = listOf(
        "Widget.Button",
        "Widget.Button.Inset",
        "Widget.Button.Small",
        "Widget.Button.Toggle")
    var index = 0
    index = checkSection(values, index, PROJECT_HEADER, 2, expectedProjectValues, expectedProjectDisplayValues)
    index = checkSection(values, index, APPCOMPAT_HEADER, 2, expectedAppCompatValues, expectedAppCompatDisplayValues)
    index = checkSection(values, index, ANDROID_HEADER, -40, expectedAndroidValues, expectedAndroidDisplayValues)
    assertThat(index).isEqualTo(-1)
  }
}
