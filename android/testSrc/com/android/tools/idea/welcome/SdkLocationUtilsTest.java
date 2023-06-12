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
package com.android.tools.idea.welcome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.testutils.file.DelegatingFileSystemProvider;
import com.android.testutils.file.InMemoryFileSystems;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public final class SdkLocationUtilsTest {
  private final FileSystem fs = new DelegatingFileSystemProvider(InMemoryFileSystems.createInMemoryFileSystem()) {
    @Override
    public void checkAccess(@NotNull Path path, AccessMode... modes) throws IOException {
      Path file = path.getFileName();
      if (file != null && file.toString().endsWith("unwritable") && Arrays.asList(modes).contains(AccessMode.WRITE)) {
        throw new AccessDeniedException("unwritable");
      }
      super.checkAccess(path, modes);
    }
  }.getFileSystem();

  @Test
  public void isWritableSdkLocationIsNull() {
    assertFalse(SdkLocationUtils.isWritable(null));
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanNotWrite() throws IOException {
    Path file = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/unwritable"));
    Files.createFile(file);

    assertFalse(SdkLocationUtils.isWritable(file));
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanWrite() throws IOException {
    Path sdkLocation = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sdk"));
    Files.createFile(sdkLocation);

    assertFalse(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanNotWrite() throws IOException {
    Path sdkLocation = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/unwritable"));
    Files.createDirectories(sdkLocation);

    assertFalse(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanWrite() throws IOException {
    Path sdkLocation = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sdk"));
    Files.createDirectories(sdkLocation);

    assertTrue(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanNotWrite() throws IOException {
    Path sdkLocation = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/d1/d2/unwritable"));
    Files.createDirectories(sdkLocation);

    assertFalse(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanWrite() throws IOException {
    Path sdkLocation = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/d1/d2/sdk"));
    Files.createDirectories(fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/d1")));

    assertTrue(SdkLocationUtils.isWritable(sdkLocation));
  }
}
