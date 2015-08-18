/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.android.AndroidTestCase;

import java.io.BufferedReader;
import java.io.File;

public class AndroidTargetDataTest extends AndroidTestCase {
  private File mySdkDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySdkDir = new File(getTestSdkPath());
    assertTrue(mySdkDir.exists());
  }

  public void testResourceIdMap() throws Exception {
    final AndroidTargetData.MyPublicResourceCacheBuilder builder = new AndroidTargetData.MyPublicResourceCacheBuilder();
    String path = "platforms/android-1.5/data/res/values/public.xml".replace('/', File.separatorChar);
    File publicXml = new File(mySdkDir, path);

    assertTrue(publicXml.exists());

    BufferedReader reader = null;
    try {
      reader = Files.newReader(publicXml, Charsets.UTF_8);
      NanoXmlUtil.parse(reader, builder);
    } finally {
      assert reader != null;
      reader.close();
    }

    TIntObjectHashMap<String> map = builder.getIdMap();
    assertEquals("@android:transition/move", map.get(0x010f0001));
    assertEquals("@android:id/widget_frame", map.get(0x01020018));
    assertNull(map.get(0));
  }
}
