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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for  {@link AndroidSdkType}.
 */
public class AndroidSdkTypeTest {
  @Test
  public void testValidateAndroidSdk() {
    File nonReadableFile = new DirectoryStub("fake", false, true);
    AndroidSdkType.ValidationResult result = AndroidSdkType.validateAndroidSdk(nonReadableFile, false);
    assertFalse(result.success);
    assertEquals("The path is not readable.", result.message);

    result = AndroidSdkType.validateAndroidSdk(nonReadableFile, true);
    assertFalse(result.success);
    assertEquals("The path\n'fake'\nis not readable.", result.message);

    File nonWritableFile = new DirectoryStub("fake", true, false);
    result = AndroidSdkType.validateAndroidSdk(nonWritableFile, false);
    assertFalse(result.success);
    assertEquals("The path is not writable.", result.message);

    result = AndroidSdkType.validateAndroidSdk(nonWritableFile, true);
    assertFalse(result.success);
    assertEquals("The path\n'fake'\nis not writable.", result.message);
  }

  private static class DirectoryStub extends File {
    private final boolean myReadable;
    private final boolean myWritable;

    DirectoryStub(@NotNull String path, boolean readable, boolean writable) {
      super(path);
      myReadable = readable;
      myWritable = writable;
    }

    @Override
    public boolean canRead() {
      return myReadable;
    }

    @Override
    public boolean canWrite() {
      return myWritable;
    }

    @Override
    public boolean isDirectory() {
      return true;
    }
  }
}