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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidWearRunLineMarkerContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {

    projectRule.fixture.addWearDependenciesToProject()
  }


  @Test
  @RunsInEdt
  fun testGetWatchFaceInfo() {
    val watchFaceFile = projectRule.fixture.addFileToProject(
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
    assertNotNull(contributor.getSlowInfo(watchFaceFile.findElementByText("class")))
    assertNull(contributor.getSlowInfo(watchFaceFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  @RunsInEdt
  fun testGetTileInfo() {
    val tileFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyTileService.kt",
      """
      package com.example.myapplication

      import androidx.wear.tiles.TileService

      class MyTestTile : TileService() {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getSlowInfo(tileFile.findElementByText("class")))
    assertNull(contributor.getSlowInfo(tileFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  @RunsInEdt
  fun testGetComplicationInfo() {
    val complicationFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.kt",
      """
      package com.example.myapplication

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

      class MyTestComplication : ComplicationDataSourceService() {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getSlowInfo(complicationFile.findElementByText("class")))
    assertNull(contributor.getSlowInfo(complicationFile.findElementByText("package com.example.myapplication")))
  }

  @Test
  @RunsInEdt
  fun testGetComplicationInfoJava() {
    val complicationFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.java",
      """
      package com.example.myapplication;

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
        
      public class MyComplicationService extends ComplicationDataSourceService {
      }
      """.trimIndent())

    val contributor = AndroidWearRunMarkerContributor()
    assertNotNull(contributor.getSlowInfo(complicationFile.findElementByText("class")))
    assertNull(contributor.getSlowInfo(complicationFile.findElementByText("package com.example.myapplication;")))
  }
}