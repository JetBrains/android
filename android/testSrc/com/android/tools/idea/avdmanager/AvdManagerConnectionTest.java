/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.io.FileUtil;

import java.awt.*;
import java.io.File;
import java.util.Map;

public class AvdManagerConnectionTest extends AndroidGradleTestCase {

  public File tempDir;
  private AvdInfo myAvdInfo;
  private Map<String, String> myPropertiesMap = Maps.newHashMap();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tempDir = new File(getProject().getBasePath());

    myAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", "target", null, new IdDisplay("mockId", "mockDisplay"), "x86", myPropertiesMap);
  }

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public void testGetAvdResolutionFromInlineDeclaration() throws Exception {
    myPropertiesMap.put("skin.name", "1200x1950");
    assertEquals(new Dimension(1200, 1950), AvdManagerConnection.getAvdResolution(myAvdInfo));
  }

  public void testGetAvdResolutionFromSkinFile() throws Exception {
    File tempFile = new File(tempDir, "layout");
    FileUtil.writeToFile(tempFile, "parts { device { display { width 123 height 456 } } }");
    assertEquals(new Dimension(123, 456), AvdManagerConnection.getResolutionFromLayoutFile(tempFile));
  }

  public void testGetAvdResolutionFromFilePath() throws Exception {
    File tempFile = new File(tempDir, "layout");
    FileUtil.writeToFile(tempFile, "parts { device { display { width 123 height 456 } } }");

    myPropertiesMap.put("skin.path", tempDir.getPath());
    assertEquals(new Dimension(123, 456), AvdManagerConnection.getAvdResolution(myAvdInfo));
  }

  public void testGetAvdDensity() throws Exception {
    myPropertiesMap.put("hw.lcd.density", "120");
    assertEquals("ldpi", AvdManagerConnection.getAvdDensity(myAvdInfo));
    myPropertiesMap.put("hw.lcd.density", "160");
    assertEquals("mdpi", AvdManagerConnection.getAvdDensity(myAvdInfo));
    myPropertiesMap.put("hw.lcd.density", "240");
    assertEquals("hdpi", AvdManagerConnection.getAvdDensity(myAvdInfo));
    myPropertiesMap.put("hw.lcd.density", "320");
    assertEquals("xhdpi", AvdManagerConnection.getAvdDensity(myAvdInfo));
    myPropertiesMap.put("hw.lcd.density", "480");
    assertEquals("xxhdpi", AvdManagerConnection.getAvdDensity(myAvdInfo));
  }
}