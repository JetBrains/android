/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.material.icons

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.common.BundledIconsUrlProvider
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getIconFileNameWithoutExtension
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val LOG = Logger.getInstance(MaterialVdIconsLoader::class.java)

/** Read only model of [VdIcon]s. */
interface MaterialVdIcons {
  val styles: Collection<String>

  fun getCategories(style: String): Collection<String>

  fun getIcons(style: String, category: String): Collection<VdIcon>

  fun getAllIcons(style: String): Collection<VdIcon>

  companion object {
    /** The default empty instance. Returns empty for every method. */
    @JvmField
    val EMPTY =
      object : MaterialVdIcons {
        override val styles: Collection<String> = emptyList()

        override fun getCategories(style: String): Collection<String> = emptyList()

        override fun getIcons(style: String, category: String): Collection<VdIcon> = emptyList()

        override fun getAllIcons(style: String): Collection<VdIcon> = emptyList()
      }
  }
}

/** Write implementation of [MaterialVdIcons]. */
class MaterialVdIconsImpl : MaterialVdIcons {
  /**
   * Class representing the full name of an icon. [category] is optional.
   */
  data class IconCoordinates(val style: String, val category: String?, val name: String)

  private val modelLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

  /** Styles seen by this model. */
  @GuardedBy("modelLock")
  private val _styles: MutableSet<String> = mutableSetOf<String>()

  /** Icons loaded by this model so far. */
  @GuardedBy("modelLock")
  private val loadedIcons =
    mutableMapOf<IconCoordinates, VdIcon>()

  override val styles: Set<String>
    get() = modelLock.read { _styles.toSet() }

  override fun getCategories(style: String): List<String> =
    modelLock.read {
      loadedIcons.keys.filter { it.style == style }.mapNotNull { it.category }.distinct().sorted()
    }

  override fun getIcons(style: String, category: String): List<VdIcon> =
    modelLock.read {
      if (!_styles.contains(style)) return emptyList()

      loadedIcons
        .filter { it.key.style == style && it.key.category == category }
        .map { it.value }
        .distinct()
    }

  override fun getAllIcons(style: String): List<VdIcon> =
    modelLock.read {
      if (!_styles.contains(style)) return emptyList()
      loadedIcons.filter { it.key.style == style }.map { it.value }.distinct()
    }

  fun addStyle(style: String) =
    modelLock.read {
      if (_styles.contains(style)) return@read
      modelLock.write { _styles.add(style) }
    }

  fun addIcons(icons: List<Pair<IconCoordinates, VdIcon>>) {
    modelLock.write {
      icons.forEach { (coordinates, icon) ->
        _styles.add(coordinates.style)
        loadedIcons[coordinates] = icon
      }
    }
  }
}

/** Loads Material [VdIcon]s from the [urlProvider] based on the given [MaterialIconsMetadata]. */
class MaterialVdIconsLoader(
  private val metadata: MaterialIconsMetadata,
  private val urlProvider: MaterialIconsUrlProvider = BundledIconsUrlProvider(),
) {
  /**
   * Model containing the current icons information managed by this loader.
   */
  private val model = MaterialVdIconsImpl()

  /**
   * Loads the icons bundled with AndroidStudio that correspond to the given [style], returns an
   * updated [MaterialVdIcons].
   *
   * Every call to this method will update the backing model of all the icons loaded by this
   * instance, so the returned [MaterialVdIcons] will also include icons loaded in previous calls.
   */
  @Slow
  internal fun loadMaterialVdIcons(style: String): MaterialVdIcons {
    require(metadata.families.contains(style)) { "Style: $style not part of the metadata." }

    if (urlProvider.getStyleUrl(style) == null) {
      // Not used, but it's good to know if the provider fails here
      LOG.warn("No URL for style: $style. From provider: ${urlProvider::class.java.name}.")
    }

    // We record the style as seen even if we can not load any icons from it
    model.addStyle(style)

    val loadedIcons =
      metadata.icons.flatMap { iconMetadata ->
        if (iconMetadata.unsupportedFamilies.contains(style)) {
          // Check that the icon is expected for the asked style
          return@flatMap emptyList()
        }
        // The name should be consistent with bundled AND downloaded icons, to guarantee that we can
        // find them
        val expectedFileName =
          getIconFileNameWithoutExtension(iconName = iconMetadata.name, styleName = style) +
          SdkConstants.DOT_XML
        val vdIcon =
          loadVdIcon(styleName = style, iconName = iconMetadata.name, fileName = expectedFileName)

        if (vdIcon != null) {
          if (iconMetadata.categories.isNotEmpty()) {
            iconMetadata.categories.map {
              MaterialVdIconsImpl.IconCoordinates(
                style = style,
                category = it,
                name = iconMetadata.name,
              ) to vdIcon
            }
          }
          else {
            listOf(
              MaterialVdIconsImpl.IconCoordinates(
                style = style,
                category = null,
                name = iconMetadata.name,
              ) to vdIcon)
          }
        }
        else emptyList()
      }
    model.addIcons(loadedIcons)

    return model
  }

  private fun loadVdIcon(styleName: String, iconName: String, fileName: String): VdIcon? {
    val iconUrl = urlProvider.getIconUrl(styleName, iconName, fileName)
    if (iconUrl == null) {
      LOG.warn(
        "Could not obtain Icon URL: Name=$iconName FileName=$fileName. From provider: ${urlProvider::class.java.name}."
      )
      return null
    }
    return try {
      VdIcon(iconUrl).apply { setShowName(true) }
    } catch (e: IOException) {
      LOG.warn("Failed to load material icon $iconUrl", e)
      return null
    }
  }
}
