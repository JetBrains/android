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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.BUTTON
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Dependencies
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.EnumValueUtil.checkSection
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

private const val PROJECT_STYLES =
  """
<resources>
    <style name="MyButtonStyle" parent="Widget.AppCompat.Button"/>
    <style name="MyButtonStyle.Blue"/>
</resources>"""

class StyleEnumSupportTest {

  @JvmField @Rule val myProjectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @JvmField @Rule val myEdtRule = EdtRule()

  @RunsInEdt
  @Test
  fun testButtonStyles() {
    Dependencies.add(myProjectRule.fixture, APPCOMPAT_LIB_ARTIFACT_ID)
    myProjectRule.fixture.addFileToProject("res/values/project_styles.xml", PROJECT_STYLES)
    val util = SupportTestUtil(myProjectRule, BUTTON)
    val property = util.makeProperty("", ATTR_STYLE, NlPropertyType.STYLE)
    val support = StyleEnumSupport(property)

    val values = support.values
    val expectedProjectValues = listOf("@style/MyButtonStyle", "@style/MyButtonStyle.Blue")
    val expectedProjectDisplayValues = listOf("MyButtonStyle", "MyButtonStyle.Blue")
    val expectedAppCompatValues =
      listOf(
        "@style/Widget.AppCompat.Button",
        "@style/Widget.AppCompat.Button.Borderless",
        "@style/Widget.AppCompat.Button.Borderless.Colored",
        "@style/Widget.AppCompat.Button.ButtonBar.AlertDialog",
        "@style/Widget.AppCompat.Button.Colored",
        "@style/Widget.AppCompat.Button.Small",
      )
    val expectedAppCompatDisplayValues =
      listOf(
        "Widget.AppCompat.Button",
        "Widget.AppCompat.Button.Borderless",
        "Widget.AppCompat.Button.Borderless.Colored",
        "Widget.AppCompat.Button.ButtonBar.AlertDialog",
        "Widget.AppCompat.Button.Colored",
        "Widget.AppCompat.Button.Small",
      )
    val expectedAndroidValues =
      listOf(
        "@android:style/Widget.Button",
        "@android:style/Widget.Button.Inset",
        "@android:style/Widget.Button.Small",
        "@android:style/Widget.Button.Toggle",
      )
    val expectedAndroidDisplayValues =
      listOf("Widget.Button", "Widget.Button.Inset", "Widget.Button.Small", "Widget.Button.Toggle")
    var index = 0
    index =
      checkSection(
        values,
        index,
        PROJECT_HEADER,
        3,
        expectedProjectValues,
        expectedProjectDisplayValues,
      )
    index =
      checkSection(
        values,
        index,
        APPCOMPAT_HEADER,
        7,
        expectedAppCompatValues,
        expectedAppCompatDisplayValues,
      )
    index =
      checkSection(
        values,
        index,
        ANDROID_HEADER,
        -40,
        expectedAndroidValues,
        expectedAndroidDisplayValues,
      )
    assertThat(index).isEqualTo(-1)
  }
}
