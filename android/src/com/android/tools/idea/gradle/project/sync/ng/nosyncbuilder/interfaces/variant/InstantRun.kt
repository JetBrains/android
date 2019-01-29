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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant

/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldInstantRun
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

/** Model for InstantRun related information. */
interface InstantRun {
  enum class Status(val oldValue: Int) {
    /** Instant Run is supported. */
    SUPPORTED(0),
    /** Instant Run is not supported for non-debug build variants. */
    NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT(1),
    /** Instant Run is not supported because the variant is used for testing. */
    NOT_SUPPORTED_VARIANT_USED_FOR_TESTING(2),
    /** Instant Run is not supported when Jack is used. FIXME (is it deprecated?)*/
    NOT_SUPPORTED_FOR_JACK(3),
    /** Instant Run currently does not support projects with external native build system. */
    NOT_SUPPORTED_FOR_EXTERNAL_NATIVE_BUILD(4),
    /** Instant Run is currently disabled for the experimental plugin. */
    NOT_SUPPORTED_FOR_EXPERIMENTAL_PLUGIN(5);

    companion object {
      fun fromValue(value: Int): Status = when (value) {
        OldInstantRun.STATUS_SUPPORTED -> SUPPORTED
        OldInstantRun.STATUS_NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT -> NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT
        OldInstantRun.STATUS_NOT_SUPPORTED_VARIANT_USED_FOR_TESTING -> NOT_SUPPORTED_VARIANT_USED_FOR_TESTING
        OldInstantRun.STATUS_NOT_SUPPORTED_FOR_JACK -> NOT_SUPPORTED_FOR_JACK
        OldInstantRun.STATUS_NOT_SUPPORTED_FOR_EXTERNAL_NATIVE_BUILD -> NOT_SUPPORTED_FOR_EXTERNAL_NATIVE_BUILD
        OldInstantRun.STATUS_NOT_SUPPORTED_FOR_EXPERIMENTAL_PLUGIN -> NOT_SUPPORTED_FOR_EXPERIMENTAL_PLUGIN
        else -> throw IllegalStateException("Nonexistent Instant Run status type")
      }
    }
  }

  /**
   * The last incremental build information, including success or failure, verifier
   * reason for requesting a restart, etc...
   *
   * Contains a file location, possibly not existing.
   */
  val infoFile: File
  /** Whether the owner artifact supports Instant Run. This may depend on the toolchain used. */
  val isSupportedByArtifact: Boolean
  /** The status code indicating whether Instant Run is supported and why. */
  val supportStatus: Status

  fun toProto(converter: PathConverter) = VariantProto.InstantRun.newBuilder()
    .setInfoFile(converter.fileToProto(infoFile))
    .setIsSupportedByArtifact(isSupportedByArtifact)
    .setSupportStatus(VariantProto.InstantRun.Status.valueOf(supportStatus.name))
    .build()!!
}
