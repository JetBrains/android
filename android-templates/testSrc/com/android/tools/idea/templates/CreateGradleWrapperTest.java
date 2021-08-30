/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.templates;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;

import com.android.tools.idea.testing.AndroidGradleTests;
import com.intellij.testFramework.LightPlatformTestCase;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CreateGradleWrapperTest extends LightPlatformTestCase {

  @Test
  public void createGradleWrapper() throws Exception {
    File projectFolderPath = new File(getTestDirectoryName());
    assertThat(projectFolderPath.mkdir()).isTrue();

    AndroidGradleTests.createGradleWrapper(projectFolderPath, GRADLE_LATEST_VERSION);

    assertAbout(file()).that(new File(projectFolderPath, "gradlew")).isFile();
    assertAbout(file()).that(new File(projectFolderPath, "gradlew.bat")).isFile();
    assertAbout(file()).that(new File(projectFolderPath, "gradle")).isDirectory();
    assertAbout(file()).that(new File(projectFolderPath, join("gradle", "wrapper", "gradle-wrapper.jar"))).isFile();
    assertAbout(file()).that(new File(projectFolderPath, join("gradle", "wrapper", "gradle-wrapper.properties"))).isFile();
  }
}
