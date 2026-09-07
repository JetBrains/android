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
package com.android.tools.idea.rendering.webp

import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.webp.WebpImageWriterSpi
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.max
import org.w3c.dom.Node

/** Helper object for WebP encoding utilities (including multi-frame animations from animated GIFs) and decoding WebP frames for preview. */
object WebpEncoding {
  private const val ANIMATION_FLAG = 0x02
  private const val ALPHA_FLAG = 0x10

  // WebP ANMF flags: bit 0: dispose (0 = none, 1 = background), bit 1: blend (0 = blend, 1 = no blend)
  private const val ANMF_NO_BLEND = 0x02

  // WebP RIFF chunk tags
  private const val TAG_RIFF = "RIFF"
  private const val TAG_WEBP = "WEBP"
  private const val TAG_VP8X = "VP8X"
  private const val TAG_ANIM = "ANIM"
  private const val TAG_ANMF = "ANMF"
  private const val TAG_VP8 = "VP8 "
  private const val TAG_VP8L = "VP8L"
  private const val TAG_ALPH = "ALPH"

  // GIF metadata element and attribute names
  private const val NODE_LOGICAL_SCREEN_DESCRIPTOR = "LogicalScreenDescriptor"
  private const val ATTR_LOGICAL_SCREEN_WIDTH = "logicalScreenWidth"
  private const val ATTR_LOGICAL_SCREEN_HEIGHT = "logicalScreenHeight"

  private const val NODE_APPLICATION_EXTENSION = "ApplicationExtension"
  private const val ATTR_APPLICATION_ID = "applicationID"
  private const val ATTR_AUTHENTICATION_CODE = "authenticationCode"
  private const val APP_ID_NETSCAPE = "NETSCAPE"
  private const val AUTH_CODE_2_0 = "2.0"

  private const val NODE_IMAGE_DESCRIPTOR = "ImageDescriptor"
  private const val ATTR_IMAGE_LEFT_POSITION = "imageLeftPosition"
  private const val ATTR_IMAGE_TOP_POSITION = "imageTopPosition"

  private const val NODE_GRAPHIC_CONTROL_EXTENSION = "GraphicControlExtension"
  private const val ATTR_DELAY_TIME = "delayTime"
  private const val ATTR_DISPOSAL_METHOD = "disposalMethod"

  private enum class GifDisposalMethod(val value: String) {
    NONE("none"),
    DO_NOT_DISPOSE("doNotDispose"),
    RESTORE_TO_BACKGROUND_COLOR("restoreToBackgroundColor"),
    RESTORE_TO_PREVIOUS("restoreToPrevious");

    companion object {
      fun fromString(value: String?): GifDisposalMethod {
        if (value == null) return NONE
        return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: NONE
      }
    }
  }

