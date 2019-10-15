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
package com.android.tools.idea.templates

import com.android.ide.common.signing.KeystoreHelper
import com.android.ide.common.signing.KeytoolException
import com.android.prefs.AndroidLocation
import com.android.prefs.AndroidLocation.AndroidLocationException
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.utils.StdLogger
import com.google.common.base.Strings
import com.google.common.io.BaseEncoding
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate

/**
 * Functions for dealing with singing configurations and keystore files.
 */
object KeystoreUtils {
  @Throws(KeytoolException::class, AndroidLocationException::class)
  @JvmStatic
  fun getOrCreateDefaultDebugKeystore(): File {
    val debugLocation = File(KeystoreHelper.defaultDebugKeystoreLocation())
    if (!debugLocation.exists()) {
      val keystoreDirectory = File(AndroidLocation.getFolder())

      if (!keystoreDirectory.canWrite()) {
        throw AndroidLocationException("Could not create debug keystore because \"$keystoreDirectory\" is not writable")
      }

      val logger = StdLogger(StdLogger.Level.ERROR)
      // Default values taken from http://developer.android.com/tools/publishing/app-signing.html
      // Keystore name: "debug.keystore"
      // Keystore password: "android"
      // Keystore alias: "androiddebugkey"
      // Key password: "android"
      KeystoreHelper.createDebugStore(null, debugLocation, "android", "android", "AndroidDebugKey", logger)
    }
    /** It should have been created by [KeystoreHelper.createDebugStore] */
    if (!debugLocation.exists()) {
      throw AndroidLocationException("Could not create debug keystore")
    }
    return debugLocation
  }

  /**
   * Get the debug keystore path.
   *
   * @return the keystore file.
   * @throws Exception if the keystore file could not be obtained.
   */
  @JvmStatic
  fun getDebugKeystore(facet: AndroidFacet): File {
    val gradleDebugKeystore = getGradleDebugKeystore(facet)
    if (gradleDebugKeystore != null) {
      return gradleDebugKeystore
    }

    val state = facet.configuration.state
    return if (state != null && !Strings.isNullOrEmpty(state.CUSTOM_DEBUG_KEYSTORE_PATH))
      File(state.CUSTOM_DEBUG_KEYSTORE_PATH)
    else
      getOrCreateDefaultDebugKeystore()
  }

  /**
   * Gets a custom debug keystore defined in the build.gradle file for this module
   *
   * @return null if there is no custom debug keystore configured, or if the project is not a Gradle project.
   */
  private fun getGradleDebugKeystore(facet: AndroidFacet): File? {
    val projectBuildModel = ProjectBuildModel.get(facet.module.project)
    val gradleBuildModel = projectBuildModel.getModuleBuildModel(facet.module) ?: return null

    val signingConfig = gradleBuildModel.android().signingConfigs().first { "debug" == it.name() } ?: return null

    val debugStorePath = signingConfig.storeFile().valueAsString() ?: return null
    val debugStoreFile = File(debugStorePath)
    if (debugStoreFile.isAbsolute) {
      return debugStoreFile
    }
    else {
      // Path is relative
      val moduleRoot = AndroidRootUtil.findModuleRootFolderPath(facet.module) ?: return debugStoreFile
      return File(moduleRoot, debugStorePath)
    }
  }

  /**
   * Get the SHA1 hash of a signing certificate inside a keystore, encoded as base16 (each byte separated by ':').
   *
   * @param keyStoreFile the keystore file. Must be readable.
   * @param keyAlias     the certificate alias to digest or null to indicate the first certificate in the keyStore
   * @throws Exception when the sha1 couldn't be computed for any reason.
   */
  @Throws(Exception::class)
  @JvmStatic
  @JvmOverloads
  fun sha1(keyStoreFile: File,
           keyAlias:/*When requesting the first certificate sha1*/ String? = null,
           keyStorePassword:/*When default android keystore password should be used*/ String? = null): String {
    val signingCert = getCertificate(keyStoreFile, keyAlias, keyStorePassword)
    try {
      val certBytes = MessageDigest.getInstance("SHA1").digest(signingCert.encoded)
      // Add a separator every 2 characters (i.e. every byte from hash)
      return BaseEncoding.base16().withSeparator(":", 2).encode(certBytes)
    }
    catch (e: Exception) {
      throw Exception("Could not compute SHA1 hash from certificate", e)
    }
  }

  /**
   * Returns the [Certificate] specified by the `certificateAlias` in the specified `keystoreFile`. When a null
   * `certificateAlias` is supplied then the first certificate read from the file is returned.
   */
  @Throws(Exception::class)
  private fun getCertificate(keyStoreFile: File,
                             certificateAlias:/*When requesting the first certificate sha1*/ String?,
                             keyStorePassword:/*When default android keystore password should be used*/ String?): Certificate {
    try {
      val keyStore = KeyStore.getInstance("JKS")
      keyStore.load(FileInputStream(keyStoreFile), (keyStorePassword ?: "android").toCharArray())

      return keyStore.getCertificate(certificateAlias ?: keyStore.aliases().nextElement())
    }
    catch (exception: GeneralSecurityException) {
      throw Exception("Could not extract certificate from file.", exception)
    }
    catch (exception: IOException) {
      throw Exception("Could not extract certificate from file.", exception)
    }
  }
}
