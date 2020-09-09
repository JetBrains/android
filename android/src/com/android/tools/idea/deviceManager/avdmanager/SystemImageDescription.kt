/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.repository.Revision
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoPackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.targets.PlatformTarget
import com.android.sdklib.repository.targets.SystemImage
import java.io.File

/**
 * Information on a system image. Used internally by the avd manager.
 */
data class SystemImageDescription(
  val remotePackage: RemotePackage?,
  val systemImage: ISystemImage
) {
  constructor(systemImage: ISystemImage): this(null, systemImage)

  constructor(remotePackage: RemotePackage): this(
    remotePackage.apply { require(hasSystemImage(this)) },
    RemoteSystemImage(remotePackage)
  )

  val version: AndroidVersion get() = systemImage.androidVersion

  val isRemote: Boolean get() = remotePackage != null

  fun obsolete(): Boolean = systemImage.obsolete()

  val abiType: String get() = systemImage.abiType

  val tag: IdDisplay get() = systemImage.tag

  val name: String
    get() {
      val versionString = SdkVersionInfo.getVersionString(version.featureLevel) ?: "API ${version.apiString}"
      return "Android $versionString"
    }

  val vendor: String get() = systemImage.addonVendor?.display ?: PlatformTarget.PLATFORM_VENDOR

  val versionName: String? get() = SdkVersionInfo.getVersionString(systemImage.androidVersion.apiLevel)

  val revision: Revision get() = systemImage.revision

  val skins: Array<File> get() = systemImage.skins

  private class RemoteSystemImage internal constructor(private val myRemotePackage: RemotePackage) : ISystemImage {
    private val myTag: IdDisplay
    private val vendor: IdDisplay?
    private val abi: String
    private val androidVersion: AndroidVersion
    override fun getLocation(): File {
      assert(false) { "Can't get location for remote image" }
      return File("")
    }

    override fun getTag(): IdDisplay = myTag

    override fun getAddonVendor(): IdDisplay? = vendor

    override fun getAbiType(): String = abi

    override fun getSkins(): Array<File> = arrayOf()

    override fun getRevision(): Revision = myRemotePackage.version

    override fun getAndroidVersion(): AndroidVersion = androidVersion

    override fun hasPlayStore(): Boolean {
      if (SystemImage.PLAY_STORE_TAG == myTag) {
        return true
      }
      // A Wear system image has Play Store if it is
      // a recent API version and is NOT Wear-for-China.
      return SystemImage.WEAR_TAG == tag && androidVersion.apiLevel >= AndroidVersion.MIN_RECOMMENDED_WEAR_API &&
             !myRemotePackage.path.contains(ISystemImage.WEAR_CN_DIRECTORY)
    }

    override fun obsolete(): Boolean = myRemotePackage.obsolete()

    override fun compareTo(other: ISystemImage): Int =
      if (other is RemoteSystemImage) myRemotePackage.compareTo(other.myRemotePackage) else 1

    override fun hashCode(): Int = myRemotePackage.hashCode()

    override fun equals(other: Any?): Boolean {
      if (other !is RemoteSystemImage) return false
      return myRemotePackage == other.myRemotePackage
    }

    init {
      val details = myRemotePackage.typeDetails
      assert(details is DetailsTypes.ApiDetailsType)
      androidVersion = (details as DetailsTypes.ApiDetailsType).androidVersion
      var tag: IdDisplay? = null
      var vendor: IdDisplay? = null
      var abi = "armeabi"
      if (details is DetailsTypes.AddonDetailsType) {
        tag = details.tag
        vendor = details.vendor
        if (SystemImage.GOOGLE_APIS_X86_TAG == tag) {
          abi = "x86"
        }
      }
      if (details is DetailsTypes.SysImgDetailsType) {
        tag = details.tag
        vendor = details.vendor
        abi = details.abi
      }
      myTag = tag ?: SystemImage.DEFAULT_TAG
      this.vendor = vendor
      this.abi = abi
    }
  }

  companion object {
    fun hasSystemImage(p: RepoPackage): Boolean {
      val details = p.typeDetails
      if (details !is DetailsTypes.ApiDetailsType) {
        return false
      }
      if (details is DetailsTypes.SysImgDetailsType) {
        return true
      }
      // Google APIs addons up to 19 included a bundled system image
      return details is DetailsTypes.AddonDetailsType && (details as DetailsTypes.AddonDetailsType).vendor.id == "google" &&
             AvdWizardUtils.TAGS_WITH_GOOGLE_API.contains((details as DetailsTypes.AddonDetailsType).tag) && details.apiLevel <= 19
    }
  }
}