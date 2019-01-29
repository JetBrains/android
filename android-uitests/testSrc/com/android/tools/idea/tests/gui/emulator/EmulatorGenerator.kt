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

@file:JvmName("EmulatorGenerator")

package com.android.tools.idea.tests.gui.emulator

import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture
import org.fest.swing.exception.ActionFailedException
import org.fest.swing.exception.ComponentLookupException

/**
 * Returns the name of the created AVD. Use this name to find the AVD when searching
 * for the AVD during a UI test
 */
fun ensureAvdIsCreated(avdManagerDialog: AvdManagerDialogFixture, avdSpec: AvdSpec): String {
  try {
    avdManagerDialog.selectAvd(avdSpec.avdName)
    // Found the AVD! Don't need to do do anything!
  } catch(didNotFindAvd: Exception) {
    when (didNotFindAvd) {
      is ComponentLookupException, is ActionFailedException -> createAvd(avdSpec, avdManagerDialog)
      else -> throw didNotFindAvd
    }
  }
  avdManagerDialog.close()
  return avdSpec.avdName
}

private fun createAvd(avdSpec: AvdSpec, avdManagerDialog: AvdManagerDialogFixture) {
  avdManagerDialog.createNew()
    .selectHardware()
    .selectHardwareProfile(avdSpec.hardwareProfile)
    .wizard()
    .clickNext()
    .chooseSystemImageStep
    .selectTab(avdSpec.systemImageTabGroup)
    .selectSystemImage(avdSpec.systemImageSpec)
    .wizard()
    .clickNext()
    .configureAvdOptionsStep
    .setAvdName(avdSpec.avdName)
    .selectGraphicsSoftware()
    .wizard()
    .clickFinish()
}

/**
 * Returns the name of the created AVD. Use this name to find the AVD when searching
 * for the AVD during a UI test
 */
fun ensureDefaultAvdIsCreated(avdManagerDialog: AvdManagerDialogFixture) =
  ensureAvdIsCreated(avdManagerDialog, AvdSpec.Builder().build())
