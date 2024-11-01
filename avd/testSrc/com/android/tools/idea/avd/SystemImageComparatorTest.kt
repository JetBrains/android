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

import com.android.repository.api.RepoPackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.IdDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class SystemImageComparatorTest {
  @Test
  fun compareIsForTablet() {
    // Arrange
    val image1 = mockSystemImage(AndroidVersion(34, null, 7, true))

    val image2 = mockSystemImage(AndroidVersion(34, null, 7, true))
    whenever(image2.tags)
      .thenReturn(listOf(SystemImageTags.PLAY_STORE_TAG, SystemImageTags.TABLET_TAG))

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  @Test
  fun compareIsPreview() {
    // Arrange
    val image1 = mockSystemImage(AndroidVersion(34, "VanillaIceCream", 12, true))
    val image2 = mockSystemImage(AndroidVersion(34, null, 12, false))

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  @Test
  fun compareFeatureLevels() {
    // Arrange
    val image1 = mockSystemImage(AndroidVersion(33, null, 5, false))
    val image2 = mockSystemImage(AndroidVersion(34, null, 12, false))

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  @Test
  fun compareExtensionLevels() {
    // Arrange
    val image1 = mockSystemImage(AndroidVersion(34, null, 10, false))
    val image2 = mockSystemImage(AndroidVersion(34, null, 8, false))
    val image3 = mockSystemImage(AndroidVersion(34, null, null, true))
    val image4 = mockSystemImage(AndroidVersion(34, null, 7, true), hasPlayStore = true)

    val images = listOf(image4, image3, image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2, image3, image4), sortedImages)
  }

  @Test
  fun compareServices() {
    val image1 = mockSystemImage(AndroidVersion(33, null, 3, true))
    whenever(image1.hasGoogleApis()).thenReturn(true)

    val image2 = mockSystemImage(AndroidVersion(33, null, 3, true), true)

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  @Test
  fun compareOtherTagCounts() {
    val image1 = mockSystemImage(AndroidVersion(33, null, 3, true), true)
    whenever(image1.tags).thenReturn(listOf(IdDisplay.create("other-tag", "Other Tag")))

    val image2 = mockSystemImage(AndroidVersion(33, null, 3, true), true)

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  @Test
  fun compareDisplayNames() {
    val image1 =
      mockSystemImage(
        AndroidVersion(33, null, 3, true),
        true,
        "Google Play Intel x86_64 Atom System Image",
      )

    val image2 =
      mockSystemImage(
        AndroidVersion(33, null, 3, true),
        true,
        "Google Play ARM 64 v8a System Image",
      )

    val images = listOf(image2, image1)

    // Act
    val sortedImages = images.sortedWith(SystemImageComparator)

    // Assert
    assertEquals(listOf(image1, image2), sortedImages)
  }

  private companion object {
    private fun mockSystemImage(
      version: AndroidVersion,
      hasPlayStore: Boolean = false,
      displayName: String? = null,
    ): ISystemImage {
      val image = mock<ISystemImage>()
      whenever(image.androidVersion).thenReturn(version)
      whenever(image.hasPlayStore()).thenReturn(hasPlayStore)

      if (displayName != null) {
        val repoPackage = mock<RepoPackage>()
        whenever(repoPackage.displayName).thenReturn(displayName)

        whenever(image.`package`).thenReturn(repoPackage)
      }

      return image
    }
  }
}
