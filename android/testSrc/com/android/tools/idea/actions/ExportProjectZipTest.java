/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class ExportProjectZipTest extends AndroidGradleTestCase {

  public void testExportProject() throws Exception {
    loadProject(TestProjectPaths.DEPENDENT_MODULES);
    invokeGradleTasks(getProject(), "assembleDebug");

    File zip = new File(myFixture.getTempDirPath(), "project.zip");
    ExportProjectZip.save(zip, getProject(), null);
    List<String> zipContent = new ArrayList<>();
    try (ZipFile zipFile = new ZipFile(zip)) {
      zipFile.stream().forEach(e -> zipContent.add(e.getName()));
    }
    Truth.assertThat(zipContent).containsExactly("testExportProject/gradlew.bat",
                                                 "testExportProject/gradlew",
                                                 "testExportProject/lib/",
                                                 "testExportProject/lib/src/",
                                                 "testExportProject/lib/src/main/",
                                                 "testExportProject/lib/src/main/AndroidManifest.xml",
                                                 "testExportProject/lib/src/main/assets/",
                                                 "testExportProject/lib/src/main/assets/lib.asset.txt",
                                                 "testExportProject/lib/src/main/assets/raw.asset.txt",
                                                 "testExportProject/lib/src/main/res/",
                                                 "testExportProject/lib/src/main/res/assets/",
                                                 "testExportProject/lib/src/main/res/assets/lib_asset.txt",
                                                 "testExportProject/lib/src/main/res/drawable/",
                                                 "testExportProject/lib/src/main/res/drawable/lib.png",
                                                 "testExportProject/lib/src/main/res/values/",
                                                 "testExportProject/lib/src/main/res/values/colors.xml",
                                                 "testExportProject/lib/build.gradle",
                                                 "testExportProject/settings.gradle",
                                                 "testExportProject/app/",
                                                 "testExportProject/app/src/",
                                                 "testExportProject/app/src/paid/",
                                                 "testExportProject/app/src/paid/AndroidManifest.xml",
                                                 "testExportProject/app/src/paid/res/",
                                                 "testExportProject/app/src/paid/res/values/",
                                                 "testExportProject/app/src/paid/res/values/colors.xml",
                                                 "testExportProject/app/src/main/",
                                                 "testExportProject/app/src/main/AndroidManifest.xml",
                                                 "testExportProject/app/src/main/assets/",
                                                 "testExportProject/app/src/main/assets/app.asset.txt",
                                                 "testExportProject/app/src/main/assets/raw.asset.txt",
                                                 "testExportProject/app/src/main/res/",
                                                 "testExportProject/app/src/main/res/assets/",
                                                 "testExportProject/app/src/main/res/assets/app_asset.txt",
                                                 "testExportProject/app/src/main/res/drawable/",
                                                 "testExportProject/app/src/main/res/drawable/app.png",
                                                 "testExportProject/app/src/main/res/values/",
                                                 "testExportProject/app/src/main/res/values/colors.xml",
                                                 "testExportProject/app/build.gradle",
                                                 "testExportProject/build.gradle",
                                                 "testExportProject/gradle/",
                                                 "testExportProject/gradle/wrapper/",
                                                 "testExportProject/gradle/wrapper/gradle-wrapper.jar",
                                                 "testExportProject/gradle/wrapper/gradle-wrapper.properties");
  }
}
