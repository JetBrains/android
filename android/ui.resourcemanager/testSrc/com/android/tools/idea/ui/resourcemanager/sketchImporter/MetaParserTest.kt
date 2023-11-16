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
package com.android.tools.idea.ui.resourcemanager.sketchImporter

import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import kotlin.test.assertEquals

class MetaParserTest {
  @Test
  fun checkVersionNew() {
    val meta: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.meta.SketchMeta = SketchTestUtils.parseMeta(
      AndroidTestBase.getTestDataPath() + "/sketch/meta51.json")

    assertEquals(meta.appVersion, 51.2)
  }

  @Test
  fun checkVersionOld() {
    val meta: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.meta.SketchMeta = SketchTestUtils.parseMeta(
      AndroidTestBase.getTestDataPath() + "/sketch/meta43.json")

    assertEquals(meta.appVersion, 43.0)
  }
}