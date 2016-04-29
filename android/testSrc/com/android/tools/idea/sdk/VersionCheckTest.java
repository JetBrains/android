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
package com.android.tools.idea.sdk;

import com.google.common.base.Strings;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.AndroidTestCaseHelper.getAndroidSdkPath;

/**
 * Tests for {@link VersionCheck}.
 */
public class VersionCheckTest extends TestCase {
  private static final String PRE_V22_SDK_PATH = "PRE_V22_SDK_PATH";

  private File mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkPath = getAndroidSdkPath();
  }

  @Nullable
  private static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    if (Strings.isNullOrEmpty(s)) {
      s = System.getenv(name);
    }
    return s;
  }

  // Disabled until Tools 23 is available.
  public void DISABLEDtestCheckVersion() {
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(mySdkPath.getPath());
    assertTrue(result.isCompatibleVersion());
  }

  public void testCheckVersionWithOldSdk() {
    String myPreV22SdkPath = getSystemPropertyOrEnvironmentVariable(PRE_V22_SDK_PATH);
    if (!Strings.isNullOrEmpty(myPreV22SdkPath)) {
      VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(myPreV22SdkPath);
      assertFalse(result.isCompatibleVersion());
      assertNotNull(result.getRevision());
    } else {
      System.out.println("Test testCheckVersionWithOldSdk not run, no pre-v22 tools found.");
    }
  }
}
