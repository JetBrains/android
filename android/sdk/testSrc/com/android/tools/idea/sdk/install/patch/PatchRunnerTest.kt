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
package com.android.tools.idea.sdk.install.patch

import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.intellij.util.io.write
import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PatchRunnerTest {
  @Test
  fun testRun() {
    val sdk = TestUtils.getSdk()
    val patcherPackage = FakeLocalPackage("patcher;v4", sdk.resolve("patcher/v4"))
    val progress = FakeProgressIndicator()

    val dest = Files.createTempDirectory("PatchRunnerTest_testRun")
    val contentFile = dest.resolve("file")
    contentFile.write("Here's my initial contents\n")

    val newContents = Files.createTempDirectory("PatchRunnerTest_testRun2")
    newContents.resolve("file").write("Here's my new contents\n")

    val patch = Files.createTempDirectory("PatchRunnerTest_testRun3").resolve("patch.jar")

    // Generate that patch that will be applied
    val loader = PatchRunner.getLoader(patcherPackage, progress)!!
    val runnerClass = loader.loadClass("com.intellij.updater.Runner")
    val main = runnerClass.getDeclaredMethod("main", Array<String>::class.java)
    main.invoke(null, arrayOf("create", "1", "2", dest.toString(), newContents.toString(), patch.toString(), "--no_jar"))

    // Now apply it
    val patcher = PatchRunner.DefaultFactory().getPatchRunner(patcherPackage, progress)!!
    patcher.run(dest, patch, progress)
    progress.assertNoErrorsOrWarnings()
    assertThat(contentFile).hasContents("Here's my new contents")
  }
}