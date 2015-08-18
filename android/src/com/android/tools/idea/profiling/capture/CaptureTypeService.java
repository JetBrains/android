/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.capture;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaptureTypeService {

  ExtensionPointName<CaptureType> EP_NAME = ExtensionPointName.create("com.android.captureType");

  @NotNull
  public static CaptureTypeService getInstance() {
    return ServiceManager.getService(CaptureTypeService.class);
  }

  @NotNull
  public CaptureType[] getCaptureTypes() {
    return EP_NAME.getExtensions();
  }

  @Nullable
  public <T extends CaptureType> T getType(Class<T> type) {
    return EP_NAME.findExtension(type);
  }

  @Nullable
  public CaptureType getTypeFor(@NotNull VirtualFile file) {
    for (CaptureType type : getCaptureTypes()) {
      if (type.isValidCapture(file)) {
        return type;
      }
    }
    return null;
  }
}
