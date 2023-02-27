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
package com.android.tools.idea.sdk;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
public final class AndroidSdkPathStore {

  @NonNls private static final String ANDROID_SDK_PATH_KEY = "android.sdk.path";

  public static AndroidSdkPathStore getInstance() {
    return ApplicationManager.getApplication().getService(AndroidSdkPathStore.class);
  }

  public void setAndroidSdkPath(@Nullable Path path) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    String sdkPath = path == null ? null : path.toAbsolutePath().toString();

    PropertiesComponent component = PropertiesComponent.getInstance();
    component.setValue(ANDROID_SDK_PATH_KEY, sdkPath);
  }

  @Nullable
  public Path getAndroidSdkPath() {
    PropertiesComponent component = PropertiesComponent.getInstance();
    String sdkPath = component.getValue(ANDROID_SDK_PATH_KEY);
    return sdkPath == null ? null : Paths.get(sdkPath);
  }
}
