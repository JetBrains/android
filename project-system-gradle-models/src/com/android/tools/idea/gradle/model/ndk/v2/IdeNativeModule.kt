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

package com.android.tools.idea.gradle.model.ndk.v2

import java.io.File

enum class NativeBuildSystem {
    NDK_BUILD, CMAKE
}

/**
 * Response returned by Gradle to Android Studio containing information about an Android module that
 * contains native code.
 */
interface IdeNativeModule {

    /** The name of the module. For example "HelloWorld.app". */
    val name: String

    /** Variants in this module. */
    val variants: List<IdeNativeVariant>

    /** The native build system used by this module. */
    val nativeBuildSystem: NativeBuildSystem

    /**
     * The version of NDK used to build the native part of this module. For example "21.1.6352462".
     */
    val ndkVersion: String

    /**
     * The default NDK version used by the Android Gradle Plugin if user has not overridden it. For
     * example "21.1.6352462".
     */
    val defaultNdkVersion: String

    /**
     * The absolute path of the root external build file. For example,
     * "<project root>/app/src/main/cpp/CMakeLists.txt".
     */
    val externalNativeBuildFile: File
}

