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

import static org.easymock.EasyMock.eq;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.io.CancellableFileIo;
import com.android.repository.testframework.MockFileOp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public final class SdkLocationUtilsTest {
  private MockFileOp myFileOp = new MockFileOp();

  @Test
  public void isWritableSdkLocationIsNull() {
    assertFalse(SdkLocationUtils.isWritable(null));
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanNotWrite() throws IOException {
    Path file = myFileOp.toPath("/foo");
    Files.createFile(file);
    try (MockedStatic<CancellableFileIo> mockedIo = Mockito.mockStatic(CancellableFileIo.class)) {
      mockedIo.when(() -> CancellableFileIo.isWritable(eq(file))).thenReturn(false);
      assertFalse(SdkLocationUtils.isWritable(file));
    }
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanWrite() throws IOException {
    Path sdkLocation = myFileOp.toPath("/sdk");
    Files.createFile(sdkLocation);

    assertFalse(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanNotWrite() throws IOException {
    Path sdkLocation = myFileOp.toPath("/sd");
    try (MockedStatic<CancellableFileIo> mockedIo = Mockito.mockStatic(CancellableFileIo.class)) {
      mockedIo.when(() -> CancellableFileIo.isWritable(eq(sdkLocation))).thenReturn(false);
      assertFalse(SdkLocationUtils.isWritable(sdkLocation));
    }
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanWrite() throws IOException {
    Path sdkLocation = myFileOp.toPath("/sdk");
    Files.createDirectories(sdkLocation);

    assertTrue(SdkLocationUtils.isWritable(sdkLocation));
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanNotWrite() throws IOException {
    Path sdkLocation = myFileOp.toPath("/d1/d2/sdk");
    Path parent = myFileOp.toPath("/d1");
    try (MockedStatic<CancellableFileIo> mockedIo = Mockito.mockStatic(CancellableFileIo.class)) {
      mockedIo.when(() -> CancellableFileIo.isWritable(eq(parent))).thenReturn(false);
      assertFalse(SdkLocationUtils.isWritable(sdkLocation));
    }
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanWrite() throws IOException {
    Path sdkLocation = myFileOp.toPath("/d1/d2/sdk");
    Files.createDirectories(myFileOp.toPath("/d1"));

    assertTrue(SdkLocationUtils.isWritable(sdkLocation));
  }
}
