/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.AndroidDeclarativeType
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeBuildModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidDeclarativeModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.model.android.AndroidDeclarativeModelImpl
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_APP
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_LIBRARY
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile

class GradleDeclarativeBuildModelImpl(val gradleBuildFile: GradleBuildFile): GradleBuildModelImpl(gradleBuildFile), GradleDeclarativeBuildModel {
  override fun existingAndroidElement(): AndroidDeclarativeType? {
    return try {
      when (android().fullyQualifiedName) {
        ANDROID_APP.name -> AndroidDeclarativeType.APPLICATION
        ANDROID_LIBRARY.name -> AndroidDeclarativeType.LIBRARY
        else -> null
      }
    }
    catch (e: IllegalStateException) {
      null
    }
  }

  override fun createAndroidElement(type: AndroidDeclarativeType): AndroidDeclarativeModel =
    when (type) {
      AndroidDeclarativeType.APPLICATION ->  AndroidDeclarativeModelImpl(gradleBuildFile.ensurePropertyElement(ANDROID_APP))
      AndroidDeclarativeType.LIBRARY -> AndroidDeclarativeModelImpl(gradleBuildFile.ensurePropertyElement(ANDROID_LIBRARY))
    }

  override fun android(): AndroidDeclarativeModel {
    return getModel(AndroidDeclarativeModel::class.java)
  }

  override fun dependencies(): DependenciesModel {
    return android().dependencies()
  }
}