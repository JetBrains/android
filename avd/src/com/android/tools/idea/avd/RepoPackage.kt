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
package com.android.tools.idea.avd

import com.android.repository.api.RepoPackage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.meta.DetailsTypes.AddonDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.PlatformDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType

internal fun RepoPackage.hasSystemImage(): Boolean {
  val details = typeDetails

  return details is SysImgDetailsType ||
    details is PlatformDetailsType && details.apiLevel <= 13 ||
    details is AddonDetailsType && details.hasSystemImage()
}

private fun AddonDetailsType.hasSystemImage(): Boolean {
  return apiLevel <= 19 &&
    vendor.id == "google" &&
    SystemImageTags.TAGS_WITH_GOOGLE_API.contains(tag)
}
