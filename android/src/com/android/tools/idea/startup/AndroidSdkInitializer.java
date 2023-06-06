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
package com.android.tools.idea.startup;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.Locale;
import org.jetbrains.android.sdk.AndroidSdkUtils;

public class AndroidSdkInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance(AndroidSdkInitializer.class);

  @Override
  public void run() {
    File androidSdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    if (androidSdkPath != null) {
      int androidPlatformToAutocreate = StudioFlags.ANDROID_PLATFORM_TO_AUTOCREATE.get();
      if (androidPlatformToAutocreate != 0) {
        LOG.info(
          String.format(Locale.US, "Automatically creating an Android platform using SDK path==%s and SDK version==%d", androidSdkPath,
                        androidPlatformToAutocreate));
        AndroidSdkUtils.createNewAndroidPlatform(androidSdkPath.toString());
      }
    }
  }
}
