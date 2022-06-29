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
package com.android.tools.idea;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidSdk;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.tools.asdriver.tests.TestFileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OpenProjectTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void openProjectTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidStudioInstallation install = AndroidStudioInstallation.fromZip(fileSystem);
    install.createFirstRunXml();
    HashMap<String, String> env = new HashMap<>();

    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));
    sdk.install(env);

    // Create a new android project, and set a fixed distribution
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip");
    Path projectPath = project.install(fileSystem.getRoot());

    // Mark that project as trusted
    install.trustPath(projectPath);

    // Create a maven repo and set it up in the installation and environment
    MavenRepo mavenRepo = new MavenRepo("tools/adt/idea/android/integration/openproject_deps.manifest");
    mavenRepo.install(fileSystem.getRoot(), install, env);

    try (Display display = Display.createDefault();
         AndroidStudio studio = install.run(display, env, new String[]{ projectPath.toString() })) {
      Matcher matcher = install.getIdeaLog().waitForMatchingLine(".*Gradle sync finished in (.*)", 300, TimeUnit.SECONDS);
      System.out.println("Sync took " + matcher.group(1));
    }
  }
}
