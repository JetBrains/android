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

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.avdmanager.AvdManagerConnection
import org.junit.rules.ExternalResource

class DeleteAvdsRule: ExternalResource() {
  override fun before() {
    val emulatorConn = AvdManagerConnection.getDefaultAvdManagerConnection()
    for (avdInfo: AvdInfo in emulatorConn.getAvds(true)) {
      emulatorConn.deleteAvd(avdInfo);
    }
  }
}