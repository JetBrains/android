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

import com.android.tools.idea.avdmanager.SkinUtils;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class NoSkin implements Skin {
  public static final Skin INSTANCE = new NoSkin();

  private NoSkin() {
  }

  /**
   * Resolves collisions in favor of this. If the user creates a hardware profile with no skin Collector will return it wrapped in a
   * DefaultSkin, which would collide with NoSkin.INSTANCE.
   */
  @NotNull
  @Override
  public Skin merge(@NotNull Skin skin) {
    assert skin.path().equals(SkinUtils.noSkin()) : skin;
    return this;
  }

  @NotNull
  @Override
  public Path path() {
    return SkinUtils.noSkin();
  }

  @NotNull
  @Override
  public String toString() {
    return "No Skin";
  }
}
