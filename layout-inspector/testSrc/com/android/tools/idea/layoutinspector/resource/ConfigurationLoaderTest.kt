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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.pipeline.transport.StringTableImpl
import com.android.tools.idea.layoutinspector.resource.data.Configuration
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.StringEntry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.lang.String.join

private const val APP_PACKAGE = "com.example"
private const val APP_THEME = "AppTheme"

class ConfigurationLoaderTest {
  private var stringIndex = 0
  private val table = mutableMapOf<String, Int>()

  @After
  fun cleanUp() {
    stringIndex = 0
    table.clear()
  }

  @Test
  fun testConfigurationLoader() {
    val appContext = AppContext(
      theme = reference(ResourceType.STYLE, APP_PACKAGE, APP_THEME),
      configuration = Configuration(
        fontScale = 1.0f,
        countryCode = 310,
        networkCode = 410,
        screenLayout = SCREENLAYOUT_SIZE_SMALL or SCREENLAYOUT_LONG_YES or SCREENLAYOUT_LAYOUTDIR_RTL or SCREENLAYOUT_ROUND_YES,
        colorMode = COLOR_MODE_WIDE_COLOR_GAMUT_YES or COLOR_MODE_HDR_YES,
        touchScreen = TOUCHSCREEN_STYLUS,
        keyboard = KEYBOARD_QWERTY,
        keyboardHidden = KEYBOARDHIDDEN_NO,
        hardKeyboardHidden = KEYBOARDHIDDEN_NO,
        navigation = NAVIGATION_WHEEL,
        navigationHidden = NAVIGATIONHIDDEN_NO,
        uiMode = UI_MODE_TYPE_NORMAL or UI_MODE_NIGHT_NO,
        smallestScreenWidth = 200,
        density = 0,
        orientation = ORIENTATION_PORTRAIT,
        screenWidth = 480,
        screenHeight = 800
      )
    )
    val table = stringTable()
    val loader = ConfigurationLoader(appContext, table, AndroidVersion.VersionCodes.Q)
    assertThat(loader.theme).isEquivalentAccordingToCompareTo(
      ResourceReference(ResourceNamespace.fromPackageName(APP_PACKAGE), ResourceType.STYLE, APP_THEME))
    assertThat(loader.folderConfiguration.qualifierString).isEqualTo(join("-",
        "mcc310",
        "mnc410",
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

  private fun reference(type: ResourceType, namespace: String, name: String): Resource =
    Resource(id(type.getName()), id(namespace), id(name))

  private fun id(str: String): Int = table[str] ?: addId(++stringIndex, str)

  private fun addId(id: Int, str: String): Int {
    table[str] = id
    return id
  }

  private fun stringTable(): StringTable =
    StringTableImpl(table.entries.map { StringEntry.newBuilder().setId(it.value).setStr(it.key).build() })
}
