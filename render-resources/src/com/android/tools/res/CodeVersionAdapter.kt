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
package com.android.tools.res

/**
 * An adapter for accessing environment-dependent code version. For example, the version of the
 * plugin or library containing this class. See subclasses for descriptions of behavior in specific
 * environments.
 */
abstract class CodeVersionAdapter {
  protected abstract fun doGetCodeVersion(): String

  companion object {
    private var codeVersionAdapter: CodeVersionAdapter? = null

    /**
     * Returns the version of library of plugin containing this class or null if the code is not a
     * part of a plugin or a versioned library.
     */
    @JvmStatic
    fun getCodeVersion(): String? {
      return codeVersionAdapter?.doGetCodeVersion()
    }

    @JvmStatic
    fun setInstance(instance: CodeVersionAdapter) {
      check(codeVersionAdapter == null) { "Re-assignment of CodeVersionAdapter detected" }
      codeVersionAdapter = instance
    }
  }
}