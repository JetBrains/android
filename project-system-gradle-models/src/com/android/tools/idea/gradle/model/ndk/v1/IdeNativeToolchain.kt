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
package com.android.tools.idea.gradle.model.ndk.v1

import java.io.File

interface IdeNativeToolchain {

  /**
   * Returns the name of the toolchain.
   *
   * e.g. "x86_64", "arm-linux-androideabi"
   *
   * @return name of the toolchain.
   */
  val name: String

  /**
   * Returns the full path of the C compiler.
   * May be null if project do not contain C sources.
   *
   * @return the C compiler path.
   */
  val cCompilerExecutable: File?

  /**
   * Returns the full path of the C++ compiler.
   * May be null if project do not contain C++ sources.
   *
   * @return the C++ compiler path.
   */
  val cppCompilerExecutable: File?
}
