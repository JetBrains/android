/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.android.testutils.TestUtils
import com.intellij.util.xml.NanoXmlUtil
import junit.framework.Assert
import net.n3.nanoxml.IXMLBuilder
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ProjectResourceIdResolverTest {

  /**
   * Tests that we correctly can read the platform public.xml
   */
  @Test
  @Throws(Exception::class)
  fun testPlatformResourceIdMap() {
    val builder = ProjectResourceIdResolver.MyPublicResourceIdMapBuilder()
    parseAndClose(Files.newInputStream(TestUtils.resolvePlatformPath("data/res/values/public-final.xml")), builder)
    val map = builder.idMap
    Assert.assertEquals("@android:transition/move", map[0x010f0001])
    Assert.assertEquals("@android:id/widget_frame", map[0x01020018])
    Assert.assertEquals("@android:attr/colorSecondary", map[0x01010530])
    Assert.assertNull(map[0])
  }

  @Test
  @Throws(IOException::class)
  fun testPublicGroupParsing() {
    val publicXmlContent = """<?xml version="1.0" encoding="utf-8"?><!-- This file defines the base public resources exported by the
     platform, which must always exist. --><resources>  <eat-comment />

  <public type="attr" name="theme" id="0x01010000" />
  <public type="attr" name="label" id="0x01010001" />
  <public type="attr" name="manageSpaceActivity" id="0x01010004" />

  <public-group type="attr" first-id="0x01010531">        <public name="fontStyle" />
        <public name="font" />
        <public name="fontWeight" />
        <public name="tooltipText" />
        <public name="autoSizeText" />
  </public-group>
  <public type="drawable" name="btn_minus" id="0x01080007" />
  <public type="drawable" name="btn_plus" id="0x01080008" />  <public type="attr" name="titleMargin" id="0x010104f8" />
  <public type="attr" name="titleMarginStart" id="0x010104f9" />
  <public-group type="id" first-id="0x01020041">
     <public name="textAssist" />
  </public-group></resources>"""
    val builder = ProjectResourceIdResolver.MyPublicResourceIdMapBuilder()
    parseAndClose(ByteArrayInputStream(publicXmlContent.toByteArray(StandardCharsets.UTF_8)), builder)
    val map = builder.idMap

    // Check that we handle correctly elements before and after a public-group
    Assert.assertEquals("@android:attr/theme", map[0x01010000])
    Assert.assertEquals("@android:drawable/btn_minus", map[0x01080007])
    Assert.assertEquals("@android:attr/titleMargin", map[0x010104f8])

    // Check the public group elements
    Assert.assertEquals("@android:attr/fontStyle", map[0x01010531])
    Assert.assertEquals("@android:attr/autoSizeText", map[0x01010531 + 4])
    Assert.assertEquals("@android:id/textAssist", map[0x01020041])
  }
}

@Throws(IOException::class)
private fun parseAndClose(stream: InputStream, builder: IXMLBuilder) {
  stream.use { NanoXmlUtil.parse(stream, builder) }
}