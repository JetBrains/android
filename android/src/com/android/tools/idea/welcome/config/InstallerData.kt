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
import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.google.common.base.Charsets
import com.google.common.base.MoreObjects
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil

import java.io.File
import java.io.IOException

private val log: Logger = logger<InstallerData>()

/**
 * Wrapper around data passed from the installer.
 */
class InstallerData(
  val androidSrc: File?,
  val androidDest: File?,
  private val createAvd: Boolean,
  val timestamp: String?,
  private val version: String?
) {
  val isCurrentVersion: Boolean
    get() = (ApplicationInfo.getInstance().build.components.lastOrNull() ?: 0).toString() == version

  fun shouldCreateAvd(): Boolean = createAvd

  override fun toString(): String = MoreObjects.toStringHelper(this)
    .add(PROPERTY_SDK_REPO, androidSrc)
    .add(PROPERTY_SDK, androidDest)
    .add(PROPERTY_AVD, createAvd)
    .add(PROPERTY_TIMESTAMP, timestamp)
    .toString()

  fun hasValidSdkLocation(): Boolean {
    androidDest ?: return false
    val severity = PathValidator.forAndroidSdkLocation().validate(androidDest).severity
    return severity != Validator.Severity.ERROR
  }

  // TODO(qumeric): turn this object into a static field
  private object Holder {
    var INSTALLER_DATA = parse()
  }

  companion object {
    @JvmField
    val EMPTY = InstallerData(null, null, true, null, null)

    private fun parse(): InstallerData? {
      val properties = readProperties() ?: return null
      val androidSdkPath = properties[PROPERTY_SDK]
      val androidDest = if (androidSdkPath.isNullOrBlank()) null else File(androidSdkPath)
      return InstallerData(getIfPathExists(properties, PROPERTY_SDK_REPO), androidDest,
                           properties[PROPERTY_AVD]?.toBoolean() ?: true,
                           properties[PROPERTY_TIMESTAMP], properties[PROPERTY_VERSION])
    }

    @VisibleForTesting
    @Synchronized
    @JvmStatic
    fun set(data: InstallerData?) {
      Holder.INSTALLER_DATA = data
    }

    fun exists(): Boolean = Holder.INSTALLER_DATA != null

    @Synchronized
    @JvmStatic
    fun get(): InstallerData = Holder.INSTALLER_DATA!!
  }
}

private const val PROPERTY_SDK = "androidsdk.dir"
private const val PROPERTY_SDK_REPO = "androidsdk.repo"
private const val PROPERTY_TIMESTAMP = "install.timestamp"
private const val PROPERTY_AVD = "create.avd"
private const val PROPERTY_VERSION = "studio.version"

private val PATH_FIRST_RUN_PROPERTIES = FileUtil.join("studio", "installer", "firstrun.data")
private fun readProperties(): Map<String, String>? {
  try {
    // First run properties file contains a series of "key=value" lines.
    val file = File(AndroidLocation.getFolder(), PATH_FIRST_RUN_PROPERTIES)
    if (!file.isFile) {
      return null
    }
    return Files.readLines(file, Charsets.UTF_16LE)
      .filter { '=' in it }
      .map { it.split('=') }
      .filterNot { (k, _) -> k.isEmpty() }
      .associate { (k, v) -> k to v }
  }
  catch (e: AndroidLocation.AndroidLocationException) {
    log.error(e)
  }
  catch (e: IOException) {
    log.error(e)
  }
  return null
}

private fun getIfPathExists(properties: Map<String, String>, propertyName: String): File? {
  val path = properties[propertyName] ?: return null
  return File(path).takeIf { it.isDirectory }
}
