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

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.io.FileUtil.createDirectory;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SdkPaths}.
 */
public class SdkPathsTest extends TestCase {
  @Nullable
  private File tmpDir;

  private static final String DUMMY_PATH = "/dummy/path".replace('/', File.separatorChar);
  private static final String DUMMY_NAME = "dummy";

  // use test rules to avoid failing PATH_TOO_LONG rule
  private static final Validator<File> CUSTOM_TEST_VALIDATOR = new PathValidator.Builder().withCommonTestRules().build("Android NDK location");

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
    File mockFile = mock(File.class);
    when(mockFile.getPath()).thenReturn(DUMMY_PATH);
    when(mockFile.isDirectory()).thenReturn(false);

    Validator.Result result = validateAndroidSdk(mockFile, false);
    assertFalse(result.isOk());
    assertEquals("The path does not belong to a directory.", result.getMessage());

    result = validateAndroidSdk(mockFile, true);
    assertFalse(result.isOk());
    assertEquals(String.format("The path\n'%1$s'\ndoes not belong to a directory.", DUMMY_PATH), result.getMessage());
  }

  public void testNoPlatformsSdkDirectory() throws Exception {
    tmpDir = createTempDirectory(SdkPathsTest.class.getName(), "testNoPlatformsSdkDirectory");

    Validator.Result result = validateAndroidSdk(tmpDir, false);
    assertFalse(result.isOk());
    assertEquals("SDK does not contain any platforms.", result.getMessage());

    result = validateAndroidSdk(tmpDir, true);
    assertFalse(result.isOk());
    assertEquals(String.format("The SDK at\n'%1$s'\ndoes not contain any platforms.", tmpDir.getPath()), result.getMessage());
  }

  public void testValidSdkDirectory() throws Exception {
    tmpDir = createTempDirectory(SdkPathsTest.class.getName(), "testValidSdkDirectory");
    createDirectory(new File(tmpDir, "platforms"));

    Validator.Result result = validateAndroidSdk(tmpDir, false);
    assertTrue(result.getMessage(), result.isOk());

    result = validateAndroidSdk(tmpDir, true);
    assertTrue(result.getMessage(), result.isOk());
  }

  public void testInvalidNdkDirectory() throws Exception {
    File mockFile = mock(File.class);
    when(mockFile.getPath()).thenReturn(DUMMY_PATH);
    when(mockFile.getAbsolutePath()).thenReturn(DUMMY_PATH);
    when(mockFile.isDirectory()).thenReturn(false);
    when(mockFile.getName()).thenReturn(DUMMY_NAME);
    when(mockFile.getParent()).thenReturn("/dummy");

    Validator.Result result = validateAndroidNdk(mockFile, false);
    assertFalse(result.isOk());
    assertEquals("The path does not belong to a directory.", result.getMessage());

    result = validateAndroidNdk(mockFile, true);
    assertFalse(result.isOk());
    assertEquals(String.format("The path\n'%1$s'\ndoes not belong to a directory.", DUMMY_PATH), result.getMessage());
  }

  public void testUnReadableNdkDirectory() throws Exception {
    File mockFile = mock(File.class);
    when(mockFile.getPath()).thenReturn(DUMMY_PATH);
    when(mockFile.getAbsolutePath()).thenReturn(DUMMY_PATH);
    when(mockFile.isDirectory()).thenReturn(true);
    when(mockFile.canRead()).thenReturn(false);
    when(mockFile.getName()).thenReturn(DUMMY_NAME);
    when(mockFile.getParent()).thenReturn("/dummy");

    Validator.Result result = validateAndroidNdk(mockFile, false);
    assertFalse(result.isOk());
    assertEquals("The path is not readable.", result.getMessage());

    result = validateAndroidNdk(mockFile, true);
    assertFalse(result.isOk());
    assertEquals(String.format("The path\n'%1$s'\nis not readable.", DUMMY_PATH), result.getMessage());
  }

  public void testNoPlatformsNdkDirectory() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    tmpDir = createTempDirectory(SdkPathsTest.class.getSimpleName(), "testNoPlatformsNdkDirectory");
    Validator.Result result = validateAndroidNdk(tmpDir, false, CUSTOM_TEST_VALIDATOR);
    assertFalse(result.isOk());
    assertEquals("NDK does not contain any platforms.", result.getMessage());

    result = validateAndroidNdk(tmpDir, true, CUSTOM_TEST_VALIDATOR);
    assertFalse(result.isOk());
    assertEquals(String.format("The NDK at\n'%1$s'\ndoes not contain any platforms.", tmpDir.getPath()), result.getMessage());
  }

  public void testNoToolchainsNdkDirectory() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    tmpDir = createTempDirectory(SdkPathsTest.class.getSimpleName(), "testNoToolchainsNdkDirectory");
    createDirectory(new File(tmpDir, "platforms"));

    Validator.Result result = validateAndroidNdk(tmpDir, false, CUSTOM_TEST_VALIDATOR);
    assertFalse(result.isOk());
    assertEquals("NDK does not contain any toolchains.", result.getMessage());

    result = validateAndroidNdk(tmpDir, true, CUSTOM_TEST_VALIDATOR);
    assertFalse(result.isOk());
    assertEquals(String.format("The NDK at\n'%1$s'\ndoes not contain any toolchains.", tmpDir.getPath()), result.getMessage());
  }

  public void testValidNdkDirectory() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    tmpDir = createTempDirectory(SdkPathsTest.class.getName(), "testValidNdkDirectory");
    createDirectory(new File(tmpDir, "platforms"));
    createDirectory(new File(tmpDir, "toolchains"));

    Validator.Result result = validateAndroidNdk(tmpDir, false, CUSTOM_TEST_VALIDATOR);
    assertTrue(result.getMessage(), result.isOk());

    result = validateAndroidNdk(tmpDir, true, CUSTOM_TEST_VALIDATOR);
    assertTrue(result.getMessage(), result.isOk());
  }
}
