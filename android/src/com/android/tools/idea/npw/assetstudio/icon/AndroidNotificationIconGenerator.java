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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.NotificationIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import org.jetbrains.annotations.NotNull;

/**
 * Settings when generating a notification icon.
 *
 * See also https://romannurik.github.io/AndroidAssetStudio/icons-notification.html
 */
public final class AndroidNotificationIconGenerator extends AndroidIconGenerator {

  @NotNull
  @Override
  protected GraphicGenerator createGenerator() {
    return new NotificationIconGenerator();
  }

  @NotNull
  @Override
  protected GraphicGenerator.Options createOptions(@NotNull Class<? extends BaseAsset> assetType) {
    return new NotificationIconGenerator.NotificationOptions();
  }
}
