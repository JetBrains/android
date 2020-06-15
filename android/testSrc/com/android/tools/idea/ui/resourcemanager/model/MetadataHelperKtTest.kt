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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DENSITY
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DIMENSIONS_DP
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DIMENSIONS_PX
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_NAME
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_SIZE
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_TYPE
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class MetadataHelperKtTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  @Test
  fun getMetadata() {
    rule.fixture.testDataPath = getTestDataDirectory()
    val png = rule.fixture.copyFileToProject("res/drawable/png.png", "src/res/drawable-hdpi/png.png")
    // Adding Xml file to test project with a String to avoid a file size inconsistency caused by different line endings when running the
    // test in Windows & Windows continuous build.
    val vector = rule.fixture.addFileToProject(
      "src/res/drawable-anydpi/vector_drawable.xml",
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "        android:width=\"24dp\"\n" +
      "        android:height=\"24dp\"\n" +
      "        android:viewportWidth=\"24.0\"\n" +
      "        android:viewportHeight=\"24.0\"\n" +
      "        android:tint=\"?attr/colorControlNormal\">\n" +
      "    <path\n" +
      "        android:fillColor=\"@android:color/white\"\n" +
      "        android:pathData=\"M9,16.2L4.8,12l-1.4,1.4L9,19 21,7l-1.4,-1.4L9,16.2z\"/>\n" +
      "</vector>").virtualFile

    Truth.assertThat(png.getMetadata()).containsExactly(FILE_NAME, "png.png",
                                                        FILE_TYPE, "PNG",
                                                        DENSITY, "High Density",
                                                        FILE_SIZE, "114 B",
                                                        DIMENSIONS_PX, "32x32",
                                                        DIMENSIONS_DP, "21x21")

    Truth.assertThat(vector.getMetadata()).containsExactly(FILE_NAME, "vector_drawable.xml",
                                                           FILE_TYPE, "Vector drawable",
                                                           DENSITY, "Any Density",
                                                           FILE_SIZE, "399 B")
  }
}