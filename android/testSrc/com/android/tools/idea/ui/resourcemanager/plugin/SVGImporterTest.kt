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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class SVGImporterTest {
  @get:Rule
  var rule = AndroidProjectRule.inMemory()

  @Test
  fun processFiles() {
    val svg1 = """
      |<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
      |  <path d="M0 0h24v24H0z" fill="none"/>
      |  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
      |</svg>
      """.trimMargin()

    val svg2 = """
      |<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
      |  <path clip-rule="evenodd" fill="none" d="M0 0h24v24H0z"/>
      |  <path d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"/>
      |</svg>
      """.trimMargin()

    val file1 = File.createTempFile("test1", "svg").also {
      it.outputStream().write(svg1.toByteArray())
    }
    val file2 = File.createTempFile("test2", "svg").also {
      it.outputStream().write(svg2.toByteArray())
    }


    val svgImporter = SVGImporter()
    val vd1 = svgImporter.processFile(file1)!!
    val vd2 = svgImporter.processFile(file2)!!
    val content1 = vd1.file.contentsToByteArray()
    val content2 = vd2.file.contentsToByteArray()

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:width=\"24dp\"\n" +
                 "    android:height=\"24dp\"\n" +
                 "    android:viewportWidth=\"24\"\n" +
                 "    android:viewportHeight=\"24\">\n" +
                 "  <path\n" +
                 "      android:fillColor=\"#FF000000\"\n" +
                 "      android:pathData=\"M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z\"/>\n" +
                 "</vector>\n", String(content1))

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:width=\"24dp\"\n" +
                 "    android:height=\"24dp\"\n" +
                 "    android:viewportWidth=\"24\"\n" +
                 "    android:viewportHeight=\"24\">\n" +
                 "  <path\n" +
                 "      android:fillColor=\"#FF000000\"\n" +
                 "      android:pathData=\"M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z\"/>\n" +
                 "</vector>\n", String(content2))
  }
}