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

import com.android.annotations.concurrency.Slow
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.model.SkiaViewNode
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.layoutinspector.proto.SkiaParserServiceGrpc
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo.isWindows
import com.intellij.util.net.NetUtils
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import java.awt.Point
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import kotlin.math.min

private const val PARSER_PACKAGE_NAME = "skiaparser"
private const val INITIAL_DELAY_MILLI_SECONDS = 10L
private const val MAX_DELAY_MILLI_SECONDS = 1000L
private const val MAX_TIMES_TO_RETRY = 10

class InvalidPictureException : Exception()
class UnsupportedPictureVersionException(val version: Int) : Exception()

data class RequestedNodeInfo(val drawId: Long, val width: Int, val height: Int, val x: Int, val y: Int)

interface SkiaParserService {
  @Throws(InvalidPictureException::class)
  fun getViewTree(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double,
    isInterrupted: () -> Boolean = { false }
  ): SkiaViewNode?

  fun shutdownAll()
}

// The minimum version of a skia parser component required by this version of studio.
// It's the parser's responsibility to be compatible with all supported studio versions.
private val minimumRevisions = mapOf(
  "skiaparser;1" to Revision(4)
)

object SkiaParser : SkiaParserService {
  private val unmarshaller = JAXBContext.newInstance(VersionMap::class.java).createUnmarshaller()
  private val devbuildServerInfo = ServerInfo(null, -1, -1)
  private var supportedVersionMap: Map<Int?, ServerInfo>? = null
  private var latestPackagePath: String? = null
  private val mapLock = Any()
  private const val VERSION_MAP_FILE_NAME = "version-map.xml"
  private val progressIndicator = StudioLoggerProgressIndicator(SkiaParser::class.java)

  @Slow
  @Throws(InvalidPictureException::class)
  override fun getViewTree(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double,
    isInterrupted: () -> Boolean
  ): SkiaViewNode? {
    val server = runServer(data) ?: throw UnsupportedPictureVersionException(getSkpVersion(data))
    val response = server.getViewTree(data, requestedNodes, scale)
    return response?.root?.let {
      try {
        buildTree(it, isInterrupted, requestedNodes.associateBy { req -> req.drawId })
      }
      catch (interruptedException: InterruptedException) {
        null
      }
    }
  }

  @Slow
  override fun shutdownAll() {
    supportedVersionMap?.values?.forEach { it.shutdown() }
    devbuildServerInfo.shutdown()
  }

  @VisibleForTesting
  fun buildTree(
    node: SkiaParser.InspectorView,
    isInterrupted: () -> Boolean,
    drawIdToRequest: Map<Long, RequestedNodeInfo>
  ): SkiaViewNode? {
    if (isInterrupted()) {
      throw InterruptedException()
    }

    return if (!node.image.isEmpty) {
      val width = if (node.width > 0) node.width else drawIdToRequest[node.id]?.width ?: return null
      val height = if (node.height > 0) node.height else drawIdToRequest[node.id]?.height ?: return null
      val intArray = IntArray(width * height)
      node.image.asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(intArray)
      val buffer = DataBufferInt(intArray, width * height)
      val model = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, intArrayOf(0xff0000, 0xff00, 0xff, 0xff000000.toInt()))
      val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
      val colorModel = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                        32, 0xff0000, 0xff00, 0xff, 0xff000000.toInt(), false, DataBuffer.TYPE_INT)
      @Suppress("UndesirableClassUsage")
      SkiaViewNode(node.id, BufferedImage(colorModel, raster, false, null))
    }
    else {
      SkiaViewNode(node.id, node.childrenList.mapNotNull { buildTree(it, isInterrupted, drawIdToRequest) })
    }
  }

  /**
   * Run a server that can parse the given [data], or null if no appropriate server version is available.
   */
  @Slow
  private fun runServer(data: ByteArray): ServerInfo? {
    val server = findServerInfoForSkpVersion(getSkpVersion(data)) ?: return null
    server.runServer()
    return server
  }

  @VisibleForTesting
  fun getSkpVersion(data: ByteArray): Int {
    // SKPs start with "skiapict" in ascii
    if (data.slice(0..7) != "skiapict".toByteArray(Charsets.US_ASCII).asList() || data.size < 12) {
      throw InvalidPictureException()
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
      supportedVersionMap?.let {
        it.values.find { serverInfo -> serverInfo.skpVersionRange.contains(skpVersion) }
      }
    }
  }

  @Slow
  private fun downloadLatestVersion(): Boolean {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    // TODO: async and progress
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator,
                                 StudioDownloader(), StudioSettingsController.getInstance())

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
      val mappingFile = File(latestPackage.location, VERSION_MAP_FILE_NAME)
      try {
        val mapInputStream = sdkHandler.fileOp.newFileInputStream(mappingFile)
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
        Logger.getInstance(SkiaParser::class.java).warn("Failed to parse mapping file", e)
      }
    }
  }

  private fun getLatestParserPackage(sdkHandler: AndroidSdkHandler): LocalPackage? {
    return sdkHandler.getLatestLocalPackageForPrefix(PARSER_PACKAGE_NAME, { true }, true, progressIndicator)
  }
}

