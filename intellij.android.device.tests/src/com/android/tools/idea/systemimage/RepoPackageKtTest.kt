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
package com.android.tools.idea.systemimage

import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.TypeDetails
import com.android.sdklib.repository.AndroidSdkHandler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RepoPackageKtTest {
  private val repoPackage = RepoManager.getCommonModule().createLatestFactory().createRemotePackage()

  @Test
  fun repoPackageHasSystemImageDetailsIsSysImgDetailsType() {
    // Arrange
    val details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertTrue(hasSystemImage)
  }

  @Test
  fun repoPackageHasSystemImageDetailsIsPlatformDetailsTypeAndApiLevelIsLessThanOrEqualTo13() {
    // Arrange
    val details = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType()
    details.apiLevel = 10

    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertTrue(hasSystemImage)
  }

  @Test
  fun repoPackageHasSystemImage() {
    // Arrange
    val details = RepoManager.getGenericModule().createLatestFactory().createGenericDetailsType()
    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertFalse(hasSystemImage)
  }

  @Test
  fun addonDetailsTypeHasSystemImageApiLevelIsGreaterThan19() {
    // Arrange
    val details = AndroidSdkHandler.getAddonModule().createLatestFactory().createAddonDetailsType()
    details.apiLevel = 21

    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertFalse(hasSystemImage)
  }

  @Test
  fun addonDetailsTypeHasSystemImageTagEqualsGoogleApis() {
    // Arrange
    val vendor = AndroidSdkHandler.getCommonModule().createLatestFactory().createIdDisplayType()
    vendor.id = "google"

    val tag = AndroidSdkHandler.getCommonModule().createLatestFactory().createIdDisplayType()
    tag.id = "google_apis"

    val details = AndroidSdkHandler.getAddonModule().createLatestFactory().createAddonDetailsType()
    details.apiLevel = 10
    details.vendor = vendor
    details.tag = tag

    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertTrue(hasSystemImage)
  }

  @Test
  fun addonDetailsTypeHasSystemImage() {
    // Arrange
    val vendor = AndroidSdkHandler.getCommonModule().createLatestFactory().createIdDisplayType()
    vendor.id = "google"

    val tag = AndroidSdkHandler.getCommonModule().createLatestFactory().createIdDisplayType()
    tag.id = "google_gdk"

    val details = AndroidSdkHandler.getAddonModule().createLatestFactory().createAddonDetailsType()
    details.apiLevel = 19
    details.vendor = vendor
    details.tag = tag

    repoPackage.setTypeDetails(details as TypeDetails)

    // Act
    val hasSystemImage = repoPackage.hasSystemImage()

    // Assert
    assertFalse(hasSystemImage)
  }
}
