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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.projectRoots.JavaSdkVersion
import java.io.File

object JdkUtils {

  @JvmStatic
  fun getEmbeddedJdkPathWithVersion(version: JavaSdkVersion): File {
    if (!IdeInfo.getInstance().isAndroidStudio) return IdeSdks.getInstance().jdkPath?.toFile()!!

    val embeddedJdkPath = when (version) {
      JavaSdkVersion.JDK_17 -> JdkConstants.JDK_17_PATH
      JavaSdkVersion.JDK_11 -> JdkConstants.JDK_11_PATH
      JavaSdkVersion.JDK_1_8 -> JdkConstants.JDK_1_8_PATH
      else -> error("Unsupported JavaSdkVersion: $version")
    }
    return File(embeddedJdkPath)
  }

  @JvmStatic
  fun overrideProjectGradleJdkPathWithVersion(gradleRootProject: File, jdkVersion: JavaSdkVersion) {
    val configProperties = GradleConfigProperties(gradleRootProject)
    val currentJdkVersion = configProperties.javaHome?.toPath()?.let {
      Jdks.getInstance().findVersion(it)
    }
    if (currentJdkVersion != jdkVersion) {
      IdeSdks.getInstance().jdkPath?.toFile()!!.also {
        configProperties.javaHome = it
        configProperties.save()
      }
    }
  }
}