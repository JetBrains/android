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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
record SystemImageSkin(@NotNull Path path, @NotNull AndroidVersion version, @NotNull String abi) implements Skin {
  /**
   * If the parameter is a DefaultSkin, return this; otherwise return the parameter. System images that don't have their own skins refer to
   * the platform skins for their platform version. This resolution will drop those skins. It also handles the user manually picking a
   * system image skin before the asynchronous collection is done.
   */
  @NotNull
  @Override
  public Skin merge(@NotNull Skin skin) {
    assert skin.path().equals(path) : skin;
    return skin instanceof DefaultSkin ? this : skin;
  }

  @NotNull
  @Override
  public String toString() {
    return path.getFileName() + " (" + AndroidVersionUtils.getFullApiName(version) + ' ' + abi + ')';
  }
}
