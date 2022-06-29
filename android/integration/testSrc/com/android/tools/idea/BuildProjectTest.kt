/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.testutils.TestUtils
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSdk
import com.android.tools.asdriver.tests.AndroidStudioInstallation
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.TestFileSystem
import com.android.tools.asdriver.tests.XvfbServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class BuildProjectTest {
  @JvmField @Rule
  var tempFolder = TemporaryFolder()

  @Test
  fun buildProjectTest() {
    val fileSystem = TestFileSystem(tempFolder.root.toPath())
    val install = AndroidStudioInstallation.fromZip(fileSystem)
    install.createFirstRunXml()
    val env = HashMap<String, String>()

    val sdk = AndroidSdk(TestUtils.resolveWorkspacePath("prebuilts/studio/sdk/linux"))
    sdk.install(env)

    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip")
    val projectPath = project.install(fileSystem.root)

    // Mark that project as trusted
    install.trustPath(projectPath)

    // Create a maven repo and set it up in the installation and environment
    val mavenRepo = MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest")
    mavenRepo.install(fileSystem.root, install, env)
    XvfbServer().use { display ->
      install.run(display, env, arrayOf(projectPath.toString())).use { studio ->
        var matcher = install.ideaLog.waitForMatchingLine(".*Gradle sync finished in (.*)", 300, TimeUnit.SECONDS)
        println("Sync took " + matcher.group(1))
        studio.waitForIndex()
        studio.executeAction("MakeGradleProject")
        matcher = install.ideaLog.waitForMatchingLine(".*Gradle build finished in (.*)", 180, TimeUnit.SECONDS)
        println("Build took " + matcher.group(1))
      }
    }
  }
}