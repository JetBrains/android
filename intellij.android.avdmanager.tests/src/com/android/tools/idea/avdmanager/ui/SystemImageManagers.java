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
package com.android.tools.idea.avdmanager.ui;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImageManager;
import org.jetbrains.annotations.NotNull;

final class SystemImageManagers {
  private SystemImageManagers() {
  }

  @NotNull
  static ISystemImage getImageAt(@NotNull SystemImageManager manager,
                                 @NotNull AndroidSdkHandler handler,
                                 @NotNull String path,
                                 @NotNull ProgressIndicator indicator) {
    var localPackage = handler.getLocalPackage(path, indicator);
    assert localPackage != null;

    var image = manager.getImageAt(localPackage.getLocation());
    assert image != null;

    return image;
  }
}
