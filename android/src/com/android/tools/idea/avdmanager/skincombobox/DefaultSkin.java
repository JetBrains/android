/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public record DefaultSkin(@NotNull Path path) implements Skin {
  /**
   * Resolve collisions in favor of the parameter. If the user picks a platform skin or a system image skin with the file chooser before the
   * asynchronous skin collection finishes it will be represented by a DefaultSkin. We resolve collisions in this way because the combo box
   * renders PlatformSkins and SystemImageSkins with more information.
   */
  @NotNull
  @Override
  public Skin merge(@NotNull Skin skin) {
    assert skin.path().equals(path) : skin;
    return skin;
  }

  @NotNull
  @Override
  public String toString() {
    return path.getFileName().toString();
  }
}
