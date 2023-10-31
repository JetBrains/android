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
package com.android.tools.idea.gradle.project

import com.android.ide.common.repository.AgpVersion
import com.intellij.pom.java.LanguageLevel

/**
 * AGP specific version mapping with the compatible Java compiled version
 */
enum class AgpCompatibleJdkVersion(val languageLevel: LanguageLevel) {
    JDK8(LanguageLevel.JDK_1_8),
    JDK11(LanguageLevel.JDK_11),
    JDK17(LanguageLevel.JDK_17);

    companion object {
        fun getCompatibleJdkVersion(agpVersion: AgpVersion): AgpCompatibleJdkVersion = when {
            agpVersion < "7.0.0-alpha01" -> JDK8
            agpVersion < "8.0.0-beta01" -> JDK11
            else -> JDK17
        }
    }
}