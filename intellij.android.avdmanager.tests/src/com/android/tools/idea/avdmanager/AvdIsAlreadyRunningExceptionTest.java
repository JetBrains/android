/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import java.io.File;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AvdIsAlreadyRunningExceptionTest {
  @Test
  public void avdIsAlreadyRunningException() {
    // Arrange
    Path path = Path.of(System.getProperty("user.home"), ".android", "avd", "Pixel_6_API_33.avd");

    AvdInfo avd = Mockito.mock(AvdInfo.class);
    Mockito.when(avd.getDisplayName()).thenReturn("Pixel 6 API 33");
    Mockito.when(avd.getDataFolderPath()).thenReturn(path);

    // Act
    Throwable exception = new AvdIsAlreadyRunningException(avd);

    // Assert
    Object expectedMessage = "Pixel 6 API 33 is already running. If that is not the case, delete " + path + File.separatorChar +
                             "*.lock and try again.";

    assertEquals(expectedMessage, exception.getMessage());
  }
}
