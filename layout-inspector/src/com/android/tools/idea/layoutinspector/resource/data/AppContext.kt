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
package com.android.tools.idea.layoutinspector.resource.data

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTable
import java.awt.Dimension

/**
 * Misc. context about the current running app.
 *
 * @param mainDisplayOrientation The orientation of the device main display in degrees.
 */
class AppContext(
  val theme: Resource? = null,
  val screenSize: Dimension? = null,
  val mainDisplayOrientation: Int,
  val themeString: String = ""
) {
  fun createThemeReference(
    stringTable: StringTable,
    projectPackageName: String
  ): ResourceReference? =
    if (themeString.isEmpty()) theme?.createReference(stringTable)
    else createReference(themeString, projectPackageName)
}
