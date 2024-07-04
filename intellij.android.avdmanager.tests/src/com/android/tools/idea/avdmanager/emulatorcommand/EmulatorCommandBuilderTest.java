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
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class EmulatorCommandBuilderTest {
  private FileSystem myFileSystem;
  private Path myEmulator;

  private AvdInfo myAvd;

  @Before
  public void initEmulator() {
    myFileSystem = Jimfs.newFileSystem(Configuration.unix());
    myEmulator = myFileSystem.getPath("/home/user/Android/Sdk/emulator/emulator");
  }

  @Before
  public void initAvd() {
    myAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(myAvd.getName()).thenReturn("Pixel_4_API_30");
  }

  @After
  public void tearDown() {
    try {
      myFileSystem.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void build() {
    // Arrange
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildOnWindows() throws Exception {
    // Arrange
    try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.windows())) {
      Path emulator = fileSystem.getPath("C:\\Users\\user\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe");
      EmulatorCommandBuilder builder = new EmulatorCommandBuilder(emulator, myAvd);

      // Act
      GeneralCommandLine command = builder.build();

      // Assert
      assertEquals("C:\\Users\\user\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe -avd Pixel_4_API_30",
                   command.getCommandLineString());
    }
  }

  @Test
  public void buildNetworkLatencyIsNotNull() {
    // Arrange
    Mockito.when(myAvd.getProperty(AvdWizardUtils.AVD_INI_NETWORK_LATENCY)).thenReturn("none");
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -netdelay none -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildNetworkSpeedIsNotNull() {
    // Arrange
    Mockito.when(myAvd.getProperty(AvdWizardUtils.AVD_INI_NETWORK_SPEED)).thenReturn("full");
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -netspeed full -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildEmulatorSupportsSnapshots() {
    // Arrange
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd)
      .setEmulatorSupportsSnapshots(true);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildStudioParamsIsNotNull() {
    // Arrange
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd)
      .setStudioParams(myFileSystem.getPath("/home/user/temp/emu.tmp"));

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -studio-params /home/user/temp/emu.tmp -avd Pixel_4_API_30",
                 command.getCommandLineString());
  }

  @Test
  public void buildLaunchInToolWindow() {
    // Arrange
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd)
      .setLaunchInToolWindow(true);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300",
                 command.getCommandLineString());
  }

  @Test
  public void buildStudioEmuParamsIsNotEmpty() {
    // Arrange
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd)
      .addAllStudioEmuParams(Arrays.asList("-param-1", "-param-2", "-param-3"));

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -param-1 -param-2 -param-3", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineEmulatorBinary() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false);
    Mockito.when(myAvd.getUserSettings()).thenReturn(ImmutableMap.of(AvdWizardUtils.EMULATOR_BINARY_KEY, "../my-package/my-emulator"));
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/my-package/my-emulator -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineOptionsInUserSettings() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false);
    Mockito.when(myAvd.getUserSettings()).thenReturn(ImmutableMap.of(AvdWizardUtils.COMMAND_LINE_OPTIONS_KEY, "   -custom options"));
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -custom options", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineOptionsInCongig_Disabled() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false);
    Mockito.when(myAvd.getProperty(AvdWizardUtils.COMMAND_LINE_OPTIONS_KEY)).thenReturn("-some random -options");
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineOptions_Enabled() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true);
    Mockito.when(myAvd.getProperty(AvdWizardUtils.COMMAND_LINE_OPTIONS_KEY)).thenReturn("-some random -options");
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -some random -options", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineOptionsHandlesNullInput() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true);
    Mockito.when(myAvd.getProperty(AvdWizardUtils.COMMAND_LINE_OPTIONS_KEY)).thenReturn(null);
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30", command.getCommandLineString());
  }

  @Test
  public void buildAvdCommandLineOptionsIsSanitized() {
    // Arrange
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true);
    Mockito.when(myAvd.getProperty(AvdWizardUtils.COMMAND_LINE_OPTIONS_KEY)).thenReturn("  -some\nrandom  \n unsanitized  -options \n ");
    EmulatorCommandBuilder builder = new EmulatorCommandBuilder(myEmulator, myAvd);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -some random unsanitized -options",
                 command.getCommandLineString());
  }
}
