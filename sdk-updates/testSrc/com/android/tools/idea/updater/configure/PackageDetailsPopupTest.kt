/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.updater.configure

import com.android.repository.Revision
import com.android.repository.api.Channel
import com.android.repository.api.RepoManager
import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackageDetailsPopupTest {

  @Test
  fun testBasicDetails() {
    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    val expected =
      """
      Display name: Dummy Display Name
      Path: dummy-path
      Version: 1.2.3
      Local location: ${local.location}
    """
        .trimIndent()

    assertThat(detailsText.trim()).isEqualTo(expected)
  }

  @Test
  fun testObsoleteAndLicense() {
    val factory = RepoManager.commonModule.createLatestFactory()
    val licenseObj = factory.createLicenseType("license text", "dummy-license-id")

    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        setObsolete(true)
        license = licenseObj
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    val expected =
      """
      Display name: Dummy Display Name
      Path: dummy-path
      Version: 1.2.3
      Obsolete: true
      License: dummy-license-id
      Local location: ${local.location}
    """
        .trimIndent()

    assertThat(detailsText.trim()).isEqualTo(expected)
  }

  @Test
  fun testRemoteDetailsWithChannelDecoding() {
    val remote =
      FakePackage.FakeRemotePackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        setCompleteUrl("http://example.com/dummy.zip")
      }

    // FakeRemotePackage has a default channel with ID 0.
    // Let's test channel-0 (Stable)
    var pkg = UpdatablePackage(remote)
    var detailsText = PackageDetailsPopup.getPackageDetails(pkg)
    assertThat(detailsText).contains("Remote channel: channel-0 (Stable)")
    assertThat(detailsText).contains("Archive size: 0 bytes")
    assertThat(detailsText).contains("Archive URL: http://example.com/dummy.zip")

    // Test channel-1 (Beta)
    remote.channel = Channel.create(1)
    pkg = UpdatablePackage(remote)
    detailsText = PackageDetailsPopup.getPackageDetails(pkg)
    assertThat(detailsText).contains("Remote channel: channel-1 (Beta)")

    // Test custom channel name
    val channelCustom = Channel.create(4).apply { setValue("Custom Channel") }
    remote.channel = channelCustom
    pkg = UpdatablePackage(remote)
    detailsText = PackageDetailsPopup.getPackageDetails(pkg)
    assertThat(detailsText).contains("Remote channel: channel-4 ")
  }

  @Test
  fun testApiDetails() {
    val details =
      AndroidSdkHandler.repositoryModule.createLatestFactory().createPlatformDetailsType().apply {
        apiLevel = 30
        codename = "R"
        extensionLevel = 1
        isBaseExtension = true
      }

    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        typeDetails = details as TypeDetails
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    assertThat(detailsText).contains("API: R")
    assertThat(detailsText).contains("Major version: 30")
    assertThat(detailsText).contains("Codename: R")
    assertThat(detailsText).contains("ABIs: armeabi")
    assertThat(detailsText).contains("Extension level: 1")
    assertThat(detailsText).contains("Base Extension: true")
  }

  @Test
  fun testSysImgDetails() {
    val details =
      AndroidSdkHandler.sysImgModule.createLatestFactory().createSysImgDetailsType().apply {
        apiLevel = 30
        abis.add("x86")
        tags.add(IdDisplay.create("default", "Default Tag"))
      }

    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        typeDetails = details as TypeDetails
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    assertThat(detailsText).contains("API: 30")
    assertThat(detailsText).contains("ABIs: x86")
    if (details.tags.isNotEmpty()) {
      assertThat(detailsText).contains("Tags:\n  - Default Tag [default]\n")
    }
  }

  @Test
  fun testAddonDetails() {
    val details =
      AndroidSdkHandler.addonModule.createLatestFactory().createAddonDetailsType().apply {
        apiLevel = 30
        tag = IdDisplay.create("addon-tag-id", "Addon Tag Display")
      }

    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        typeDetails = details as TypeDetails
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    assertThat(detailsText).contains("API: 30")
    assertThat(detailsText).contains("Tag: Addon Tag Display [addon-tag-id]")
  }

  @Test
  fun testDependencies() {
    val factory = RepoManager.commonModule.createLatestFactory()
    val dep = factory.createDependencyType(Revision(2, 0, 0), "some-dependency-path")

    val local =
      FakePackage.FakeLocalPackage("dummy-path").apply {
        displayName = "Dummy Display Name"
        setRevision(Revision(1, 2, 3))
        setDependencies(ImmutableList.of(dep))
      }

    val pkg = UpdatablePackage(local)
    val detailsText = PackageDetailsPopup.getPackageDetails(pkg)

    assertThat(detailsText).contains("Dependencies:\n  - some-dependency-path (2.0.0)")
  }
}
