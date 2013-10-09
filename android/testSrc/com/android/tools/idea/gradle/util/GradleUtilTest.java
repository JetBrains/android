/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest extends TestCase {
  private File myTempDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTempDir = Files.createTempDir();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testGetGradleWrapperPropertiesFilePath() throws IOException {
    File wrapper = new File(myTempDir, "gradle-wrapper.properties");
    FileUtilRt.createIfNotExists(wrapper);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(wrapper);
      properties.load(fileInputStream);
      String distributionUrl = properties.getProperty("distributionUrl");
      assertEquals("http://services.gradle.org/distributions/gradle-1.6-bin.zip", distributionUrl);
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  public void testUpdateGradleDistributionUrl() {
    File wrapperPath = GradleUtil.getGradleWrapperPropertiesFilePath(myTempDir);

    List<String> expected = Lists.newArrayList(FileUtil.splitPath(myTempDir.getPath()));
    expected.addAll(Lists.newArrayList("gradle", "wrapper", "gradle-wrapper.properties"));

    assertEquals(expected, FileUtil.splitPath(wrapperPath.getPath()));
  }
}
