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
package com.android.tools.compose.code.completion

import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.MaterialVdIconsProvider
import com.android.tools.idea.material.icons.MaterialVdIcons
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.ImageIcon

typealias IconLoader = ((MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit, Disposable) -> Unit

/**
 * Light service providing cached Material icons for usage in the autocomplete dialog.
 *
 * Icons are loaded using [MaterialVdIconsProvider], sized to 16x16 to fit autocomplete UI, and are stored using soft references so that
 * they will be discarded if there is memory pressure.
 */
@Service
internal class ComposeMaterialIconService
  @VisibleForTesting internal constructor(private val loadIcons: IconLoader) : Disposable {
  constructor() : this(ComposeMaterialIconService::callLoadMaterialVdIcons)

  private var iconsWrapper: SoftReference<MaterialVdIconsWrapper> = SoftReference(null)
  private val iconLoadingInProgress: AtomicBoolean = AtomicBoolean()

  override fun dispose() {
    iconLoadingInProgress.set(false)
    iconsWrapper = SoftReference(null)
  }

  /**
   * Gets an icon given its expected filename.
   *
   * The filename should follow the idiomatic file format for Material icons of "<theme>_<iconname>_24.xml". For example, the Attachment
   * icon in the Sharp theme would have the name "sharp_attachment_24.xml".
   */
  fun getIcon(iconFileName: String): Icon? {
    // Return an icon if we currently have a reference to the icon wrapper.
    iconsWrapper.get()?.let { return@getIcon it.getIcon(iconFileName) }

    // Since there's no reference, go ahead and start loading icons but don't wait for it to complete.
    ensureIconsLoaded()
    return null
  }

  /**
   * Icons are loaded using [MaterialVdIconsProvider], which will download icons if they aren't already available on disk. This method kicks
   * off the loading process, and can be used in situations when we know we might be requesting icons shortly.
   */
  fun ensureIconsLoaded() {
    // If we have the icon wrapper, there's no need to start a new loading process.
    if (iconsWrapper.get() != null) return

    if (!iconLoadingInProgress.compareAndSet(/* expectedValue = */ false, /* newValue = */ true)) {
      // Icons are already being loaded, so do nothing.
      return
    }

    // Check once more for whether we have icons, on the small chance that loading completed between the above two checks.
    if (iconsWrapper.get() != null) {
      iconLoadingInProgress.set(false)
      return
    }

    // Icons are really not loaded yet, so start the process.
    loadIcons(this::materialVdIconsLoadedCallback, this)
  }

  private fun materialVdIconsLoadedCallback(icons: MaterialVdIcons, status: MaterialVdIconsProvider.Status) {
    // Store a wrapper using returned icons. When this callback is called multiple times, each call supersedes the last and contains a
    // superset of its icons.
    iconsWrapper = SoftReference(MaterialVdIconsWrapper(icons))

    // Only mark loading completed on FINISH; otherwise we expect more callbacks.
    if (status == MaterialVdIconsProvider.Status.FINISHED) iconLoadingInProgress.set(false)
  }

  /**
   * Wrapper around [MaterialVdIcons] providing lookup access by icon name and resizing the icons for auto-complete.
   */
  private class MaterialVdIconsWrapper(materialVdIcons: MaterialVdIcons) {

    private val iconMap: Map<String, Supplier<Icon?>> =
      materialVdIcons.styles
        .flatMap { style -> materialVdIcons.getAllIcons(style).asList() }
        .associate { icon -> icon.name to Suppliers.memoize { icon.to16by16() } }

    fun getIcon(iconFileName: String): Icon? = iconMap[iconFileName]?.get()

    private fun VdIcon.to16by16(): Icon? = renderIcon(16, 16)?.let(::ImageIcon)
  }

  companion object {
    fun getInstance(application: Application): ComposeMaterialIconService = application.service()

    /** Call to [MaterialVdIconsProvider.loadMaterialVdIcons] wrapped in a function to allow overriding for tests. */
    private fun callLoadMaterialVdIcons(refreshUiCallback: (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit,
                                        parentDisposable: Disposable) {
      MaterialVdIconsProvider.loadMaterialVdIcons(refreshUiCallback, parentDisposable)
    }
  }
}
