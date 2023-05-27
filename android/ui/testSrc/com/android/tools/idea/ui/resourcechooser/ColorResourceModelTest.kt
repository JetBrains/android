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
package com.android.tools.idea.ui.resourcechooser

import com.android.tools.idea.configurations.ConfigurationManager
import org.jetbrains.android.AndroidTestCase

class ColorResourceModelTest: AndroidTestCase() {

  fun testModelResource() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    myFixture.copyFileToProject("xmlpull/color.xml", "res/values/color.xml")

    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val configuration = manager.getConfiguration(layoutFile)
    val model = ColorResourceModel(configuration)

    run {
      val resources = model.getResourceReference(ColorResourceModel.Category.PROJECT, "").toMutableList()
      resources.sortBy { it.resourceUrl.toString() }

      // The order of resources is not important. We sort them to make test consistence.
      assertSize(3, resources)
      assertEquals("@color/color1", resources[0].resourceUrl.toString())
      assertEquals("@color/color2", resources[1].resourceUrl.toString())
      assertEquals("@color/color3", resources[2].resourceUrl.toString())
    }

    // Test filter
    run {
      val resources = model.getResourceReference(ColorResourceModel.Category.PROJECT, "2").toMutableList()
      resources.sortBy { it.resourceUrl.toString() }
      assertSize(1, resources)
      assertEquals("@color/color2", resources[0].resourceUrl.toString())
    }
  }
}
