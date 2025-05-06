/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs.honeyface

import com.android.SdkConstants.TAG_WATCH_FACE
import com.intellij.ui.ColorUtil
import java.awt.Color
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.nameWithoutExtension
import kotlin.math.roundToInt
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Converts [HoneyFace]s to XML [Document]s. */
internal class HoneyFaceXmlConverter {
  fun toXml(honeyFace: HoneyFace): Document {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    val watchFace =
      document.createElement(TAG_WATCH_FACE).also {
        it.setAttribute("width", honeyFace.settings.width.toString())
        it.setAttribute("height", honeyFace.settings.height.toString())
        if (honeyFace.settings.isCropped) {
          val clipShape =
            when (honeyFace.settings.shape) {
              "circle" -> "CIRCLE"
              "rectangle" -> "RECTANGLE"
              else -> "NONE"
            }
          it.setAttribute("clipShape", clipShape)
        }
      }

    val scene = document.createElement("Scene")
    val backgroundColor = honeyFace.background.single().categories.color.properties.color.value
    scene.setAttribute("backgroundColor", backgroundColor.toHtmlString())

    // The scene object is a list that only has a single root item
    val rootSceneItem = honeyFace.scene.single()
    assert(rootSceneItem.name == "Root")
    rootSceneItem.child
      ?.mapNotNull { document.createSceneElement(it) }
      ?.forEach { scene.appendChild(it) }
    watchFace.appendChild(scene)

    document.appendChild(watchFace)
    return document
  }

  private fun Document.createSceneElement(sceneItem: SceneItem): Element? {
    if (!sceneItem.visible && !sceneItem.visibleOnAOD) {
      return null
    }
    val sceneElement =
      when (sceneItem.type) {
        "image" -> createImageElement(sceneItem)
        "group" -> createElement("Group")
        else -> return null
      }
    sceneElement.setAttribute("name", sceneItem.name)
    sceneElement.setAttribute("alpha", if (!sceneItem.visible) "0" else "255")

    sceneItem.categories.placement?.let { placementCategory ->
      val position = placementCategory.properties.position.value
      sceneElement.setAttribute("x", position.x.roundToInt().toString())
      sceneElement.setAttribute("y", position.y.roundToInt().toString())
    }

    sceneItem.categories.dimension?.let { dimensionsCategory ->
      val width = dimensionsCategory.properties.width.value
      val height = dimensionsCategory.properties.height.value
      sceneElement.setAttribute("width", width.roundToInt().toString())
      sceneElement.setAttribute("height", height.roundToInt().toString())
    }

    sceneItem.child?.mapNotNull { createSceneElement(it) }?.forEach { sceneElement.appendChild(it) }
    return sceneElement
  }

  private fun Document.createImageElement(sceneItem: SceneItem): Element {
    val partImageElement = createElement("PartImage")
    sceneItem.categories.image?.properties?.let { imageProperties ->
      val imageElement = createElement("Image")
      val imagePath = imageProperties.image.value
      imageElement.setAttribute("resource", imagePath.fileName.nameWithoutExtension)

      partImageElement.appendChild(imageElement)
    }

    return partImageElement
  }
}

private fun RgbaColor.toHtmlString(): String = Color(r, g, b, a).toHtmlString()

private fun HslaColor.toHtmlString(): String {
  val color = Color.getHSBColor(h, s, l)
  val colorWithAlpha = Color(color.red, color.green, color.blue, (a * 255).toInt())
  return colorWithAlpha.toHtmlString()
}

/**
 * We use this function instead of [ColorUtil.toHtmlColor] as that method puts the alpha at the end
 * (e.g. #000000ff for opaque black) instead of at the front where it needs to be (e.g #ff000000).
 */
private fun Color.toHtmlString() = "#${Integer.toHexString(rgb)}"
