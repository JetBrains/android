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
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class DefaultEmulatorCommandBuilderFactory implements EmulatorCommandBuilderFactory {
  @Override
  public @NotNull EmulatorCommandBuilder newEmulatorCommandBuilder(@NotNull Path emulator, @NotNull AvdInfo avd) {
    if (Objects.equals(avd.getProperty(ConfigKey.FORCE_COLD_BOOT_MODE), "yes")) {
      return new ColdBootEmulatorCommandBuilder(emulator, avd);
    }

    if (Objects.equals(avd.getProperty(ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE), "yes")) {
      String snapshot = Optional.ofNullable(avd.getProperty(ConfigKey.CHOSEN_SNAPSHOT_FILE)).orElse("");
      return new BootWithSnapshotEmulatorCommandBuilder(emulator, avd, snapshot);
    }

    return new EmulatorCommandBuilder(emulator, avd);
  }
}
