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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidVersionConverterTest {
  private static final AndroidVersionConverter CONVERTER = new AndroidVersionConverter();

  @Test
  public void fromStringMatcherMatches() {
    // Act
    Object version = CONVERTER.fromString("androidversion:31:null:3:false");

    // Assert
    assertEquals(new AndroidVersion(31, null, 3, false), version);
  }

  @Test
  public void fromStringCodeName() {
    // Act
    Object version = CONVERTER.fromString("S");

    // Assert
    assertEquals(new AndroidVersion(0, "S", null, true), version);
  }

  @Test
  public void fromStringApiLevel() {
    // Act
    Object version = CONVERTER.fromString("31");

    // Assert
    assertEquals(new AndroidVersion(31, null, null, true), version);
  }

  @Test
  public void testToString() {
    // Arrange
    AndroidVersion version = new AndroidVersion(31, null, 3, false);

    // Act
    Object string = CONVERTER.toString(version);

    // Assert
    assertEquals("androidversion:31:null:3:false", string);
  }
}
