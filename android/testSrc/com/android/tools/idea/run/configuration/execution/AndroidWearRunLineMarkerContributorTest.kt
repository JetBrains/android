/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.idea.run.configuration.AndroidWearRunMarkerContributor
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

class AndroidWearRunLineMarkerContributorTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.addWearDependenciesToProject()
  }

  override fun tearDown() {
    super.tearDown()
  }

  @Test
  fun testGetWatchFaceInfo() {
    val watchFaceFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyTestWatchFace.kt",
      """
      package com.example.myapplication

      import android.support.wearable.watchface.WatchFaceService

      /**
       * Some comment
       */
      class MyTestWatchFace : WatchFaceService() {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getInfo(watchFaceFile.findElementByText("class")))
    assertNull(contributor.getInfo(watchFaceFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  fun testGetTileInfo() {
    val tileFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyTileService.kt",
      """
      package com.example.myapplication

      import androidx.wear.tiles.TileService

      class MyTestTile : TileService() {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getInfo(tileFile.findElementByText("class")))
    assertNull(contributor.getInfo(tileFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  fun testGetComplicationInfo() {
    val complicationFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.kt",
      """
      package com.example.myapplication

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

      class MyTestComplication : ComplicationDataSourceService() {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getInfo(complicationFile.findElementByText("class")))
    assertNull(contributor.getInfo(complicationFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  fun testGetComplicationInfoJava() {
    val complicationFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.java",
      """
      package com.example.myapplication;

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
        
      public class MyComplicationService extends ComplicationDataSourceService {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getInfo(complicationFile.findElementByText("class")))
    assertNull(contributor.getInfo(complicationFile.findElementByText("package com.example.myapplication;")))
  }
}