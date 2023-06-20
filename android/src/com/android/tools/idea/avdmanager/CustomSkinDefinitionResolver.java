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
package com.android.tools.idea.avdmanager;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Consumes the device frame enabled, custom skin definition, and custom skin definition backup properties from the
 * {@link ConfigureAvdOptionsStep Verify Configuration step} in the Virtual Device Configuration wizard and resolves the two custom skin
 * definition properties for the config.ini file.
 *
 * <p>This bit of logic is separate because the custom skin definition properties are Files in the configuration step which are difficult to
 * test properly. They're Paths here.
 */
final class CustomSkinDefinitionResolver {
  private final @Nullable Path myCustomSkinDefinition;
  private final @Nullable Path myCustomSkinDefinitionBackup;

  /**
   * @param deviceFrameEnabled         maps to the Enable Device Frame checkbox and the showDeviceFrame config.ini property
   * @param customSkinDefinition       maps to the Custom skin definition drop down and the skin.path config.ini property
   * @param customSkinDefinitionBackup maps to the skin.path.backup config.ini property. When deviceFrameEnabled is false, skin.path will be
   *                                   set to _no_skin. skin.path.backup holds the previous value so it can be restored when
   *                                   deviceFrameEnabled is true again.
   */
  CustomSkinDefinitionResolver(@NotNull FileSystem fileSystem,
                               boolean deviceFrameEnabled,
                               @Nullable Path customSkinDefinition,
                               @Nullable Path customSkinDefinitionBackup) {
    if (deviceFrameEnabled) {
      if (customSkinDefinitionBackup == null) {
        myCustomSkinDefinition = customSkinDefinition;
        myCustomSkinDefinitionBackup = null;

        return;
      }

      myCustomSkinDefinition = customSkinDefinitionBackup;
      myCustomSkinDefinitionBackup = null;

      return;
    }

    if (customSkinDefinitionBackup == null) {
      myCustomSkinDefinition = fileSystem.getPath(SkinUtils.NO_SKIN);
      myCustomSkinDefinitionBackup = customSkinDefinition;

      return;
    }

    myCustomSkinDefinition = customSkinDefinition;
    myCustomSkinDefinitionBackup = customSkinDefinitionBackup;
  }

  @NotNull Optional<Path> getCustomSkinDefinition() {
    return Optional.ofNullable(myCustomSkinDefinition);
  }

  @NotNull Optional<Path> getCustomSkinDefinitionBackup() {
    return Optional.ofNullable(myCustomSkinDefinitionBackup);
  }
}
