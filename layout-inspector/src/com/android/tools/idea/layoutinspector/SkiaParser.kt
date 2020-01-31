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

import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.layoutinspector.proto.SkiaParserServiceGrpc
import com.android.tools.idea.protobuf.ByteString
import com.intellij.util.ui.UIUtil
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.netty.NettyChannelBuilder
import java.awt.Image
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.DirectColorModel
import java.awt.image.PixelInterleavedSampleModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel

object SkiaParser {

  // TODO: create a named channel using a port that's dynamically determined when we launch the server.
  private val channel: ManagedChannel = NettyChannelBuilder
    .forAddress("localhost", 12345)
    .usePlaintext(true)
    .maxMessageSize(512 * 1024 * 1024 - 1)
    .build();

  // TODO: actually find and (re-)launch the server, and reconnect here if necessary.
  private val client: SkiaParserServiceGrpc.SkiaParserServiceBlockingStub = SkiaParserServiceGrpc.newBlockingStub(channel)

  fun getViewTree(data: ByteArray): InspectorView? {
    val request = SkiaParser.GetViewTreeRequest.newBuilder().setSkp(ByteString.copyFrom(data)).build()
    val response = client.getViewTree(request)
    return response.root?.let { buildTree(it) }
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
}
