/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.asdriver.tests

import com.android.test.testutils.TestUtils

import com.google.devtools.build.runfiles.Runfiles

import java.nio.file.Path
import java.nio.file.Paths

class Workspace {

  companion object {

    /**
     * Returns the runfile path to a given external workspace.
     */
    @JvmStatic fun getRoot(name: String): Path {
      if (TestUtils.runningFromBazel()) {
        val runfiles = Runfiles.preload().withSourceRepository("")
        return Paths.get(runfiles.rlocation(name + "/"))
      }

      return TestUtils.getWorkspaceRoot(name)
    }

  }

}
