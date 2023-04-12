/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_YES
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_YES
import com.android.tools.idea.layoutinspector.resource.KEYBOARDHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_QWERTY
import com.android.tools.idea.layoutinspector.resource.NAVIGATIONHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_WHEEL
import com.android.tools.idea.layoutinspector.resource.ORIENTATION_PORTRAIT
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LAYOUTDIR_RTL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LONG_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_ROUND_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_SMALL
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_STYLUS
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_NO
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_NORMAL
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.lang.String.join

class FromProtoConversionTest {
  private var stringIndex = 0
  private val table = mutableMapOf<String, Int>()

  @After
  fun cleanUp() {
    stringIndex = 0
    table.clear()
  }

  @Test
  fun testConvertConfiguration() {
    val proto = LayoutInspectorViewProtocol.Configuration.newBuilder().apply {
      countryCode = 310
      networkCode = 410
      screenLayout = SCREENLAYOUT_SIZE_SMALL or SCREENLAYOUT_LONG_YES or SCREENLAYOUT_LAYOUTDIR_RTL or SCREENLAYOUT_ROUND_YES
      colorMode = COLOR_MODE_WIDE_COLOR_GAMUT_YES or COLOR_MODE_HDR_YES
      touchScreen = TOUCHSCREEN_STYLUS
      keyboard = KEYBOARD_QWERTY
      keyboardHidden = KEYBOARDHIDDEN_NO
      hardKeyboardHidden = KEYBOARDHIDDEN_NO
      navigation = NAVIGATION_WHEEL
      navigationHidden = NAVIGATIONHIDDEN_NO
      uiMode = UI_MODE_TYPE_NORMAL or UI_MODE_NIGHT_NO
      smallestScreenWidthDp = 200
      density = 0
      orientation = ORIENTATION_PORTRAIT
      screenWidthDp = 480
      screenHeightDp = 800
      grammaticalGender = GRAMMATICAL_GENDER_FEMININE
    }.build()

    val folderConfiguration = proto.convert(29)

    assertThat(folderConfiguration.qualifierString).isEqualTo(join("-",
        "mcc310",
        "mnc410",
        "feminine",
        "ldrtl",
        "sw200dp",
        "w480dp",
        "h800dp",
        "small",
        "long",
        "round",
        "widecg",
        "highdr",
        "port",
        "notnight",
        "stylus",
        "keysexposed",
        "qwerty",
        "navexposed",
        "wheel",
        "v29"))
  }

  private fun id(str: String): Int = table[str] ?: addId(++stringIndex, str)

  private fun addId(id: Int, str: String): Int {
    table[str] = id
    return id
  }
}
