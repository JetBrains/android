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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Paths;

import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;

/**
 * Tests for {@link FileResourceOpener}.
 */
public class FileResourceOpenerTest extends AndroidTestCase {
  public void testOpen() throws Exception {
    String resApkPath = Paths.get(myFixture.getTestDataPath(), "design_aar", "res.apk").normalize().toString();
    URI uri = new URI("apk", resApkPath, null);
    String resourcePath = "res/drawable-mdpi-v4/design_ic_visibility.png";
    PathString pathString = new PathString(uri, resourcePath);

    ByteArrayInputStream stream = FileResourceOpener.open(pathString);
    assertNotNull(stream);
    assertEquals(309, stream.available());

    stream = FileResourceOpener.open(uri.toString() + JAR_SEPARATOR + resourcePath);
    assertNotNull(stream);
    assertEquals(309, stream.available());
  }

  public void testCreateXmlPullParser() throws Exception {
    String resApkPath = Paths.get(myFixture.getTestDataPath(), "design_aar", "res.apk").normalize().toString();
    URI uri = new URI("apk", resApkPath, null);
    String resourcePath = "res/layout/design_bottom_navigation_item.xml";
    PathString pathString = new PathString(uri, resourcePath);

    XmlPullParser parser = FileResourceOpener.createXmlPullParser(pathString);
    assertTrue(parser instanceof ProtoXmlPullParser);

    VirtualFile virtualFile = ResourceHelper.toVirtualFile(pathString);
    parser = FileResourceOpener.createXmlPullParser(virtualFile);
    assertTrue(parser instanceof ProtoXmlPullParser);
  }
}
