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
package org.jetbrains.android.dom.converters

import com.android.SdkConstants
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.res.NO_PREFIX_PACKAGES_FOR_VIEW
import com.intellij.util.xml.ConvertContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

class ViewClassConverter : PackageClassConverter(false, NO_PREFIX_PACKAGES_FOR_VIEW, true,
                                                 arrayOf(AndroidUtils.VIEW_CLASS_NAME)) {

  companion object {
    private val EXTENDED_NO_PREFIX_PACKAGES_FOR_VIEW = NO_PREFIX_PACKAGES_FOR_VIEW + SdkConstants.ANDROID_APP_PKG
  }

  // Returning packages should be aligned with [IdeResourcesUtil.isViewPackageNeeded]
  override fun getExtraBasePackages(context: ConvertContext): Array<String> {
    val facet = AndroidFacet.getInstance(context) ?: return emptyArray()
    val apiLevel = StudioAndroidModuleInfo.getInstance(facet).moduleMinApi
    return if (apiLevel >= 20) EXTENDED_NO_PREFIX_PACKAGES_FOR_VIEW else NO_PREFIX_PACKAGES_FOR_VIEW
  }
}