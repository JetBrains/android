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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InfoSectionTest {
  @Test
  public void testToString() {
    // Arrange
    InfoSection section = new InfoSection("Properties");

    InfoSection.setText(section.addNameAndValueLabels("fastboot.chosenSnapshotFile"), "");
    InfoSection.setText(section.addNameAndValueLabels("runtime.network.speed"), "full");
    InfoSection.setText(section.addNameAndValueLabels("hw.accelerometer"), "yes");
    InfoSection.setText(section.addNameAndValueLabels("fastboot.forceChosenSnapshotBoot"), "no");

    // Act
    Object actual = section.toString();

    // Assert
    Object expected = String.format("Properties%n" +
                                    "fastboot.chosenSnapshotFile%n" +
                                    "runtime.network.speed            full%n" +
                                    "hw.accelerometer                 yes%n" +
                                    "fastboot.forceChosenSnapshotBoot no%n");

    assertEquals(expected, actual);
  }
}
