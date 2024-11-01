/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags

/** Sorts system images by preference: least preferred to most */
internal object SystemImageComparator :
  Comparator<ISystemImage> by (compareBy<ISystemImage> { it.isRecommended() }
    .thenBy { it.isForTablet() }
    .thenByDescending { it.androidVersion.isPreview }
    .thenBy { it.androidVersion.featureLevel }
    .thenByDescending {
      if (it.androidVersion.isBaseExtension) 0 else it.androidVersion.extensionLevel ?: 0
    }
    .thenByDescending { it.getServices() }
    .thenByDescending { it.getOtherTagCount() }
    .thenByDescending { it.`package`.displayName })

private fun ISystemImage.isForTablet() = tags.contains(SystemImageTags.TABLET_TAG)

/**
 * Returns the number of tags that haven't otherwise been considered by the comparator. The more of
 * these a system image has the less we know about it and the further down the list it gets sorted
 * at.
 */
private fun ISystemImage.getOtherTagCount() = tags.count { it !in ACCOUNTED_FOR_TAGS }

private val ACCOUNTED_FOR_TAGS =
  setOf(
    SystemImageTags.ANDROID_TV_TAG,
    SystemImageTags.AUTOMOTIVE_DISTANT_DISPLAY_TAG,
    SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG,
    SystemImageTags.AUTOMOTIVE_TAG,
    SystemImageTags.CHROMEOS_TAG,
    SystemImageTags.DESKTOP_TAG,
    SystemImageTags.GOOGLE_APIS_TAG,
    SystemImageTags.GOOGLE_APIS_X86_TAG,
    SystemImageTags.GOOGLE_TV_TAG,
    SystemImageTags.PLAY_STORE_TAG,
    SystemImageTags.TABLET_TAG,
    SystemImageTags.WEAR_TAG,
  )
