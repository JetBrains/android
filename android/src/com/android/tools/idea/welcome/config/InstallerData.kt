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
package com.android.tools.idea.welcome.config

import com.google.common.annotations.VisibleForTesting
import com.android.prefs.AndroidLocation
import com.android.tools.idea.npw.PathValidationResult
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsStep
import com.google.common.base.Charsets
import com.google.common.base.MoreObjects
import com.google.common.collect.Maps
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil

import java.io.File
import java.io.IOException

/**
 * Wrapper around data passed from the installer.
 */
class InstallerData(
  val androidSrc: File?,
  val androidDest: File?,
  private val myCreateAvd: Boolean,
  val timestamp: String?,
  private val myVersion: String?
) {

  val isCurrentVersion: Boolean
    get() {
      val buildNumber = ApplicationInfo.getInstance().build.components
      val build = if (buildNumber.isNotEmpty()) buildNumber[buildNumber.size - 1] else 0
      return build.toString() == myVersion
    }

  fun shouldCreateAvd(): Boolean = myCreateAvd

  override fun toString(): String = MoreObjects.toStringHelper(this)
    .add(PROPERTY_SDK_REPO, androidSrc)
    .add(PROPERTY_SDK, androidDest)
    .add(PROPERTY_AVD, myCreateAvd)
    .add(PROPERTY_TIMESTAMP, timestamp)
    .toString()

  fun hasValidSdkLocation(): Boolean {
    val location = androidDest ?: return false
    val path = location.absolutePath
    val validationResult = PathValidationResult.validateLocation(path, SdkComponentsStep.FIELD_SDK_LOCATION, false)
    return !validationResult.isError
  }

  private object Holder {
    var INSTALLER_DATA = parse()
  }

  companion object {
    @JvmField
    val EMPTY = InstallerData(null, null, true, null, null)

    private val PATH_FIRST_RUN_PROPERTIES = FileUtil.join("studio", "installer", "firstrun.data")
    private const val PROPERTY_SDK = "androidsdk.dir"
    private const val PROPERTY_SDK_REPO = "androidsdk.repo"
    private const val PROPERTY_TIMESTAMP = "install.timestamp"
    private const val PROPERTY_AVD = "create.avd"
    private const val PROPERTY_VERSION = "studio.version"
    private val LOG = Logger.getInstance(InstallerData::class.java)

    private fun parse(): InstallerData? {
      val properties = readProperties() ?: return null
      val androidSdkPath = properties[PROPERTY_SDK]
      val androidDest = if (StringUtil.isEmptyOrSpaces(androidSdkPath)) null else File(androidSdkPath)
      return InstallerData(getIfPathExists(properties, PROPERTY_SDK_REPO), androidDest,
                           java.lang.Boolean.valueOf(if (properties.containsKey(PROPERTY_AVD)) properties[PROPERTY_AVD] else "true"),
                           properties[PROPERTY_TIMESTAMP], properties[PROPERTY_VERSION])
    }

    private fun readProperties(): Map<String, String>? {
      try {
        // Firstrun properties file contains a series of "key=value" lines.
        val file = File(AndroidLocation.getFolder(), PATH_FIRST_RUN_PROPERTIES)
        if (file.isFile) {
          val properties = Maps.newHashMap<String, String>()
          val lines = Files.readLines(file, Charsets.UTF_16LE)
          for (line in lines) {
            val keyValueSeparator = line.indexOf('=')
            if (keyValueSeparator < 0) {
              continue
            }
            val key = line.substring(0, keyValueSeparator).trim { it <= ' ' }
            val value = line.substring(keyValueSeparator + 1).trim { it <= ' ' }
            if (key.isEmpty()) {
              continue
            }
            properties[key] = value
          }
          return properties
        }
      }
      catch (e: AndroidLocation.AndroidLocationException) {
        LOG.error(e)
      }
      catch (e: IOException) {
        LOG.error(e)
      }

      return null
    }

    private fun getIfPathExists(properties: Map<String, String>, propertyName: String): File? {
      val path = properties[propertyName]
      if (!StringUtil.isEmptyOrSpaces(path)) {
        val file = File(path)
        return if (file.isDirectory) file else null
      }
      return null
    }

    @VisibleForTesting
    @Synchronized
    @JvmStatic
    fun set(data: InstallerData?) {
      Holder.INSTALLER_DATA = data
    }

    fun exists(): Boolean {
      return Holder.INSTALLER_DATA != null
    }

    @Synchronized
    @JvmStatic
    fun get(): InstallerData {
      return Holder.INSTALLER_DATA!!
    }
  }
}
