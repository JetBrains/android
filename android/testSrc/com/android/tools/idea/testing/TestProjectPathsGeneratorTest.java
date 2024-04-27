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
package com.android.tools.idea.testing;

import com.android.tools.idea.testing.TestProjectPathsGenerator.TestProjectPathsInfo;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Ignore;

/**
 * Tests for {@link TestProjectPathsGenerator}.
 */
@Ignore // This is for a standalone, test-only application
public class TestProjectPathsGeneratorTest extends TestCase {
  public void testCodeGeneration() throws IOException {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    TestProjectPathsInfo info = TestProjectPathsGenerator.generateTestProjectPathsFile();
    String javaFilePath = info.javaFilePath.getPath();
    String content = new String(Files.readAllBytes(Paths.get(javaFilePath)));

    assertEquals("Please run TestProjectPathsGenerator to keep the file TestProjectPaths up to date",
                 content.trim(),
                 info.fileContents.trim());
  }
}