  /** Reads an animated GIF from the provided [ImageReader] and writes an Animated WebP to [outputStream]. */
  @JvmStatic
  @Throws(IOException::class)
  fun writeAnimatedGif(
    reader: ImageReader,
    numImages: Int,
    outputStream: OutputStream,
    settings: WebpConversionSettings,
  ) {
    val loopCount = extractLoopCount(reader)
    val logicalDimensions = extractLogicalScreenDimensions(reader)

    var canvasWidth = logicalDimensions?.get(0) ?: 0
    var canvasHeight = logicalDimensions?.get(1) ?: 0

    // First pass if dimensions not found: find maximum bounds
    if (canvasWidth <= 0 || canvasHeight <= 0) {
      for (i in 0 until numImages) {
        val w = reader.getWidth(i)
        val h = reader.getHeight(i)
        canvasWidth = max(canvasWidth, w)
        canvasHeight = max(canvasHeight, h)
      }
    }

    if (canvasWidth <= 0 || canvasHeight <= 0) {
      throw IOException("Unable to determine dimensions for animated image")
    }

    val frames = ArrayList<FrameData>(numImages)
    var hasAlpha = false

    //noinspection UndesirableClassUsage
    val canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
    val canvasG = canvas.createGraphics()

    var backupCanvas: BufferedImage? = null
    var prevDisposalMethod = GifDisposalMethod.NONE
    var prevLeft = 0
    var prevTop = 0
    var prevWidth = canvasWidth
    var prevHeight = canvasHeight

    for (i in 0 until numImages) {
      val rawFrame = reader.read(i) ?: continue

      val meta = extractFrameMetadata(reader, i)
      val left = meta?.left ?: 0
      val top = meta?.top ?: 0
      val width = rawFrame.width
      val height = rawFrame.height
      val durationMs = meta?.durationMs ?: 100
      val disposalMethod = meta?.disposalMethod ?: GifDisposalMethod.NONE

      // Handle disposal of previous frame
      when (prevDisposalMethod) {
        GifDisposalMethod.RESTORE_TO_BACKGROUND_COLOR -> {
          canvasG.composite = AlphaComposite.Clear
          canvasG.fillRect(prevLeft, prevTop, prevWidth, prevHeight)
          canvasG.composite = AlphaComposite.SrcOver
        }
        GifDisposalMethod.RESTORE_TO_PREVIOUS -> {
          backupCanvas?.let { backup ->
            canvasG.composite = AlphaComposite.Src
            canvasG.drawImage(backup, 0, 0, null)
            canvasG.composite = AlphaComposite.SrcOver
          }
        }
        GifDisposalMethod.NONE,
        GifDisposalMethod.DO_NOT_DISPOSE -> {
          // Do nothing
        }
      }

      // If current frame requests restoreToPrevious, save current canvas state before drawing
      backupCanvas = if (disposalMethod == GifDisposalMethod.RESTORE_TO_PREVIOUS) copyImage(canvas) else null

      // Draw the new frame onto the cumulative canvas
      canvasG.drawImage(rawFrame, left, top, null)

      val frameSnapshot = copyImage(canvas)
      if (ImageUtils.isNonOpaque(frameSnapshot)) {
        hasAlpha = true
      }

      val frameBaos = ByteArrayOutputStream()
      WebpImageWriterSpi.writeImage(frameSnapshot, frameBaos, settings.lossless, settings.quality)
      val singleFrameWebp = frameBaos.toByteArray()

      val frameSubchunks = extractFrameSubchunks(singleFrameWebp)
      frames.add(FrameData(canvasWidth, canvasHeight, durationMs, frameSubchunks))

      prevDisposalMethod = disposalMethod
      prevLeft = left
      prevTop = top
      prevWidth = width
      prevHeight = height
    }

    canvasG.dispose()

    if (frames.isEmpty()) {
      throw IOException("No valid frames found in animated image")
    }

    writeAnimatedWebp(canvasWidth, canvasHeight, loopCount, hasAlpha, frames, outputStream)
  }

  private fun copyImage(src: BufferedImage): BufferedImage {
    //noinspection UndesirableClassUsage
    val copy = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    val g = copy.createGraphics()
    g.composite = AlphaComposite.Src
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return copy
  }

