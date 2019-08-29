/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;


import com.android.SdkConstants;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.setGradleWrapperExecutable;
import static com.intellij.testFramework.UsefulTestCase.assertDoesntExist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * Tests for {@link AndroidGradleModuleUtils}
 * This tests uses Junit 4, so they can't be merged with {@link AndroidGradleModuleUtils} yet
 */
public class AndroidGradleUtilsTest {
  @Rule
  public TemporaryFolder myFolder = new TemporaryFolder();

  @Test
  public void gradleWrapperExecutable() throws IOException {
    Assume.assumeTrue(SystemInfo.isUnix);

    File basePath = myFolder.newFolder();
    File gradleFile = new File(basePath, SdkConstants.FN_GRADLE_WRAPPER_UNIX);
    assertTrue(gradleFile.createNewFile());
    assertTrue(gradleFile.setExecutable(false));

    setGradleWrapperExecutable(basePath);
    assertTrue(gradleFile.canExecute());
  }

  @Test
  public void gradleWrapperExecutableNoFile() throws IOException{
    Assume.assumeTrue(SystemInfo.isUnix);

    try {
      File missingDir = new File(myFolder.getRoot(), "missing");
      assertDoesntExist(missingDir);

      setGradleWrapperExecutable(missingDir);
      fail("Missing Exception");
    }
    catch (IOException ex) {
      assertEquals("Could not find gradle wrapper. Command line builds may not work properly.", ex.getMessage());
    }
  }
}
