/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.VersionCodes;
import com.android.sdklib.repository.IdDisplay;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TargetsTest {
  private static final AndroidVersion VERSION = new AndroidVersion(VersionCodes.R);

  @Test
  public void testToStringTagEqualsDefaultTag() {
    // Act
    Object string = Targets.toString(VERSION);

    // Assert
    assertEquals("Android 11.0", string);
  }

  @Test
  public void testToString() {
    // Arrange
    IdDisplay tag = IdDisplay.create("google_apis_playstore", "Google Play");

    // Act
    Object string = Targets.toString(VERSION, tag);

    // Assert
    assertEquals("Android 11.0 Google Play", string);
  }

  @Test
  public void testToStringAndroidVersionWithExtensionLevel() {
    // Arrange
    AndroidVersion versionWithExtensionLevel = new AndroidVersion(VersionCodes.R, null, 5, false);

    // Act
    Object string = Targets.toString(versionWithExtensionLevel);

    // Assert
    assertEquals("Android 11.0", string);
  }
}
