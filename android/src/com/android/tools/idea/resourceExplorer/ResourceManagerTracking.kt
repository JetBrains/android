/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.resources.ResourceType

/**
 * Utility class to log usage related to the Resource Manager
 *
 * [Metrics Docs](http://go/android-devtools-metrics)
 */
object ResourceManagerTracking {
  /**
   * Called when the Resource Manager tool window is opened
   */
  fun logPanelOpens() {
  }

  /**
   * Called when the Resource Manager tool window is closed
   */
  fun logPanelCloses() {
  }

  /**
   * Called when users click the "Import Drawable" option
   */
  fun logAssetAddedViaButton() {}

  /**
   * Called when users drop files onto the Resource Manager
   */
  fun logAssetAddedViaDnd() {}

  /**
   * Called the view displaying all versions of a resource is displayed
   */
  fun logDetailViewOpened(type: ResourceType?) {}

  /**
   * Called when a resource file is opened via the resource manager.
   */
  fun logAssetOpened(type: ResourceType) {}

  /**
   * Called when the resource manager is switched to grid mode
   */
  fun logSwitchToGridMode() {}

  /**
   * Called when the resource manager is switched to list mode
   */
  fun logSwitchToListMode() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  /**
   * Called when user toggle the library filter (off by default)
   */
  fun logShowLibrariesToggle(state: Boolean) {}

  /**
   * Called when users drop or paste a resource from the Resource Manager onto a blank area
   * in an XML file.
   */
  fun logPasteOnBlank(type: ResourceType) {}

  /**
   * Called when users drop or paste a resource from the Resource Manager onto an XML tag (not an attribute)
   */
  fun logPasteOnXmlTag(type: ResourceType) {}

  /**
   * Called when users drop or paste a resource from thr Resource Manager onto an XML attribute or value.
   */
  fun logPasteOnXmlAttribute(type: ResourceType) {}

  /**
   * Called when users drop or paste a resource on a text area that does not recognize the Resource Url flavor.
   */
  fun logPasteUrlText(type: ResourceType?) {}

  /**
   * (Not in use yet) Called when a resource is drop onto a view (to change the background of a view for instance)
   */
  fun logDragOnView() {}

  /**
   * Called when a user drag a resource on a view group. This is happens if the view itself does not handle resource url drop.
   */
  fun logDragOnViewGroup() {}

  /**
   * Called when density qualifier has been inferred from file path.
   */
  fun logDensityInferred() {}
}