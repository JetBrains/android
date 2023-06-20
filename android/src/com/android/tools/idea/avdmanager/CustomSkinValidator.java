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
package com.android.tools.idea.avdmanager;

import com.android.SdkConstants;
import com.android.tools.adtui.validation.Validator;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class CustomSkinValidator implements Validator<Optional<File>> {
  @Override
  public @NotNull Result validate(@NotNull Optional<File> optionalCustomSkin) {
    if (optionalCustomSkin.isEmpty()) {
      return Result.OK;
    }

    File customSkin = optionalCustomSkin.get();

    if (FileUtil.filesEqual(customSkin, AvdWizardUtils.NO_SKIN)) {
      return Result.OK;
    }

    if (FileUtil.filesEqual(customSkin, SkinChooser.LOADING_SKINS)) {
      return new Result(Severity.ERROR, "Loading device skins. This can take a few seconds.");
    }

    File layout = new File(customSkin, SdkConstants.FN_SKIN_LAYOUT);

    if (!layout.isFile()) {
      return new Result(Severity.ERROR, "The skin directory does not point to a valid skin.");
    }

    return Result.OK;
  }
}
