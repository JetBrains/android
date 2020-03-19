/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.repository.testframework.MockFileOp
import com.android.tools.idea.FakeSdkRule
import com.android.tools.idea.testing.AndroidProjectRule
import junit.framework.Assert.assertEquals
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class SkiaParserTest {

  @Test
  fun testGetSkpVersion() {
    val version = SkiaParser.getSkpVersion("skiapict".toByteArray().plus(byteArrayOf(10, 0, 1, 0)).plus("blah".toByteArray()))
    assertEquals(65546, version)
  }

  @Test
  fun testInvalidSkp() {
    try {
      SkiaParser.getViewTree("foobarbaz".toByteArray())
      fail()
    }
    catch (expected: InvalidPictureException) {}
  }
}

class SkiaParserTest2 {
  val projectRule = AndroidProjectRule.inMemory()
  val fakeSdkRule = FakeSdkRule(projectRule)
    .withLocalPackage("skiaparser;1")

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fakeSdkRule)

  @Test
  fun testFindServerInfoForSkpVersion() {
    val fileOp = fakeSdkRule.fileOp as MockFileOp
    fileOp.recordExistingFile(File(fakeSdkRule.sdkPath, "skiaparser/1/version-map.xml").path, """
      <?xml version="1.0" encoding="utf-8"?>
      <versionMapping>
        <server version="1" skpStart="1" skpEnd="10"/>
        <server version="2" skpStart="11" skpEnd="15"/>
        <server version="3" skpStart="16" skpEnd="20"/>
      </versionMapping>
    """.trimIndent())

    val info: ServerInfo = SkiaParser.findServerInfoForSkpVersion(13)!!
    assertEquals(2, info.serverVersion)
  }
}