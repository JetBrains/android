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

import com.android.repository.io.FileOp;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SdkLocationUtilsTest {
  private FileOp myFileOp;

  @Before
  public void mockFileOp() {
    myFileOp = Mockito.mock(FileOp.class);
  }

  @Test
  public void isWritableSdkLocationIsNull() {
    assertFalse(SdkLocationUtils.isWritable(myFileOp, null));
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanNotWrite() {
    File sdkLocation = Mockito.mock(File.class);
    Mockito.when(myFileOp.exists(sdkLocation)).thenReturn(true);

    assertFalse(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsNotDirectoryAndCanWrite() {
    File sdkLocation = Mockito.mock(File.class);

    Mockito.when(myFileOp.exists(sdkLocation)).thenReturn(true);
    Mockito.when(myFileOp.canWrite(sdkLocation)).thenReturn(true);

    assertFalse(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanNotWrite() {
    File sdkLocation = Mockito.mock(File.class);

    Mockito.when(myFileOp.exists(sdkLocation)).thenReturn(true);
    Mockito.when(myFileOp.isDirectory(sdkLocation)).thenReturn(true);

    assertFalse(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }

  @Test
  public void isWritableSdkLocationIsDirectoryAndCanWrite() {
    File sdkLocation = Mockito.mock(File.class);

    Mockito.when(myFileOp.exists(sdkLocation)).thenReturn(true);
    Mockito.when(myFileOp.isDirectory(sdkLocation)).thenReturn(true);
    Mockito.when(myFileOp.canWrite(sdkLocation)).thenReturn(true);

    assertTrue(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }

  @Test
  public void isWritableAncestorIsNull() {
    assertFalse(SdkLocationUtils.isWritable(myFileOp, Mockito.mock(File.class)));
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanNotWrite() {
    File ancestor2 = Mockito.mock(File.class);
    Mockito.when(myFileOp.exists(ancestor2)).thenReturn(true);

    File ancestor1 = Mockito.mock(File.class);
    Mockito.when(ancestor1.getParentFile()).thenReturn(ancestor2);

    File sdkLocation = Mockito.mock(File.class);
    Mockito.when(sdkLocation.getParentFile()).thenReturn(ancestor1);

    assertFalse(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }

  @Test
  public void isWritableAncestorIsNotNullAndCanWrite() {
    File ancestor2 = Mockito.mock(File.class);

    Mockito.when(myFileOp.exists(ancestor2)).thenReturn(true);
    Mockito.when(myFileOp.canWrite(ancestor2)).thenReturn(true);

    File ancestor1 = Mockito.mock(File.class);
    Mockito.when(ancestor1.getParentFile()).thenReturn(ancestor2);

    File sdkLocation = Mockito.mock(File.class);
    Mockito.when(sdkLocation.getParentFile()).thenReturn(ancestor1);

    assertTrue(SdkLocationUtils.isWritable(myFileOp, sdkLocation));
  }
}
