/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import static com.android.SdkConstants.PreferenceTags.CHECK_BOX_PREFERENCE;
import static com.android.SdkConstants.PreferenceTags.EDIT_TEXT_PREFERENCE;
import static com.android.SdkConstants.PreferenceTags.LIST_PREFERENCE;
import static com.android.SdkConstants.PreferenceTags.MULTI_SELECT_LIST_PREFERENCE;
import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;
import static com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN;
import static com.android.SdkConstants.PreferenceTags.RINGTONE_PREFERENCE;
import static com.android.SdkConstants.PreferenceTags.SWITCH_PREFERENCE;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;

public final class PreferenceUtils {
  public static final Collection<String> VALUES = ImmutableSet.of(
    CHECK_BOX_PREFERENCE,
    EDIT_TEXT_PREFERENCE,
    LIST_PREFERENCE,
    MULTI_SELECT_LIST_PREFERENCE,
    PREFERENCE_CATEGORY,
    PREFERENCE_SCREEN,
    RINGTONE_PREFERENCE,
    SWITCH_PREFERENCE
  );

  private PreferenceUtils() {
  }
}
