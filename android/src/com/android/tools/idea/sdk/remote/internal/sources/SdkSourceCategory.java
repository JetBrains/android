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

package com.android.tools.idea.sdk.remote.internal.sources;

/**
 * The category of a given {@link SdkSource} (which represents a download site).
 */
public enum SdkSourceCategory {

  /**
   * The default canonical and official Android repository.
   */
  ANDROID_REPO("Android Repository", true),

  /**
   * Repositories contributed by the SDK_UPDATER_URLS env var,
   * only used for local debugging.
   */
  GETENV_REPOS("Custom Repositories", false),

  /**
   * All third-party add-ons fetched from the Android repository.
   */
  ADDONS_3RD_PARTY("Third party Add-ons", true),

  /**
   * All add-ons contributed locally by the user via the "Add Add-on Site" button.
   */
  USER_ADDONS("User Add-ons", false),

  /**
   * Add-ons contributed by the SDK_UPDATER_USER_URLS env var,
   * only used for local debugging.
   */
  GETENV_ADDONS("Custom Add-ons", false);


  private final String mUiName;
  private final boolean mAlwaysDisplay;

  private SdkSourceCategory(String uiName, boolean alwaysDisplay) {
    mUiName = uiName;
    mAlwaysDisplay = alwaysDisplay;
  }

  /**
   * Returns the UI-visible name of the category. Displayed in the available package tree.
   * Cannot be null nor empty.
   */
  public String getUiName() {
    return mUiName;
  }

}
