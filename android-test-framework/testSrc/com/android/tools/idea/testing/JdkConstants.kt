/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.EmbeddedDistributionPaths
import com.intellij.openapi.projectRoots.JavaSdk
import kotlin.io.path.absolutePathString
import org.jetbrains.jps.model.java.JdkVersionDetector

object JdkConstants {
  val JDK_17 by lazy { JavaSdk.getInstance().suggestSdkName(null, JDK_17_PATH) }
  val JDK_11 by lazy { JavaSdk.getInstance().suggestSdkName(null, JDK_11_PATH) }
  val JDK_1_8 by lazy { JavaSdk.getInstance().suggestSdkName(null, JDK_1_8_PATH) }
  val JDK_EMBEDDED by lazy { JavaSdk.getInstance().suggestSdkName(null, JDK_EMBEDDED_PATH) }

  const val JDK_INVALID_PATH = "jdk-invalid-path"
  val JDK_21_PATH by lazy { EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jbr-next").toString() }
  val JDK_17_PATH by lazy { EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk17").toString() }
  val JDK_11_PATH by lazy { EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk11").toString() }
  val JDK_1_8_PATH by lazy { EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk8").toString() }
  val JDK_EMBEDDED_PATH by lazy { IdeSdks.getInstance().embeddedJdkPath.absolutePathString() }

  val JDK_EMBEDDED_VERSION by lazy { JdkVersionDetector.getInstance().detectJdkVersionInfo(JDK_EMBEDDED_PATH)!!.version.feature.toString() }
}
