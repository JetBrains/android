/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.skia

import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.common.toInt
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.util.StudioPathManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

private const val PARSER_PACKAGE_NAME = "skiaparser"

// The minimum version of a skia parser component required by this version of studio.
// It's the parser's responsibility to be compatible with all supported studio versions.
private val minimumRevisions = mapOf(
  "skiaparser;1" to Revision(6),
  "skiaparser;2" to Revision(1)
)

fun interface SkiaParserServerConnectionFactory {
  fun createConnection(data: ByteArray): SkiaParserServerConnection
}

object SkiaParserServerConnectionFactoryImpl : SkiaParserServerConnectionFactory {

  /**
   * Metadata for a skia parser server version. May or may not correspond to a server on disk, but has the capability to download it if not.
   * If [serverVersion] is null, corresponds to the locally-built server (in a dev build).
   * Has the capability to create [SkiaParserServerConnection]s for the corresponding server.
   */
  @VisibleForTesting
  class ServerInfo(val serverVersion: Int?, skpStart: Int, skpEnd: Int?) {
    private val serverName = "skia-grpc-server" + if (SystemInfo.isWindows) ".exe" else ""

    val skpVersionRange: IntRange = IntRange(skpStart, skpEnd ?: Int.MAX_VALUE)

    private val progressIndicator = StudioLoggerProgressIndicator(ServerInfo::class.java)
    private val packagePath = "$PARSER_PACKAGE_NAME${RepoPackage.PATH_SEPARATOR}$serverVersion"

    private val serverPath: Path? = findPath()

    private fun findPath(): Path? {
      return if (serverVersion == null) {
        // devbuild
        if (StudioPathManager.isRunningFromSources()) {
          Paths.get(StudioPathManager.getBinariesRoot()).resolve("tools/base/dynamic-layout-inspector/skia/${serverName}")
        }
        else null
      }
      else {
        val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
        var serverPackage = sdkHandler.getLocalPackage(packagePath, progressIndicator) ?: return null
        // If the path isn't in the map at all, it's newer than this version of studio.
        if (minimumRevisions.getOrDefault(serverPackage.path, Revision(0)) > serverPackage.version) {
          // Current version is too old, try to update
          val updatablePackage = sdkHandler.getSdkManager(progressIndicator).packages.consolidatedPkgs[packagePath] ?: return null
          if (updatablePackage.isUpdate) {
            SdkQuickfixUtils.createDialogForPackages(null, listOf(updatablePackage), listOf(), false)?.show()
          }
          serverPackage = sdkHandler.getLocalPackage(packagePath, progressIndicator) ?: return null
          // we didn't update successfully
          if (minimumRevisions.getOrDefault(serverPackage.path, Revision(0)) > serverPackage.version) {
            return null
          }
        }
        serverPackage.location.resolve(serverName)
      }
    }

    @Slow
    fun createServer(): SkiaParserServerConnection {
      if (serverPath?.let { CancellableFileIo.exists(it) } != true && !tryDownload()) {
        throw Exception("Unable to find server version $serverVersion")
      }
      val realPath = serverPath ?: throw Exception("Unable to find server version $serverVersion")
      return SkiaParserServerConnection(realPath).apply { runServer() }
    }

    @Slow
    private fun tryDownload(): Boolean {
      if (serverVersion == null) {
        // devbuild, can't download
        return false
      }

      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      val sdkManager = sdkHandler.getSdkManager(progressIndicator)
      // TODO: async and progress
      sdkManager.loadSynchronously(
        RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
        progressIndicator,
        StudioDownloader(), StudioSettingsController.getInstance()
      )

      val updatablePackage = sdkManager.packages.consolidatedPkgs[packagePath] ?: return false
      if (updatablePackage.hasLocal() && !updatablePackage.isUpdate) {
        // latest already installed
        return false
      }

      SdkQuickfixUtils.createDialogForPackages(null, listOf(updatablePackage), listOf(), false)?.show() ?: return false
      // TODO: needed?
      sdkManager.reloadLocalIfNeeded(progressIndicator)
      val newPackage = sdkManager.packages.consolidatedPkgs[packagePath] ?: return false
      return newPackage.hasLocal() && !newPackage.isUpdate
    }
  }

  private val unmarshaller = JAXBContext.newInstance(VersionMap::class.java).createUnmarshaller()
  private val devbuildServerInfo = ServerInfo(null, -1, -1)
  private var supportedVersionMap: Map<Int?, ServerInfo>? = null
  private var latestPackagePath: String? = null
  private val mapLock = Any()
  private const val VERSION_MAP_FILE_NAME = "version-map.xml"
  private val progressIndicator = StudioLoggerProgressIndicator(SkiaParserServerConnection::class.java)

  override fun createConnection(data: ByteArray): SkiaParserServerConnection {
    val skpVersion = getSkpVersion(data)
    val serverInfo = findServerInfoForSkpVersion(skpVersion) ?: throw UnsupportedPictureVersionException(skpVersion)
    return serverInfo.createServer()
  }

  /**
   * Get the [ServerInfo] for a server that can render SKPs of the given [skpVersion], or null if no valid server is found.
   */
  @VisibleForTesting
  fun findServerInfoForSkpVersion(skpVersion: Int): ServerInfo? {
    if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER.get()) {
      return devbuildServerInfo
    }
    if (supportedVersionMap == null) {
      readVersionMapping()
    }

