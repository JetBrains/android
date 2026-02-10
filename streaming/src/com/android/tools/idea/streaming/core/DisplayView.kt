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
package com.android.tools.idea.streaming.core

import com.android.sdklib.deviceprovisioner.DeviceType
import com.intellij.openapi.Disposable
import java.awt.Rectangle
import javax.swing.JComponent

/** View of a display of a physical or a virtual device. */
interface DisplayView : Disposable {
  /** Serial number of the device shown in the view. */
  val deviceSerialNumber: String

  val deviceType: DeviceType

  val apiLevel: Int

  val displayId: Int

  /** The Swing component of the view. */
  val component: JComponent

  /** Area of the window occupied by the device display image in physical pixels. */
  val displayRectangle: Rectangle?

  /** Orientation of the device display in quadrants counterclockwise. */
  val displayOrientationQuadrants: Int
  /** The difference between [displayOrientationQuadrants] and the orientation according to the internal Android data structures. */
  val displayOrientationCorrectionQuadrants: Int

  /** Scale factor of the host screen. Number of physical pixels in one logical pixel. */
  val screenScalingFactor: Double

  /** The number of the last rendered display frame. */
  val frameNumber: UInt

  /** Controls whether right clicks are sent to the device when the hardware input is disabled. */
  var rightClicksAreSentToDevice: Boolean
}