  private fun extractLogicalScreenDimensions(reader: ImageReader): IntArray? {
    return try {
      val streamMetadata = reader.streamMetadata ?: return null
      val metaFormat = streamMetadata.nativeMetadataFormatName ?: streamMetadata.metadataFormatNames?.firstOrNull() ?: return null

      val root = streamMetadata.getAsTree(metaFormat) as? IIOMetadataNode ?: return null
      val children = root.childNodes
      for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is IIOMetadataNode && NODE_LOGICAL_SCREEN_DESCRIPTOR.equals(node.nodeName, ignoreCase = true)) {
          val wStr = node.getAttribute(ATTR_LOGICAL_SCREEN_WIDTH)
          val hStr = node.getAttribute(ATTR_LOGICAL_SCREEN_HEIGHT)
          if (!wStr.isNullOrEmpty() && !hStr.isNullOrEmpty()) {
            return intArrayOf(wStr.toInt(), hStr.toInt())
          }
        }
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  private fun extractLoopCount(reader: ImageReader): Int {
    return try {
      val streamMetadata = reader.streamMetadata ?: return 0
      val formats = streamMetadata.metadataFormatNames
      if (formats != null) {
        for (format in formats) {
          val root = streamMetadata.getAsTree(format)
          val count = findLoopCountInTree(root)
          if (count >= 0) {
            return count
          }
        }
      }
      0 // Default to infinite loop
    } catch (_: Exception) {
      0
    }
  }

  private fun findLoopCountInTree(node: Node): Int {
    if (node is IIOMetadataNode && NODE_APPLICATION_EXTENSION.equals(node.nodeName, ignoreCase = true)) {
      val appID = node.getAttribute(ATTR_APPLICATION_ID)
      val authCode = node.getAttribute(ATTR_AUTHENTICATION_CODE)
      if (APP_ID_NETSCAPE.equals(appID, ignoreCase = true) && AUTH_CODE_2_0.equals(authCode, ignoreCase = true)) {
        val userObj = node.userObject
        if (userObj is ByteArray && userObj.size >= 3 && userObj[0].toInt() == 1) {
          return (userObj[1].toInt() and 0xFF) or ((userObj[2].toInt() and 0xFF) shl 8)
        }
      }
    }
    val children = node.childNodes
    for (i in 0 until children.length) {
      val count = findLoopCountInTree(children.item(i))
      if (count >= 0) {
        return count
      }
    }
    return -1
  }

  private fun extractFrameMetadata(reader: ImageReader, imageIndex: Int): FrameMetadata? {
    return try {
      val metadata = reader.getImageMetadata(imageIndex) ?: return null
      val metaFormat = metadata.nativeMetadataFormatName ?: metadata.metadataFormatNames?.firstOrNull() ?: return null

      val root = metadata.getAsTree(metaFormat) as? IIOMetadataNode ?: return null

      var left = 0
      var top = 0
      var durationMs = 100
      var disposalMethod = GifDisposalMethod.NONE

      val children = root.childNodes
      for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is IIOMetadataNode) {
          if (NODE_IMAGE_DESCRIPTOR.equals(node.nodeName, ignoreCase = true)) {
            val leftStr = node.getAttribute(ATTR_IMAGE_LEFT_POSITION)
            val topStr = node.getAttribute(ATTR_IMAGE_TOP_POSITION)
            if (!leftStr.isNullOrEmpty()) {
              left = leftStr.toInt()
            }
            if (!topStr.isNullOrEmpty()) {
              top = topStr.toInt()
            }
          } else if (NODE_GRAPHIC_CONTROL_EXTENSION.equals(node.nodeName, ignoreCase = true)) {
            val delayStr = node.getAttribute(ATTR_DELAY_TIME)
            if (!delayStr.isNullOrEmpty()) {
              val delayCs = delayStr.toInt()
              durationMs = if (delayCs > 1) delayCs * 10 else 100
            }
            disposalMethod = GifDisposalMethod.fromString(node.getAttribute(ATTR_DISPOSAL_METHOD))
          }
        }
      }
      FrameMetadata(left, top, durationMs, disposalMethod)
    } catch (_: Exception) {
      null
    }
  }

  @Throws(IOException::class)
  private fun extractFrameSubchunks(singleFrameWebp: ByteArray): ByteArray {
    if (singleFrameWebp.size < 12) {
      throw IOException("Invalid WebP bitstream")
    }
    val subchunks = ByteArrayOutputStream()
    var offset = 12 // Skip RIFF header
    while (offset + 8 <= singleFrameWebp.size) {
      val tag = String(singleFrameWebp, offset, 4, StandardCharsets.US_ASCII)
      val chunkSize = getLE32(singleFrameWebp, offset + 4)
      val paddedSize = chunkSize + (chunkSize and 1L)
      val chunkTotalSize = (8 + paddedSize).toInt()

      if (offset + chunkTotalSize > singleFrameWebp.size) {
        break
      }

      if (tag == TAG_VP8 || tag == TAG_VP8L || tag == TAG_ALPH) {
        subchunks.write(singleFrameWebp, offset, chunkTotalSize)
      }
      offset += chunkTotalSize
    }
    return subchunks.toByteArray()
  }

  @Throws(IOException::class)
  private fun writeAnimatedWebp(
    canvasWidth: Int,
    canvasHeight: Int,
    loopCount: Int,
    hasAlpha: Boolean,
    frames: List<FrameData>,
    out: OutputStream,
  ) {
    val payloadStream = ByteArrayOutputStream()

    // 1. VP8X Chunk (10 bytes payload)
    payloadStream.write(TAG_VP8X.toByteArray(StandardCharsets.US_ASCII))
    putLE32(payloadStream, 10L)
    var flags = ANIMATION_FLAG
    if (hasAlpha) {
      flags = flags or ALPHA_FLAG
    }
    payloadStream.write(flags)
    payloadStream.write(0) // Reserved
    payloadStream.write(0)
    payloadStream.write(0)
    putLE24(payloadStream, canvasWidth - 1)
    putLE24(payloadStream, canvasHeight - 1)

    // 2. ANIM Chunk (6 bytes payload)
    payloadStream.write(TAG_ANIM.toByteArray(StandardCharsets.US_ASCII))
    putLE32(payloadStream, 6L)
    putLE32(payloadStream, 0L) // Background color: 0x00000000 (BGRA)
    putLE16(payloadStream, loopCount)

    // 3. ANMF Chunks for each frame
    for (frame in frames) {
      val anmfPayloadSize = 16 + frame.subchunks.size
      payloadStream.write(TAG_ANMF.toByteArray(StandardCharsets.US_ASCII))
      putLE32(payloadStream, anmfPayloadSize.toLong())

      // ANMF 16-byte header
      putLE24(payloadStream, 0) // Frame X / 2
      putLE24(payloadStream, 0) // Frame Y / 2
      putLE24(payloadStream, frame.width - 1)
      putLE24(payloadStream, frame.height - 1)
      putLE24(payloadStream, frame.durationMs)

      // Bit 0: dispose method (0 = none), Bit 1: blend method (1 = no blend, directly overwrite canvas)
      payloadStream.write(ANMF_NO_BLEND)

      // Subchunks (ALPH + VP8 / VP8L)
      payloadStream.write(frame.subchunks)

      // ANMF padding byte if payload size is odd
      if ((anmfPayloadSize and 1) != 0) {
        payloadStream.write(0)
      }
    }

    val payload = payloadStream.toByteArray()

    // RIFF Container Header
    out.write(TAG_RIFF.toByteArray(StandardCharsets.US_ASCII))
    putLE32(out, payload.size.toLong() + 4L) // File size - 8 = payload size + 4 (for "WEBP")
    out.write(TAG_WEBP.toByteArray(StandardCharsets.US_ASCII))
    out.write(payload)
  }

  private fun getLE32(b: ByteArray, offset: Int): Long {
    return (b[offset].toLong() and 0xFFL) or
      ((b[offset + 1].toLong() and 0xFFL) shl 8) or
      ((b[offset + 2].toLong() and 0xFFL) shl 16) or
      ((b[offset + 3].toLong() and 0xFFL) shl 24)
  }

  private fun getLE24(b: ByteArray, offset: Int): Int {
    return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8) or ((b[offset + 2].toInt() and 0xFF) shl 16)
  }

  /** Decodes a WebP image, extracting and rendering the first frame if the image is an Animated WebP. */
  @JvmStatic
  @Throws(IOException::class)
  fun decodeWebp(webpBytes: ByteArray): BufferedImage? {
    if (webpBytes.size < 12) {
      return ImageIO.read(ByteArrayInputStream(webpBytes))
    }

    val riff = String(webpBytes, 0, 4, StandardCharsets.US_ASCII)
    val webp = String(webpBytes, 8, 4, StandardCharsets.US_ASCII)
    if (riff != TAG_RIFF || webp != TAG_WEBP) {
      return ImageIO.read(ByteArrayInputStream(webpBytes))
    }

    try {
      var offset = 12
      var canvasWidth = 0
      var canvasHeight = 0
      var isAnimated = false

      while (offset + 8 <= webpBytes.size) {
        val tag = String(webpBytes, offset, 4, StandardCharsets.US_ASCII)
        val chunkSize = getLE32(webpBytes, offset + 4)
        val paddedSize = chunkSize + (chunkSize and 1L)
        val payloadOffset = offset + 8

        if (payloadOffset + chunkSize > webpBytes.size) {
          break
        }

        if (tag == TAG_VP8X && chunkSize >= 10L) {
          val flags = webpBytes[payloadOffset].toInt() and 0xFF
          if ((flags and ANIMATION_FLAG) != 0) {
            isAnimated = true
            canvasWidth = 1 + getLE24(webpBytes, payloadOffset + 4)
            canvasHeight = 1 + getLE24(webpBytes, payloadOffset + 7)
          }
        } else if (tag == TAG_ANMF && isAnimated && chunkSize >= 16L) {
          val frameX = getLE24(webpBytes, payloadOffset) * 2
          val frameY = getLE24(webpBytes, payloadOffset + 3) * 2
          val frameWidth = 1 + getLE24(webpBytes, payloadOffset + 6)
          val frameHeight = 1 + getLE24(webpBytes, payloadOffset + 9)

          val subchunksStart = payloadOffset + 16
          val subchunksLength = (chunkSize - 16L).toInt()

          if (subchunksLength >= 4) {
            val subTag = String(webpBytes, subchunksStart, 4, StandardCharsets.US_ASCII)
            val singleFrameWebp: ByteArray
            if (subTag == TAG_ALPH) {
              val out = ByteArrayOutputStream(12 + 18 + subchunksLength)
              out.write(TAG_RIFF.toByteArray(StandardCharsets.US_ASCII))
              putLE32(out, (4 + 18 + subchunksLength).toLong())
              out.write(TAG_WEBP.toByteArray(StandardCharsets.US_ASCII))
              out.write(TAG_VP8X.toByteArray(StandardCharsets.US_ASCII))
              putLE32(out, 10L)
              out.write(ALPHA_FLAG)
              out.write(0)
              out.write(0)
              out.write(0)
              putLE24(out, frameWidth - 1)
              putLE24(out, frameHeight - 1)
              out.write(webpBytes, subchunksStart, subchunksLength)
              singleFrameWebp = out.toByteArray()
            } else {
              val out = ByteArrayOutputStream(12 + subchunksLength)
              out.write(TAG_RIFF.toByteArray(StandardCharsets.US_ASCII))
              putLE32(out, (4 + subchunksLength).toLong())
              out.write(TAG_WEBP.toByteArray(StandardCharsets.US_ASCII))
              out.write(webpBytes, subchunksStart, subchunksLength)
              singleFrameWebp = out.toByteArray()
            }

            val frameImage = ImageIO.read(ByteArrayInputStream(singleFrameWebp))
            if (frameImage != null) {
              if (
                canvasWidth <= 0 ||
                  canvasHeight <= 0 ||
                  (frameX == 0 && frameY == 0 && frameWidth == canvasWidth && frameHeight == canvasHeight)
              ) {
                return frameImage
              }
              //noinspection UndesirableClassUsage
              val canvasImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
              val g = canvasImage.createGraphics()
              g.drawImage(frameImage, frameX, frameY, null)
              g.dispose()
              return canvasImage
            }
          }
        }

        offset = (payloadOffset + paddedSize).toInt()
      }
    } catch (_: Exception) {}

    return ImageIO.read(ByteArrayInputStream(webpBytes))
  }

  private fun putLE16(out: OutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value shr 8) and 0xFF)
  }

  private fun putLE24(out: OutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value shr 8) and 0xFF)
    out.write((value shr 16) and 0xFF)
  }

  private fun putLE32(out: OutputStream, value: Long) {
    out.write((value and 0xFFL).toInt())
    out.write(((value shr 8) and 0xFFL).toInt())
    out.write(((value shr 16) and 0xFFL).toInt())
    out.write(((value shr 24) and 0xFFL).toInt())
  }

  private data class FrameMetadata(
    val left: Int,
    val top: Int,
    val durationMs: Int,
    val disposalMethod: GifDisposalMethod,
  )

  private class FrameData(
    val width: Int,
    val height: Int,
    val durationMs: Int,
    val subchunks: ByteArray,
  )
}
