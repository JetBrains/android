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
package com.android.tools.asdriver.tests.base;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.TestFileSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IntelliJInstallation extends IdeInstallation<IntelliJ> {

  protected IntelliJInstallation(String platform, TestFileSystem testFileSystem, Path workDir, Path studioDir) throws IOException {
    super(platform, testFileSystem, workDir, studioDir);
    acceptUserAgreement();
  }

  private void acceptUserAgreement() throws IOException {
    Path path = fileSystem.getHome().resolve(".java/.userPrefs/jetbrains/_!(!!cg\"p!(}!}@\"j!(k!|w\"w!'8!b!\"p!':!e@==/prefs.xml");
    Files.createDirectories(path.getParent());
    String contents = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                      "<!DOCTYPE map SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n" +
                      "<map MAP_XML_VERSION=\"1.0\">\n" +
                      "  <entry key=\"euacommunity_accepted_version\" value=\"999.999\"/>\n" +
                      "</map>";
    Files.writeString(path, contents, StandardCharsets.UTF_8);
  }

  @Override
  protected void createVmOptions(StringBuilder vmOptions) throws IOException {
    super.createVmOptions(vmOptions);
    vmOptions.append("-Djb.consents.confirmation.enabled=false%n");
  }

  static IntelliJInstallation fromZip(String platform, TestFileSystem testFileSystem) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "intellij");
    System.out.println("workDir: " + workDir);
    Path zipPath = TestUtils.getWorkspaceRoot(platform).resolve(platform + "-dist.zip");
    Path studioDir = workDir.resolve(platform);
    Files.createDirectories(studioDir);
    unzip(zipPath, studioDir);

    return new IntelliJInstallation(platform, testFileSystem, workDir, studioDir);
  }

  protected IntelliJ createAndAttach() throws IOException, InterruptedException {
    // TODO: We don't attach our client to IntelliJ yet.
    return new IntelliJ(this);
  }

  @Override
  protected String vmOptionEnvName() {
    return "IDEA_VM_OPTIONS";
  }

  protected String getExecutable() {
    Path studioDir = getStudioDir();
    return studioDir.resolve("bin/idea.sh").toString();
  }
}
