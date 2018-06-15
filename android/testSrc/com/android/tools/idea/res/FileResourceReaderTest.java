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
package com.android.tools.idea.res;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.res.aar.ProtoXmlPullParser;
import com.android.tools.idea.util.FileExtensions;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.xmlpull.v1.XmlPullParser;

import java.net.URI;
import java.nio.file.Paths;

import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;

/**
 * Tests for {@link FileResourceReader}.
 */
public class FileResourceReaderTest extends AndroidTestCase {
  public void testReadBytes() throws Exception {
    String resApkPath = Paths.get(myFixture.getTestDataPath(), "design_aar", FN_RESOURCE_STATIC_LIBRARY).normalize().toString();
    URI uri = new URI("apk", "", resApkPath, null);
    String resourcePath = "res/drawable-mdpi-v4/design_ic_visibility.png";
    PathString pathString = new PathString(uri, resourcePath);

    byte[] bytes = FileResourceReader.readBytes(pathString);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);

    bytes = FileResourceReader.readBytes(uri.toString() + JAR_SEPARATOR + resourcePath);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);
  }

  public void testCreateXmlPullParser() throws Exception {
    String resApkPath = Paths.get(myFixture.getTestDataPath(), "design_aar", FN_RESOURCE_STATIC_LIBRARY).normalize().toString();
    URI uri = new URI("apk", resApkPath, null);
    String resourcePath = "res/layout/design_bottom_navigation_item.xml";
    PathString pathString = new PathString(uri, resourcePath);

    XmlPullParser parser = FileResourceReader.createXmlPullParser(pathString);
    assertTrue(parser instanceof ProtoXmlPullParser);

    VirtualFile virtualFile = FileExtensions.toVirtualFile(pathString);
    parser = FileResourceReader.createXmlPullParser(virtualFile);
    assertTrue(parser instanceof ProtoXmlPullParser);
  }
}
