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
package com.android.tools.res;

import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.resources.ProtoXmlPullParser;
import com.android.ide.common.util.PathString;
import com.android.resources.AarTestUtils;
import com.android.testutils.TestUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

/**
 * Tests for {@link FileResourceReader}.
 */
public class FileResourceReaderTest {
  @Test
  public void testReadBytes() throws Exception {
    Path resApkPath = TestUtils.resolveWorkspacePath(AarTestUtils.TEST_DATA_DIR + "/design_aar/" + FN_RESOURCE_STATIC_LIBRARY);
    String resourcePath = resApkPath + "!/res/drawable-mdpi-v4/design_ic_visibility.png";
    PathString pathString = new PathString("apk", resourcePath);

    byte[] bytes = FileResourceReader.readBytes(pathString);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);

    bytes = FileResourceReader.readBytes("apk:" + resourcePath);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);
  }

  @Test
  public void testCreateXmlPullParser() throws Exception {
    Path resApkPath = TestUtils.resolveWorkspacePath(AarTestUtils.TEST_DATA_DIR + "/design_aar/" + FN_RESOURCE_STATIC_LIBRARY);
    String resourcePath = resApkPath + "!/res/layout/design_bottom_navigation_item.xml";
    PathString pathString = new PathString("apk", resourcePath);

    XmlPullParser parser = FileResourceReader.createXmlPullParser(pathString);
    assertTrue(parser instanceof ProtoXmlPullParser);
  }
}
