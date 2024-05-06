/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.retention

import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_BAD_SNAPSHOT_PB
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_AVD
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_FEATURES
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_HOST_HYPERVISOR
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_HOST_GPU
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_RENDERER
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_CORRUPTED_DATA
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_EMULATION_ENGINE_FAILED
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_INCOMPATIBLE_VERSION
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_INTERNAL_ERROR
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_NO_RAM_FILE
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_NO_SNAPSHOT_IN_IMAGE
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_NO_SNAPSHOT_PB
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_NO_TEXTURES_FILE
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_RAM_FAILED
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_SNAPSHOTS_NOT_SUPPORTED
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_SYSTEM_IMAGE_CHANGED
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_TEXTURES_FAILED
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED

fun findFailureReasonFromEmulatorOutput(emulatorOutput: String): EmulatorSnapshotFailureReason {
  val knownErrors = mapOf(
    "unknown failure" to EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED,
    "bad snapshot metadata" to EMULATOR_SNAPSHOT_FAILURE_REASON_BAD_SNAPSHOT_PB,
    "bad snapshot data" to EMULATOR_SNAPSHOT_FAILURE_REASON_CORRUPTED_DATA,
    "missing snapshot files" to EMULATOR_SNAPSHOT_FAILURE_REASON_NO_SNAPSHOT_PB,
    "incompatible snapshot version" to EMULATOR_SNAPSHOT_FAILURE_REASON_INCOMPATIBLE_VERSION,
    "missing saved RAM data" to EMULATOR_SNAPSHOT_FAILURE_REASON_NO_RAM_FILE,
    "missing saved textures data" to EMULATOR_SNAPSHOT_FAILURE_REASON_NO_TEXTURES_FILE,
    "snapshot doesn't exist" to EMULATOR_SNAPSHOT_FAILURE_REASON_NO_SNAPSHOT_IN_IMAGE,
    "current configuration doesn't support snapshots" to EMULATOR_SNAPSHOT_FAILURE_REASON_SNAPSHOTS_NOT_SUPPORTED,
    "host hypervisor has changed" to EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_HOST_HYPERVISOR,
    "host GPU has changed" to EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_HOST_GPU,
    "different renderer configured" to EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_RENDERER,
    "different emulator features" to EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_FEATURES,
    "different AVD configuration" to EMULATOR_SNAPSHOT_FAILURE_REASON_CONFIG_MISMATCH_AVD,
    "system image changed" to EMULATOR_SNAPSHOT_FAILURE_REASON_SYSTEM_IMAGE_CHANGED,
    "internal error" to EMULATOR_SNAPSHOT_FAILURE_REASON_INTERNAL_ERROR,
    "emulation engine failed" to EMULATOR_SNAPSHOT_FAILURE_REASON_EMULATION_ENGINE_FAILED,
    "RAM loading failed" to EMULATOR_SNAPSHOT_FAILURE_REASON_RAM_FAILED,
    "RAM saving failed" to EMULATOR_SNAPSHOT_FAILURE_REASON_RAM_FAILED,
    "textures loading failed" to EMULATOR_SNAPSHOT_FAILURE_REASON_TEXTURES_FAILED,
    "textures saving failed" to EMULATOR_SNAPSHOT_FAILURE_REASON_TEXTURES_FAILED
  )
  return knownErrors.asSequence()
           .filter { (knownErrorMessage, failureReason) ->
             emulatorOutput.contains(knownErrorMessage)
           }
           .map { (knownErrorMessage, failureReason) ->
             failureReason
           }
           .firstOrNull() ?: EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED
}
