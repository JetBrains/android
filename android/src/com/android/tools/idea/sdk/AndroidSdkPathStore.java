// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.sdk;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

@Service
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
