/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.layoutinspector.proto.SkiaParserServiceGrpc
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo.isWindows
import com.intellij.util.net.NetUtils
import com.intellij.util.ui.UIUtil
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import java.awt.Image
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.PixelInterleavedSampleModel
import java.awt.image.Raster
import java.io.File
import java.io.FileReader
import java.util.concurrent.CompletableFuture
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import kotlin.math.max

private const val PARSER_PACKAGE_NAME = "skiaparser"
private const val INITIAL_DELAY_MILLI_SECONDS = 10L
private const val MAX_DELAY_MILLI_SECONDS = 1000L
private const val MAX_TIMES_TO_RETRY = 10

object SkiaParser {
  private val unmarshaller = JAXBContext.newInstance(VersionMap::class.java).createUnmarshaller()
  private val devbuildServerInfo = ServerInfo(null, -1, -1)
  private var supportedVersionMap: Map<Int?, ServerInfo>? = null
  private val mapLock = Any()
  private const val VERSION_MAP_FILE_NAME = "version-map.xml"
  private val progressIndicator = StudioLoggerProgressIndicator(SkiaParser::class.java)

  fun getViewTree(data: ByteArray): InspectorView? {
    val server = runServer(data)
    val response = server.getViewTree(data)
    return response?.root?.let { buildTree(it) }
  }

  private fun buildTree(node: SkiaParser.InspectorView): InspectorView? {
    val width = node.width
    val height = node.height
    var image: Image? = null
    if (!node.image.isEmpty) {
      val buffer = DataBufferByte(node.image.toByteArray(), width * height * 4)
      val model = PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 4, 4 * width, intArrayOf(2, 1, 0, 3))
      val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
      val colorModel = ComponentColorModel(
        ColorSpace.getInstance(ColorSpace.CS_sRGB),
        true, false,
        Transparency.TRANSLUCENT,
        DataBuffer.TYPE_BYTE)
      val tmpimage = BufferedImage(colorModel, raster, false, null)
      image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
      val g = image.createGraphics()
      g.drawImage(tmpimage, 0, 0, null)
      g.dispose()
    }
    val res = InspectorView(node.id, node.type, node.x, node.y, width, height, image)
    node.childrenList.mapNotNull { buildTree(it) }.forEach { res.addChild(it) }
    return res
  }

  private fun runServer(data: ByteArray): ServerInfo {
    val server = findServerInfoForSkpVersion(getSkpVersion(data))
    server.runServer()
    return server
  }

  private fun getSkpVersion(data: ByteArray): Int {
    // SKPs start with "skiapict" in ascii
    if (data.slice(0..7) != "skiapict".toByteArray(Charsets.US_ASCII).asList() || data.size < 12) {
      throw Exception("invalid skia picture")
    }

    var skpVersion = 0
    var mult = 1
    // assume little endian for now
    for (i in 0..3) {
      skpVersion += data[i + 8] * mult
      mult = mult shl 8
    }
    return skpVersion
  }

  private fun findServerInfoForSkpVersion(skpVersion: Int): ServerInfo {
    if (supportedVersionMap == null) {
      readVersionMapping()
    }

    var serverInfo = findVersionInMap(skpVersion)
    // If we didn't find it in the map, maybe we have an old map. Download the latest and look again.
    if (serverInfo == null && downloadLatestVersion()) {
      serverInfo = findVersionInMap(skpVersion)
    }

    // We didn't find it. Maybe it hasn't been published yet, but is supported by the latest checked-in code. Try using the locally-built
    // server.
    return serverInfo ?: devbuildServerInfo
  }

  private fun findVersionInMap(skpVersion: Int): ServerInfo? {
    return synchronized(mapLock) {
      supportedVersionMap?.let {
        it.values.find { serverInfo -> serverInfo.skpVersionRange.contains(skpVersion) }
      }
    }
  }

  private fun downloadLatestVersion(): Boolean {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    // TODO: async and progress
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator,
                                 StudioDownloader(), StudioSettingsController.getInstance())

    val latestRemote = sdkHandler.getLatestRemotePackageForPrefix(
      PARSER_PACKAGE_NAME, false, progressIndicator) ?: return false
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
    val latestPackage = AndroidSdks.getInstance().tryToChooseSdkHandler().getLatestLocalPackageForPrefix(
      PARSER_PACKAGE_NAME, { true }, true, progressIndicator)
    if (latestPackage != null) {
      val mappingFile = File(latestPackage.location, VERSION_MAP_FILE_NAME)
      try {
        val map = unmarshaller.unmarshal(FileReader(mappingFile)) as VersionMap
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
        }
      }
      catch (e: Exception) {
        Logger.getInstance(SkiaParser::class.java).warn("Failed to parse mapping file", e)
      }
    }
  }
}

