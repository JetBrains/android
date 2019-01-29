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

/** Contains all the configuration information for a [Variant]. */
interface VariantConfig {
  /** The name of the variant */
  val name: String
  /** Map of generated res values where the key is the res name. */
  val resValues: Map<String, ClassField>

  /**
   * Specifies the ProGuard configuration files that the plugin should use.
   *
   * There are two ProGuard rules files that ship with the Android plugin and are used by default:
   *  - ``proguard-android.txt``
   *  - ``proguard-android-optimize.txt``
   *
   * The only difference between them is that in the second one optimisations are enabled.
   * You can use ``getDefaultProguardFile(filename: String)`` to return the full path of the files.
   */
  val proguardFiles: Collection<File>
  /** The collection of proguard rule files for consumers of the library to use. */
  val consumerProguardFiles: Collection<File>
  /**
   * The map of key value pairs for placeholder substitution in the android manifest file.
   *
   * This map will be used by the manifest merger.
   */
  val manifestPlaceholders: Map<String, String>
  val isDebuggable: Boolean
  /** Application id for the given variant. */
  val applicationId: String?
  /**
   * The version code associated with this flavor or null if none have been set.
   * This is only the value set on this product flavor, not necessarily the actual version code used.
   */
  val versionCode: Int?
  /** Returns the version name including all the product flavor and build type suffixes. */
  val versionName: String?
  /** The minimal supported version of SDK. */
  val minSdkVersion: ApiVersion?
  /** The targeted version of SDK. If not set, the default value equals that given to [minSdkVersion] */
  val targetSdkVersion: ApiVersion?
  /**
   * The resource configuration for this variant.
   *
   * This is the list of -c parameters for aapt.
   */
  val resourceConfigurations: Collection<String>

  fun toProto(converter: PathConverter): VariantProto.VariantConfig {
    val minimalProto = VariantProto.VariantConfig.newBuilder()
      .setName(name)
      .putAllResValues(resValues.mapValues { it.value.toProto() })
      .addAllProguardFiles(proguardFiles.map { converter.fileToProto(it) })
      .addAllConsumerProguardFiles(consumerProguardFiles.map { converter.fileToProto(it) })
      .putAllManifestPlaceholders(manifestPlaceholders)
      .setDebuggable(isDebuggable)
      .setVersionName(versionName)
      .addAllResourceConfigurations(resourceConfigurations)

    applicationId?.let { minimalProto.setApplicationId(applicationId!!) }
    versionCode?.let { minimalProto.setVersionCode(versionCode!!) }
    minSdkVersion?.let { minimalProto.setMinSdkVersion(minSdkVersion!!.toProto()) }
    targetSdkVersion?.let { minimalProto.setTargetSdkVersion(targetSdkVersion!!.toProto()) }

    return minimalProto.build()!!
  }
}
