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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidStudioInstallation
import com.android.tools.asdriver.tests.Display
import com.android.tools.asdriver.tests.TestFileSystem
import com.android.utils.withResources
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A test that starts Android Studio with a custom JVM using the STUDIO_JDK env variable */
internal class StartUpCustomJvmTest {
  @get:Rule
  var tempFolder = TemporaryFolder()

  @Test
  fun startUpWithCustomJvmTest() {
    val fileSystem = TestFileSystem(tempFolder.root.toPath())
    val install = AndroidStudioInstallation.fromZip(fileSystem)
    val newJvm = install.studioDir.resolve("jbr1")
    install.studioDir.resolve("jbr").toFile().renameTo(newJvm.toFile())
    val env = mapOf("STUDIO_JDK" to newJvm.toString())

    withResources(Display.createDefault(), { install.run(it, env) }) { _, studio ->
      assertThat(studio.getSystemProperty("java.home")).isEqualTo(newJvm.toString())
    }
  }
}