/**
 * Metadata for a skia parser server version. May or may not correspond to a server on disk, but has the capability to download it if not.
 * If [serverVersion] is null, corresponds to the locally-built1 server (in a dev build).
 */
private class ServerInfo(val serverVersion: Int?, skpStart: Int, skpEnd: Int?) {
  private val serverName = "skia-grpc-server" + if (isWindows) ".exe" else ""

  val skpVersionRange: IntRange = IntRange(skpStart, skpEnd ?: Int.MAX_VALUE)
  var client: SkiaParserServiceGrpc.SkiaParserServiceBlockingStub? = null
  var channel: ManagedChannel? = null
  var handler: OSProcessHandler? = null

  private val progressIndicator = StudioLoggerProgressIndicator(ServerInfo::class.java)
  private val packagePath = "${PARSER_PACKAGE_NAME}${RepoPackage.PATH_SEPARATOR}$serverVersion"

  private val serverPath: File? = findPath()

  private fun findPath(): File? {
    return if (serverVersion == null) {
      // devbuild
      File(PathManager.getHomePath(), "../../bazel-bin/tools/base/dynamic-layout-inspector/${serverName}")
    }
    else {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      val serverPackage = sdkHandler.getLocalPackage(packagePath, progressIndicator) ?: return null
      File(serverPackage.location, serverName)
    }
  }

  /**
   * Start the server if it isn't already running.
   *
   * If the server is killed by another process we detect it with process.isAlive.
   * Note that this will be a sub process of Android Studio and will terminate when
   * Android Studio process is terminated.
   */
  fun runServer() {
    if (client != null && channel?.isShutdown != true && channel?.isTerminated != true && handler?.process?.isAlive == true) {
      // already started
      return
    }
    if (serverPath?.exists() != true && !tryDownload()) {
      throw Exception("Unable to find server version $serverVersion")
    }
    val realPath = serverPath ?: throw Exception("Unable to find server version $serverVersion")

    // TODO: actually find and (re-)launch the server, and reconnect here if necessary.
    val localPort = NetUtils.findAvailableSocketPort()
    if (localPort < 0) {
      throw Exception("Unable to find available socket port")
    }

    channel = NettyChannelBuilder
      .forAddress("localhost", localPort)
      .usePlaintext(true)
      .maxMessageSize(512 * 1024 * 1024 - 1)
      .build()
    client = SkiaParserServiceGrpc.newBlockingStub(channel)

    handler = OSProcessHandler(GeneralCommandLine(realPath.absolutePath, localPort.toString()))
  }

  private fun tryDownload(): Boolean {
    if (serverVersion == null) {
      // devbuild, can't download
      return false
    }

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    // TODO: async and progress
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                                 progressIndicator,
                                 StudioDownloader(), StudioSettingsController.getInstance())

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

  fun getViewTree(data: ByteArray): SkiaParser.GetViewTreeResponse? {
    ping()
    return getViewTreeImpl(data)
  }

  // TODO: add ping functionality to the server?
  fun ping() {
    getViewTreeImpl(ByteArray(1))
  }

  private fun getViewTreeImpl(data: ByteArray): SkiaParser.GetViewTreeResponse? {
    val request = SkiaParser.GetViewTreeRequest.newBuilder().setSkp(ByteString.copyFrom(data)).build()
    return getViewTreeWithRetry(request)
  }

  private fun getViewTreeWithRetry(request: SkiaParser.GetViewTreeRequest): SkiaParser.GetViewTreeResponse? {
    var tries = 0
    var delay = INITIAL_DELAY_MILLI_SECONDS
    var lastException: StatusRuntimeException? = null
    while (tries < MAX_TIMES_TO_RETRY) {
      try {
        return client?.getViewTree(request)
      }
      catch (ex: StatusRuntimeException) {
        if (ex.status.code != Status.Code.UNAVAILABLE) {
          throw ex
        }
        Thread.sleep(delay)
        tries++
        delay = max(2 * delay, MAX_DELAY_MILLI_SECONDS)
        lastException = ex
      }
    }
    throw lastException!!
  }
}

@XmlRootElement(name="versionMapping")
private class VersionMap {
  @XmlElement(name = "server")
  val servers: MutableList<ServerVersionSpec> = mutableListOf()
}

private class ServerVersionSpec {
  @XmlAttribute(name = "version", required = true)
  val version: Int = 0

  @XmlAttribute(name = "skpStart", required = true)
  val skpStart: Int = 0

  @XmlAttribute(name = "skpEnd", required = false)
  val skpEnd: Int? = null
}