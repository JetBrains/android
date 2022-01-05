/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device

import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.Dimension

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param disposableParent the disposable parent determining the lifespan of the view
 * @param deviceSerialNumber the serial number of the device
 * @param deviceAbi the application binary interface of the device
 * @param project the project associated with the view
 */
class DeviceView(
  disposableParent: Disposable,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  private val project: Project,
) : AbstractDisplayView(PRIMARY_DISPLAY_ID), Disposable {

  /** Size of the device display in device pixels. */
  private val deviceDisplaySize = Dimension()

  var displayRotationQuadrants: Int = 0
    private set

  /**
   * The size of the device including frame in device pixels.
   */
  val displaySizeWithFrame: Dimension
    get() = computeActualSize()

  private val connected = true

  init {
    Disposer.register(disposableParent, this)
  }

  override fun dispose() {
  }

  override fun canZoom(): Boolean = connected

  override fun computeActualSize(): Dimension =
    computeActualSize(displayRotationQuadrants)

  private fun computeActualSize(rotationQuadrants: Int): Dimension =
    deviceDisplaySize.rotatedByQuadrants(rotationQuadrants)
}
