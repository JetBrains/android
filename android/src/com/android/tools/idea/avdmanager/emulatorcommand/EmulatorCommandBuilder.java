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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.ConfigKey;
import com.android.sdklib.internal.avd.UserSettingsKey;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.text.Strings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds emulator GeneralCommandLines such as {@code /home/user/Android/Sdk/emulator/emulator -netdelay none -netspeed full
 * -avd Pixel_4_API_30}. The subclasses map to different ways of booting a virtual device: cold boot, cold boot now, quick boot, snapshot
 * boot, etc.
 */
public class EmulatorCommandBuilder {
  /** The path to the emulator executable, something like /home/user/Android/Sdk/emulator/emulator on Linux. */
  private final @NotNull Path myEmulator;

  private final @NotNull AvdInfo myAvd;

  private @Nullable Path myAvdHome;
  private boolean myEmulatorSupportsSnapshots;
  private @Nullable Path myStudioParams;
  private boolean myLaunchInToolWindow;

  private final @NotNull List<String> myStudioEmuParams;

  public EmulatorCommandBuilder(@NotNull Path emulator, @NotNull AvdInfo avd) {
    String emulatorBinary = avd.getUserSettings().get(UserSettingsKey.EMULATOR_BINARY);
    myEmulator = emulatorBinary == null ? emulator : emulator.resolveSibling(emulatorBinary).normalize();
    myAvd = avd;

    myStudioEmuParams = new ArrayList<>();
  }

  public final @NotNull EmulatorCommandBuilder setAvdHome(@Nullable Path avdHome) {
    myAvdHome = avdHome;
    return this;
  }

  public final @NotNull EmulatorCommandBuilder setEmulatorSupportsSnapshots(boolean emulatorSupportsSnapshots) {
    myEmulatorSupportsSnapshots = emulatorSupportsSnapshots;
    return this;
  }

  public final @NotNull EmulatorCommandBuilder setStudioParams(@Nullable Path studioParams) {
    myStudioParams = studioParams;
    return this;
  }

  public final @NotNull EmulatorCommandBuilder setLaunchInToolWindow(boolean launchInToolWindow) {
    myLaunchInToolWindow = launchInToolWindow;
    return this;
  }

  public final @NotNull EmulatorCommandBuilder addAllStudioEmuParams(@NotNull Collection<String> studioEmuParams) {
    myStudioEmuParams.addAll(studioEmuParams);
    return this;
  }

  public final @NotNull GeneralCommandLine build() {
    GeneralCommandLine command = new GeneralCommandLine();
    command.setExePath(myEmulator.toString());
    command.setWorkDirectory(myEmulator.getParent().toString());

    if (myAvdHome != null) {
      command.getEnvironment().put("ANDROID_AVD_HOME", myAvdHome.toString());
    }

    addParametersIfParameter2IsntNull(command, "-netdelay", myAvd.getProperty(ConfigKey.NETWORK_LATENCY));
    addParametersIfParameter2IsntNull(command, "-netspeed", myAvd.getProperty(ConfigKey.NETWORK_SPEED));

    if (myEmulatorSupportsSnapshots) {
      addSnapshotParameters(command);
    }

    addParametersIfParameter2IsntNull(command, "-studio-params", myStudioParams);
    command.addParameters("-avd", myAvd.getName());

    if (myLaunchInToolWindow) {
      command.addParameter("-qt-hide-window");
      command.addParameter("-grpc-use-token");
      command.addParameters("-idle-grpc-timeout", "300");
    }

    command.addParameters(myStudioEmuParams);
    String avdCommandLineOptions = myAvd.getUserSettings().get(UserSettingsKey.COMMAND_LINE_OPTIONS);
    command.addParameters(parseCommandLineOptions(avdCommandLineOptions));

    if (StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.get()) {
      avdCommandLineOptions = myAvd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS);
      command.addParameters(parseCommandLineOptions(avdCommandLineOptions));
    }
    return command;
  }

  private static void addParametersIfParameter2IsntNull(@NotNull GeneralCommandLine command,
                                                        @NotNull String parameter1,
                                                        @Nullable Object parameter2) {
    if (parameter2 == null) {
      return;
    }

    command.addParameters(parameter1, parameter2.toString());
  }

  void addSnapshotParameters(@NotNull GeneralCommandLine command) {
  }

  private List<String> parseCommandLineOptions(@Nullable String options) {
    if (Strings.isEmptyOrSpaces(options)) {
      return Collections.emptyList();
    }
    String withoutLineBreaks = options.trim().replaceAll("\\n", " ");
    return Arrays.asList(withoutLineBreaks.split("\\s+"));
  }
}
