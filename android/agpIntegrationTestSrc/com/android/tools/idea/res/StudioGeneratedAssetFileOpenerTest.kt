/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import junit.framework.TestCase
import org.jetbrains.android.facet.AndroidFacet
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class StudioGeneratedAssetFileOpenerTest : AndroidGradleTestCase() {
  private var myAppRepo: StudioAssetFileOpener? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    loadProject(TestProjectPaths.SIMPLE_APPLICATION_GENERATED_ASSETS)
    val invocationResult = invokeGradle(project) {
      it.assemble()
    }
    TestCase.assertTrue(invocationResult.isBuildSuccessful)

    val facet: AndroidFacet = AndroidFacet.getInstance(getModule("app").getMainModule())!!

    TestCase.assertNotNull(facet)
    myAppRepo = StudioAssetFileOpener(facet)
  }

  @Throws(IOException::class)
  fun testGeneratedAsset() {
    val appGenerated = "generated text"
    BufferedReader(InputStreamReader(myAppRepo!!.openAssetFile("generated.asset.txt"))).use { br ->
      val assetContent = br.readLine()
      assertEquals(appGenerated, assetContent)
    }
  }
}