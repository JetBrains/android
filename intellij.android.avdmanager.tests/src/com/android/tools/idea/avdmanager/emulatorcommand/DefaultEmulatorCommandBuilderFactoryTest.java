/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.emulatorcommand;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.ui.AvdWizardUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.execution.configurations.GeneralCommandLine;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DefaultEmulatorCommandBuilderFactoryTest {
  private EmulatorCommandBuilderFactory myFactory;
  private Path myEmulator;
  private AvdInfo myAvd;

  @Before
  public void initFactory() {
    myFactory = new DefaultEmulatorCommandBuilderFactory();
  }

  @Before
  public void initEmulator() {
    myEmulator = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/emulator/emulator");
  }

  @Before
  public void initAvd() {
    myAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(myAvd.getName()).thenReturn("Pixel_4_API_30");
  }

  @Test
  public void newEmulatorCommandBuilderUseColdBootEqualsYes() {
    // Arrange
    Mockito.when(myAvd.getProperty(AvdWizardUtils.USE_COLD_BOOT)).thenReturn("yes");

    // Act
    EmulatorCommandBuilder builder = myFactory.newEmulatorCommandBuilder(myEmulator, myAvd);

    // Assert
    GeneralCommandLine command = builder
      .setEmulatorSupportsSnapshots(true)
      .build();

    assertEquals("/home/user/Android/Sdk/emulator/emulator -no-snapstorage -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void newEmulatorCommandBuilderUseChosenSnapshotBootEqualsYes() {
    // Arrange
    Mockito.when(myAvd.getProperty(AvdWizardUtils.USE_CHOSEN_SNAPSHOT_BOOT)).thenReturn("yes");
    Mockito.when(myAvd.getProperty(AvdWizardUtils.CHOSEN_SNAPSHOT_FILE)).thenReturn("snap_2020-11-10_13-18-17");

    // Act
    EmulatorCommandBuilder builder = myFactory.newEmulatorCommandBuilder(myEmulator, myAvd);

    // Assert
    GeneralCommandLine command = builder
      .setEmulatorSupportsSnapshots(true)
      .build();

    assertEquals("/home/user/Android/Sdk/emulator/emulator -snapshot snap_2020-11-10_13-18-17 -no-snapshot-save -avd Pixel_4_API_30",
                 command.getCommandLineString());
  }

  @Test
  public void newEmulatorCommandBuilder() {
    // Act
    EmulatorCommandBuilder builder = myFactory.newEmulatorCommandBuilder(myEmulator, myAvd);

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30", builder.build().getCommandLineString());
  }
}
