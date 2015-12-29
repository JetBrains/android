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
package com.android.tools.idea.fd;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class InstantRunBuildInfoTest {
  @Test
  public void testBuildId() throws IOException {
    InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
    assertEquals("1451325959168", info.getBuildId());
  }

  @NotNull
  private InstantRunBuildInfo getBuildInfo(@NotNull String... buildInfoPath) throws IOException {
    String xml = Files.toString(getBuildInfoFile(buildInfoPath), Charsets.UTF_8);
    return InstantRunBuildInfo.getInstantRunBuildInfo(xml);
  }

  @NotNull
  private static File getBuildInfoFile(@NotNull String... buildInfoPath) {
    File f = new File(AndroidTestBase.getTestDataPath(), FileUtil.join(buildInfoPath));
    assert f.exists() : "test data file doesn't exist";
    return f;
  }
}