/**
 * Metadata for a skia parser server version. May or may not correspond to a server on disk, but has the capability to download it if not.
 * If [serverVersion] is null, corresponds to the locally-built1 server (in a dev build).
 */
@VisibleForTesting
class ServerInfo(val serverVersion: Int?, skpStart: Int, skpEnd: Int?) {
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
  @Slow
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
      .usePlaintext()
      .maxInboundMessageSize(512 * 1024 * 1024 - 1)
      .build()
    client = SkiaParserServiceGrpc.newBlockingStub(channel)
    handler = OSProcessHandler.Silent(GeneralCommandLine(realPath.absolutePath, localPort.toString()))
    handler!!.addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        // TODO(b/151639359) // We get a 137 when we terminate the server. Silence this error.
        if (event.exitCode != 0 && event.exitCode != 137) {
          Logger.getInstance(SkiaParser::class.java).error("SkiaServer terminated exitCode: ${event.exitCode}  text: ${event.text}")
        }
        else {
          Logger.getInstance(SkiaParser::class.java).info("SkiaServer terminated successfully")
        }
      }

      override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        Logger.getInstance(SkiaParser::class.java).debug("SkiaServer willTerminate")
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        Logger.getInstance(SkiaParser::class.java).info("SkiaServer Message: ${event.text}")
      }
    })
    handler?.startNotify()
  }

  @Slow
  fun shutdown() {
    channel?.shutdownNow()
    channel?.awaitTermination(1, TimeUnit.SECONDS)
    channel = null
    client = null
    handler?.destroyProcess()
    handler = null
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

  @Slow
  fun getViewTree(data: ByteArray, requestedNodes: Iterable<RequestedNodeInfo>, scale: Double): SkiaParser.GetViewTreeResponse? {
    ping()
    return getViewTreeImpl(data, requestedNodes, scale)
  }

  // TODO: add ping functionality to the server?
  @Slow
  fun ping() {
    getViewTreeImpl(ByteArray(1), emptyList(), 0.0)
  }

  private fun getViewTreeImpl(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double
  ): SkiaParser.GetViewTreeResponse? {
    val request = SkiaParser.GetViewTreeRequest.newBuilder()
      .setVersion(1)
      .setSkp(ByteString.copyFrom(data))
      .addAllRequestedNodes(requestedNodes.map {
        SkiaParser.RequestedNodeInfo.newBuilder()
          .setId(it.drawId)
          .setWidth(it.width)
          .setHeight(it.height)
          .setX(it.x)
          .setY(it.y)
          .build()
      })
      .setScale(scale.toFloat())
      .build()
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
        delay = min(2 * delay, MAX_DELAY_MILLI_SECONDS)
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
