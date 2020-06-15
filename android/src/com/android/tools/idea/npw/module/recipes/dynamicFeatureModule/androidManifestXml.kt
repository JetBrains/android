/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.npw.module.recipes.dynamicFeatureModule

import com.android.tools.idea.npw.dynamicapp.DeviceFeatureKind
import com.android.tools.idea.npw.dynamicapp.DeviceFeatureModel
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind

fun androidManifestXml(
  dynamicFeatureFusing: String,
  isInstantModule: Boolean,
  packageName: String,
  projectSimpleName: String,
  downloadInstallKind: DownloadInstallKind,
  deviceFeatures: Collection<DeviceFeatureModel>
): String {

  val deliveryBlock = when(downloadInstallKind) {
    DownloadInstallKind.INCLUDE_AT_INSTALL_TIME -> "<dist:install-time />"
    DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS -> {
      val deviceFeaturesBlock = deviceFeatures.joinToString("\n") {
        when (it.deviceFeatureType.get()) {
          DeviceFeatureKind.NAME ->
"""                    <dist:device-feature dist:name="${it.deviceFeatureValue}" />"""
          DeviceFeatureKind.GL_ES_VERSION ->
"""                    <dist:device-feature
                        dist:name="android.hardware.opengles.version"
                        dist:version="${it.deviceFeatureValue}" />"""
        }
      }
      """<dist:install-time>
                <dist:conditions>
                    <!-- To include or exclude this module by user countries, uncomment and update this section. -->
                    <!-- Learn more @ [https://d.android.com/r/studio-ui/dynamic-delivery/conditional-delivery] -->
                    <!--   <dist:user-countries dist:exclude="false"> -->
                    <!--     <dist:country dist:code="US" /> -->
                    <!--   </dist:user-countries> -->
$deviceFeaturesBlock
                </dist:conditions>
            </dist:install-time>"""
    }
    DownloadInstallKind.ON_DEMAND_ONLY -> "<dist:on-demand />"
  }

  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution"
    package="${packageName}">

    <dist:module
        dist:instant="${isInstantModule}"
        dist:title="@string/title_${projectSimpleName}">
        <dist:delivery>
            $deliveryBlock
        </dist:delivery>
        <dist:fusing dist:include="${dynamicFeatureFusing}" />
    </dist:module>
</manifest>
"""
}
