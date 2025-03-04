/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_DISTANT_DISPLAY_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.DESKTOP_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;
import static com.android.sdklib.SystemImageTags.XR_TAG;

import com.android.sdklib.repository.IdDisplay;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class AvdWizardUtils {
  public static final List<IdDisplay> ALL_DEVICE_TAGS = ImmutableList.of(DEFAULT_TAG, WEAR_TAG, DESKTOP_TAG,
                                                                         ANDROID_TV_TAG, GOOGLE_TV_TAG,
                                                                         AUTOMOTIVE_TAG, AUTOMOTIVE_DISTANT_DISPLAY_TAG,
                                                                         XR_TAG);
}
