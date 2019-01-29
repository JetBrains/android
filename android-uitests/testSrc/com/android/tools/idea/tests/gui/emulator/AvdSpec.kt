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
package com.android.tools.idea.tests.gui.emulator

import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture

class AvdSpec(
  val hardwareProfile: String,
  val systemImageTabGroup: String,
  val systemImageSpec: ChooseSystemImageStepFixture.SystemImage,
  val avdName: String) {

  class Builder {
    private var hardwareProfile: String = "Nexus 5"
    private var systemImageGroup: SystemImageGroups = SystemImageGroups.X86
    private var systemImageSpec: ChooseSystemImageStepFixture.SystemImage =
      ChooseSystemImageStepFixture.SystemImage("Nougat", "24", "x86", "Android 7.0")

    private var avdName: String? = null

    fun setHardwareProfile(hardwareProfile: String) = apply {
      this.hardwareProfile = hardwareProfile
    }

    fun setSystemImageGroup(imageGroup: SystemImageGroups) = apply {
      this.systemImageGroup = imageGroup
    }

    fun setSystemImageSpec(imageSpec: ChooseSystemImageStepFixture.SystemImage) = apply {
      this.systemImageSpec = imageSpec
    }

    fun setAvdName(avdName: String) = apply {
      this.avdName = avdName
    }


    fun build(): AvdSpec {
      val theAvdName = avdName?: "${systemImageSpec.releaseName}-${systemImageSpec.apiLevel}-${systemImageSpec.abiType}-${systemImageSpec.targetName}"

      return AvdSpec(
        hardwareProfile,
        systemImageGroup.tabName,
        systemImageSpec,
        theAvdName)
    }
  }

  enum class SystemImageGroups(val tabName: String) {
    RECOMMENDED("Recommended"),
    X86("x86 Images"),
    OTHER("Other Images")
  }
}