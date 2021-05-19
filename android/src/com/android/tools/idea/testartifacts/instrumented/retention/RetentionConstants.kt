/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.intellij.openapi.actionSystem.DataKey
import java.io.File

/**
 * Android Test Artifacts constants.
 */

@JvmField val EMULATOR_SNAPSHOT_ID_KEY = DataKey.create<String>("EmulatorSnapshotId")

@JvmField val EMULATOR_SNAPSHOT_FILE_KEY = DataKey.create<File>("EmulatorSnapshotFile")

@JvmField val PACKAGE_NAME_KEY = DataKey.create<String>("PackageName")

@JvmField val DEVICE_NAME_KEY = DataKey.create<String>("DeviceName")

@JvmField val RETENTION_ON_FINISH_KEY = DataKey.create<Runnable>("RetentionOnFinish")

@JvmField val RETENTION_AUTO_CONNECT_DEBUGGER_KEY = DataKey.create<Boolean>("RetentionAutoConnectDebugger")

const val LOAD_RETENTION_ACTION_ID = "android.testRetention.load"
