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

import java.nio.file.Path

/**
 * Represents the JSON format used by
 * [WatchFaceStudio](https://developer.samsung.com/watch-face-studio/overview.html) in the
 * `honeyface.json` file that's embedded within the `.wfs` files.
 */
internal data class HoneyFace(
  val settings: Settings,
  val background: List<Background>,
  val scene: List<SceneItem>,
  val stringResource: Map<String, Map<String, String>>,
  val themeColors: ThemeColorsContainer,
  val buildData: BuildData,
  val styleGroup: StyleGroupContainer,
)

internal data class Settings(
  val width: Int,
  val height: Int,
  val clockType: String,
  val shape: String?,
  val isCropped: Boolean,
)

internal data class StyleGroupContainer(val styleGroups: List<StyleGroup>)

internal data class StyleGroup(
  val id: String,
  val name: String,
  val collapse: Boolean,
  val elements: List<String>,
  val stringResId: String,
  val styleOptions: List<Map<String, String>>,
)

internal data class BuildData(
  val watchType: String,
  val packageName: String,
  val appLabel: Map<String, String>,
)

internal data class Dimensions(val width: Float, val height: Float)

internal data class Position(val x: Float, val y: Float)

internal data class Scale(val x: Float, val y: Float)

internal data class SceneItem(
  val id: String,
  val name: String,
  val type: String,
  val visible: Boolean,
  val visibleOnAOD: Boolean,
  val pivotVisibility: Boolean,
  val applyThemeColor: Boolean,
  val applyThemeColorType: Int,
  val conditionalLine: ConditionalLine,
  val categories: Categories,
  val child: List<SceneItem>?,
  val collapse: Boolean,
  val layerItemColor: String,
)

internal data class Background(val categories: BackgroundCategories)

internal data class BackgroundCategories(val color: ColorCategory, val aodColor: ColorCategory)

class ConditionalLine

internal data class ColorCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val properties: ColorProperties,
)

internal data class ColorProperties(val color: Value<RgbaColor>)

internal data class PlacementCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val properties: PlacementProperties,
)

internal data class PlacementProperties(val position: Value<Position>)

internal data class DimensionsCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val properties: DimensionsProperties,
)

internal data class DimensionsProperties(
  val width: Value<Float>,
  val height: Value<Float>,
  val scale: Value<Scale>,
)

internal data class ActionCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val properties: ActionProperties,
)

internal data class ActionProperties(val enabled: Value<Boolean>, val interaction: Value<String>)

internal data class ImageCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val properties: ImageProperties,
)

internal data class ImageProperties(val image: Value<Path>, val adjustmentColor: Value<HslaColor>)

internal data class StyleCategory(
  val enabled: Boolean?,
  val isExpanded: Boolean?,
  val visibility: Boolean?,
  val styleType: String,
  val properties: StyleProperties,
)

internal data class StyleProperties(val images: Value<List<Image>>)

internal data class Image(val path: Path, val name: String, val defaultThumbnail: Thumbnail)

internal data class Thumbnail(val path: Path, val name: String)

internal data class Value<T>(val value: T)

internal data class Categories(
  val placement: PlacementCategory?,
  val dimension: DimensionsCategory?,
  val color: ColorCategory?,
  val image: ImageCategory?,
  val action: ActionCategory?,
  val style: StyleCategory?,
)

internal data class RootScene(val dimensions: Dimensions, val position: Position)

internal data class ThemeColorsContainer(val themeColorList: List<Theme>)

internal data class Theme(val colors: List<RgbaColor>)

internal data class RgbaColor(val r: Int, val g: Int, val b: Int, val a: Int)

internal data class HslaColor(val h: Float, val s: Float, val l: Float, val a: Float)
