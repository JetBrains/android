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

package com.android.tools.idea.sdk.legacy.remote.internal.archives;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.Locale;

/**
 * The OS that this archive can be downloaded on. <br/>
 * The represents a "host" where the SDK tools and the SDK Manager can run,
 * not the Android device targets.
 * <p/>
 * The actual OS requirements for the SDK are listed at
 * <a href="http://d.android.com/sdk">http://d.android.com/sdk</a>
 */
public enum HostOs {
  /**
   * Any of the Unix-like host OSes.
   */
  LINUX("Linux"),
  /**
   * Any variation of MacOS X.
   */
  MACOSX("MacOS X"),
  /**
   * Any variation of Windows.
   */
  WINDOWS("Windows");

  private final String mUiName;

  HostOs(@NonNull String uiName) {
    mUiName = uiName;
  }

  /**
   * Returns the UI name of the OS.
   */
  @NonNull
  public String getUiName() {
    return mUiName;
  }

  /**
   * Returns the XML name of the OS.
   *
   * @returns Null, windows, macosx or linux.
   */
  @NonNull
  public String getXmlName() {
    return toString().toLowerCase(Locale.US);
  }

  /**
   * Returns the enum value matching the given XML name.
   *
   * @return A valid {@link HostOs} constnat or null if not a valid XML name.
   */
  @Nullable
  public static HostOs fromXmlName(@Nullable String xmlName) {
    if (xmlName != null) {
      for (HostOs v : values()) {
        if (v.getXmlName().equalsIgnoreCase(xmlName)) {
          return v;
        }
      }
    }
    return null;
  }
}
