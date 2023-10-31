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
package com.android.tools.idea.sdk;

import static com.android.tools.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.io.FileUtil.createDirectory;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;

import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.sdk.SdkPaths;
import com.android.tools.sdk.SdkPaths.ValidationResult;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.nio.file.Path;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link SdkPaths}.
 */
public class SdkPathsTest extends TestCase {
  @Nullable
  private File tmpDir;

  private static final String DUMMY_PATH = "dummy/path".replace('/', File.separatorChar);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tmpDir = null;
  }

  @Override
  public void tearDown() throws Exception {
    if (tmpDir != null) {
      FileUtil.delete(tmpDir);
    }
  }

  public void testInvalidSdkDirectory() throws Exception {
    Path mockFile = createFileMock(DUMMY_PATH, false);

    ValidationResult result = validateAndroidSdk(mockFile, false);
    assertFalse(result.success);
    assertEquals("The SDK path does not belong to a directory.", result.message);

    result = validateAndroidSdk(mockFile, true);
    assertFalse(result.success);
    assertEquals(String.format("The SDK path\n'%1$s'\ndoes not belong to a directory.", mockFile), result.message);
  }

  public void testNoPlatformsSdkDirectory() throws Exception {
    tmpDir = createTempDirectory(SdkPathsTest.class.getName(), "testNoPlatformsSdkDirectory");

    ValidationResult result = validateAndroidSdk(tmpDir, false);
    assertFalse(result.success);
    assertEquals("SDK does not contain any platforms.", result.message);

    result = validateAndroidSdk(tmpDir, true);
    assertFalse(result.success);
    assertEquals(String.format("The SDK at\n'%1$s'\ndoes not contain any platforms.", tmpDir.getPath()), result.message);
  }

  public void testValidSdkDirectory() throws Exception {
    tmpDir = createTempDirectory(SdkPathsTest.class.getName(), "testValidSdkDirectory");
    createDirectory(new File(tmpDir, "platforms"));

    ValidationResult result = validateAndroidSdk(tmpDir, false);
    assertTrue(result.message, result.success);

    result = validateAndroidSdk(tmpDir, true);
    assertTrue(result.message, result.success);
  }

  private static Path createFileMock(@NotNull String path, boolean isDirectory) {
    if (isDirectory) {
      return InMemoryFileSystems.createInMemoryFileSystemAndFolder(path);
    }
    return InMemoryFileSystems.getSomeRoot(InMemoryFileSystems.createInMemoryFileSystem()).resolve(path);
  }
}