    var serverInfo = findVersionInMap(skpVersion)
    // If we didn't find it in the map, maybe we have an old map. Download the latest and look again.
    if (serverInfo == null) {
      val latest = getLatestParserPackage(AndroidSdks.getInstance().tryToChooseSdkHandler())
      if (latest?.path?.equals(latestPackagePath) != true) {
        readVersionMapping()
        serverInfo = findVersionInMap(skpVersion)
      }
      if (serverInfo == null && downloadLatestVersion()) {
        serverInfo = findVersionInMap(skpVersion)
      }
    }

    return serverInfo
  }

  private fun findVersionInMap(skpVersion: Int): ServerInfo? {
    return synchronized(mapLock) {
      supportedVersionMap?.values?.find { serverInfo -> serverInfo.skpVersionRange.contains(skpVersion) }
    }
  }

  @Slow
  private fun downloadLatestVersion(): Boolean {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    // TODO: async and progress
    sdkManager.loadSynchronously(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator,
      StudioDownloader(), StudioSettingsController.getInstance()
    )

    val latestRemote = sdkHandler.getLatestRemotePackageForPrefix(
      PARSER_PACKAGE_NAME, null, true, progressIndicator) ?: return false
    val maybeNewPackage = latestRemote.path
    val updatablePackage = sdkManager.packages.consolidatedPkgs[maybeNewPackage] ?: return false
    if (updatablePackage.hasLocal() && !updatablePackage.isUpdate) {
      // latest already installed
      return false
    }

    val installResult = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeAndWait {
      // TODO: probably don't show dialog ever?
      SdkQuickfixUtils.createDialogForPackages(null, listOf(updatablePackage), listOf(), false)?.show()

      val newPackage = sdkManager.packages.consolidatedPkgs[maybeNewPackage]
      if (newPackage == null || !newPackage.hasLocal() || newPackage.isUpdate) {
        // update cancelled?
        installResult.complete(false)
      }
      readVersionMapping()
      installResult.complete(true)
    }
    return installResult.get() && supportedVersionMap != null
  }

  private fun readVersionMapping() {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val latestPackage = getLatestParserPackage(sdkHandler)
    if (latestPackage != null) {
      val mappingFile = latestPackage.location.resolve(VERSION_MAP_FILE_NAME)
      try {
        val mapInputStream = CancellableFileIo.newInputStream(mappingFile)
        val map = unmarshaller.unmarshal(mapInputStream) as VersionMap
        synchronized(mapLock) {
          val newMap = mutableMapOf<Int?, ServerInfo>()
          for (spec in map.servers) {
            val existing = supportedVersionMap?.get(spec.version)
            if (existing?.skpVersionRange?.start == spec.skpStart && existing.skpVersionRange.last == spec.skpEnd) {
              newMap[spec.version] = existing
            }
            else {
              newMap[spec.version] = ServerInfo(spec.version, spec.skpStart, spec.skpEnd)
            }
          }
          supportedVersionMap = newMap
          latestPackagePath = latestPackage.path
        }
      }
      catch (e: Exception) {
        Logger.getInstance(SkiaParserServerConnectionFactory::class.java).warn("Failed to parse mapping file", e)
      }
    }
  }

  private fun getLatestParserPackage(sdkHandler: AndroidSdkHandler): LocalPackage? {
    return sdkHandler.getLatestLocalPackageForPrefix(PARSER_PACKAGE_NAME, { true }, true, progressIndicator)
  }

  @VisibleForTesting
  fun getSkpVersion(data: ByteArray): Int {
    // SKPs start with "skiapict" in ascii
    if (data.slice(0..7) != "skiapict".toByteArray(Charsets.US_ASCII).asList() || data.size < 12) {
      throw InvalidPictureException()
    }
    return data.sliceArray(8..11).toInt()
  }
}

@XmlRootElement(name="versionMapping")
private class VersionMap {
  @XmlElement(name = "server")
  val servers: MutableList<ServerVersionSpec> = mutableListOf()
}

/**
 * The `SkPicture` versions supported by a given `skiaparser` server version. Used by JAXB to parse `version-map.xml`.
 */
private class ServerVersionSpec {
  /** The version of the `skiaparser` package (e.g. the `1` in the sdk package `skiaparser;1` */
  @XmlAttribute(name = "version", required = true)
  val version: Int = 0

  /** The first `SkPicture` version supported by this server. */
  @XmlAttribute(name = "skpStart", required = true)
  val skpStart: Int = 0

  /** The last `SkPicture` version supported by this server. */
  @XmlAttribute(name = "skpEnd", required = false)
  val skpEnd: Int? = null
}

/**
 * Thrown if a request is made to create a server for a `SkPicture` with a version that we don't know how to render.
 */
class UnsupportedPictureVersionException(val version: Int) : Exception()

/**
 * Thrown if a request is made to create a server for something that doesn't look like a valid `SkPicture`.
 */
class InvalidPictureException : Exception()

/**
 * Thrown if parsing a `SkPicture` fails in the parser.
 */
class ParsingFailedException : Exception()
