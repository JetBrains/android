/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.device

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.sdklib.AndroidVersion.VersionCodes.KITKAT_WATCH
import com.android.sdklib.AndroidVersion.VersionCodes.LOLLIPOP
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_AUTO
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_TV
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_WEAR
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API_TV
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API_WEAR
import com.android.sdklib.SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_TAG
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_X86_TAG
import com.android.sdklib.repository.targets.SystemImage.ANDROID_TV_TAG
import com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_PLAY_STORE_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_TV_TAG
import com.android.sdklib.repository.targets.SystemImage.WEAR_TAG
import icons.StudioIllustrations.FormFactors
import javax.swing.Icon
import kotlin.math.min

/**
 * Representations of all Android hardware devices we can target when building an app.
 */
enum class FormFactor(
  val id: String,
  val displayName: String,
  val defaultApi: Int,
  /**
   * The minimum API level supported by this form factor, as known at compile time.
   * Only used if offline; if we are online, we will query SDK Manager for
   * available system images.
   */
  val minOfflineApiLevel: Int,
  maxOfflineApiLevel: Int,
  val icon: Icon,
  val largeIcon: Icon,
  private val apiTags: List<IdDisplay> = listOf()
) {
  MOBILE("Mobile", "Phone and Tablet", RECOMMENDED_MIN_SDK_VERSION, LOWEST_ACTIVE_API, HIGHEST_KNOWN_API, FormFactors.MOBILE,
         FormFactors.MOBILE_LARGE, listOf(DEFAULT_TAG, GOOGLE_APIS_TAG, GOOGLE_APIS_X86_TAG)),
  WEAR("Wear", "Wear OS", LOLLIPOP, LOWEST_ACTIVE_API_WEAR, HIGHEST_KNOWN_API_WEAR, FormFactors.WEAR,
       FormFactors.WEAR_LARGE, listOf(WEAR_TAG)),
  TV("TV", "Android TV", LOLLIPOP, LOWEST_ACTIVE_API_TV, HIGHEST_KNOWN_API_TV, FormFactors.TV,
     FormFactors.TV_LARGE, listOf(ANDROID_TV_TAG, GOOGLE_TV_TAG)),
  AUTOMOTIVE("Automotive", "Automotive", VersionCodes.P, VersionCodes.P, HIGHEST_KNOWN_API_AUTO, FormFactors.CAR,
             FormFactors.CAR_LARGE, listOf(AUTOMOTIVE_TAG, AUTOMOTIVE_PLAY_STORE_TAG));

  /**
   * The maximum API level supported by this form factor, as known at compile time.
   * Only used if offline; if we are online, we will query SDK Manager for
   * available system images.
   */
  val maxOfflineApiLevel: Int = min(maxOfflineApiLevel, HIGHEST_KNOWN_STABLE_API)

  override fun toString(): String = displayName

  fun isSupported(tag: IdDisplay?, targetSdkLevel: Int): Boolean {
    if (this == MOBILE && targetSdkLevel == KITKAT_WATCH) {
      return false
    }
    return apiTags.isEmpty() || tag in apiTags
  }

  // Currently all form factors have emulators, but we keep this method to ease introduction of new form factors
  fun hasEmulator(): Boolean = true
}
