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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File
import kotlin.math.min

/**
 * An Android Artifact.
 *
 * This is the entry point for the output of a [Variant]. This can be more than one
 * output in the case of multi-apk where more than one APKs are generated from the same set
 * of sources.
 *
 */
interface AndroidArtifact : BaseArtifact {
  /** Whether the output file is signed. Always false for the main artifact of a library project. */
  val isSigned: Boolean
  /** The name of the signing config used for the signing. Null if none are set or if this is the main artifact of a library project. */
  val signingConfigName: String?
  /** The application id of this artifact. */
  val applicationId: String
  /** The name of the task used to generate the source code. The actual value might depend on the build system front end. */
  val sourceGenTaskName: String
  /**
   * The ABI filters associated with the artifact, or null if there are no filters.
   *
   * If the list contains values, then the artifact only contains these ABIs and excludes others.
   */
  val abiFilters: Collection<String>
  /** The InstantRun feature related model. */
  val instantRun: InstantRun
  /**
   * The list of additional APKs that need to installed on the device for this artifact to work correctly.
   *
   * For test artifacts, these will be "buddy APKs" from the `androidTestUtil` configuration.
   */
  val additionalRuntimeApks: Collection<File>
  /** The test options if the variant type is testing, null otherwise. */
  val testOptions: TestOptions?

  /**
   * The name of the task used to run instrumented tests or null if the variant is not a test variant.
   *
   * @since 3.1
   */
  val instrumentedTestTaskName: String?
  /**
   * The name of the task used to generate the bundle file (.aab), or null if the task is not supported.
   *
   * @since 3.2
   */
  val bundleTaskName: String?
  /**
   * The name of the task used to generate APKs via the bundle file (.aab), or null if the task is not supported.
   *
   * @since 3.2
   */
  val apkFromBundleTaskName: String?

  fun toProto(converter: PathConverter): VariantProto.AndroidArtifact {
    val minimalProto = VariantProto.AndroidArtifact.newBuilder()
      .setBaseArtifact((this as BaseArtifact).toProto(converter))
      .setApplicationId(applicationId)
      .setSourceGenTaskName(sourceGenTaskName)
      .setInstantRun(instantRun.toProto(converter))
      .addAllAbiFilters(abiFilters)
      .addAllAdditionalRuntimeApks(additionalRuntimeApks.map { converter.fileToProto(it) })
      .setSigned(isSigned)

    signingConfigName?.let { minimalProto.signingConfigName = signingConfigName }
    testOptions?.let { minimalProto.testOptions = testOptions!!.toProto() }
    instrumentedTestTaskName?.let { minimalProto.instrumentedTestTaskName = instrumentedTestTaskName!! }
    bundleTaskName?.let { minimalProto.bundleTaskName = bundleTaskName!! }
    apkFromBundleTaskName?.let { minimalProto.apkFromBundleTaskName = apkFromBundleTaskName!! }

    return minimalProto.build()!!
  }
}
