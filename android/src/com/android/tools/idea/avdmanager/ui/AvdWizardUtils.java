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

import static com.android.sdklib.SystemImageTags.AI_GLASSES_TAG;
import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_DISTANT_DISPLAY_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.DEPRECATED_AI_GLASSES_TAG;
import static com.android.sdklib.SystemImageTags.DESKTOP_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;
import static com.android.sdklib.SystemImageTags.XR_HEADSET_TAG;

import com.android.sdklib.SystemImageTags;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class AvdWizardUtils {
  /**
   * These are the device tags that can be assigned to a custom device profile. Generally, these are
   * tags that determine the form factor of the device, and will require a system image with a
   * corresponding tag (see com.android.sdklib.DeviceSystemImageMatcher).
   */
  private static final List<IdDisplay> ALL_DEVICE_TAGS =
      ImmutableList.of(
          DEFAULT_TAG,
          WEAR_TAG,
          DESKTOP_TAG,
          ANDROID_TV_TAG,
          GOOGLE_TV_TAG,
          AUTOMOTIVE_TAG,
          AUTOMOTIVE_DISTANT_DISPLAY_TAG,
          XR_HEADSET_TAG,
          AI_GLASSES_TAG);

  /** The tags that are available for selection in the Device Profile editor. */
  public static List<IdDisplay> availableDeviceTags() {
    return ALL_DEVICE_TAGS.stream()
        .filter(
            tag ->
                (StudioFlags.AI_GLASSES_DEVICE_SUPPORT_ENABLED.get() || tag != AI_GLASSES_TAG)
                    && (StudioFlags.XR_DEVICE_SUPPORT_ENABLED.get() || tag != XR_HEADSET_TAG))
        .toList();
  }

  public static IdDisplay canonicalizeTag(@Nullable String tagId) {
    if (tagId != null) {
      // The "android-automotive-playstore" tag shouldn't exist; we indicate Play support explicitly
      // in Device. ("android-automotive-distantdisplay" shouldn't either, but until we have distant
      // display support in the device schema, it's necessary.)
      if (tagId.equals(AUTOMOTIVE_PLAY_STORE_TAG.getId())) {
        return AUTOMOTIVE_TAG;
      } else if (tagId.equals(DEPRECATED_AI_GLASSES_TAG.getId())) {
        return AI_GLASSES_TAG;
      } else {
        for (IdDisplay tag : AvdWizardUtils.ALL_DEVICE_TAGS) {
          if (tag.getId().equals(tagId)) {
            return tag;
          }
        }
      }
    }
    return SystemImageTags.DEFAULT_TAG;
  }
}
