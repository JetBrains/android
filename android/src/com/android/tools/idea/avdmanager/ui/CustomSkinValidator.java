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
package com.android.tools.idea.avdmanager.ui;

import com.android.SdkConstants;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.avdmanager.SkinUtils;
import com.android.tools.idea.observable.core.ObservableBool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class CustomSkinValidator implements Validator<Optional<Path>> {
  private final @NotNull ObservableBool mySelectedSkinLargeEnough;
  private final @NotNull ObservableBool myEnableDeviceFrame;

  private CustomSkinValidator(@NotNull Builder builder) {
    mySelectedSkinLargeEnough = builder.mySelectedSkinLargeEnough;
    myEnableDeviceFrame = builder.myEnableDeviceFrame;
  }

  static final class Builder {
    private ObservableBool mySelectedSkinLargeEnough;
    private @NotNull ObservableBool myEnableDeviceFrame = ObservableBool.TRUE;

    @NotNull Builder setSelectedSkinLargeEnough(@NotNull ObservableBool selectedSkinLargeEnough) {
      mySelectedSkinLargeEnough = selectedSkinLargeEnough;
      return this;
    }

    @NotNull Builder setEnableDeviceFrame(@NotNull ObservableBool enableDeviceFrame) {
      myEnableDeviceFrame = enableDeviceFrame;
      return this;
    }

    @NotNull CustomSkinValidator build() {
      return new CustomSkinValidator(this);
    }
  }

  @Override
  public @NotNull Result validate(@NotNull Optional<@NotNull Path> optionalCustomSkin) {
    if (!myEnableDeviceFrame.get()) {
      return Result.OK;
    }

    if (optionalCustomSkin.isEmpty()) {
      return Result.OK;
    }

    var customSkin = optionalCustomSkin.get();

    if (customSkin.equals(SkinUtils.noSkin())) {
      return Result.OK;
    }

    if (!mySelectedSkinLargeEnough.get()) {
      return new Result(Severity.WARNING, "The selected skin is not large enough to view the entire screen.");
    }

    Path layout = customSkin.resolve(SdkConstants.FN_SKIN_LAYOUT);

    if (!Files.isRegularFile(layout)) {
      layout = customSkin.resolve("default").resolve(SdkConstants.FN_SKIN_LAYOUT);
      if (!Files.isRegularFile(layout)) {
        return new Result(Severity.ERROR, "The skin directory does not point to a valid skin.");
      }
    }

    return Result.OK;
  }
}
