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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link CommandLineArgs}.
 */
public class CommandLineArgsTest extends AndroidGradleTestCase {
  private CommandLineArgs myArgs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject("projects/projectWithAppandLib");
    myArgs = new CommandLineArgs(getProject());
  }

  public void testGet() throws Exception {
    List<String> args = myArgs.get();
    verify(args);
  }

  public void testGetWithExtraCommandLineOptions() throws Exception {
    Project project = getProject();
    String[] options = { "-Doption1=true", "-Doption2=true"};
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, options);

    List<String> args = myArgs.get();
    verify(args);
    assertThat(args).contains("-Doption1=true");
    assertThat(args).contains("-Doption2=true");

    assertNull(project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY));
  }

  public void testGetWithRefreshExternalNativeModelsOption() throws Exception {
    Project project = getProject();
    project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);

    List<String> args = myArgs.get();
    verify(args);
    assertThat(args).contains("-P" + PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL + "=true");
  }

  private static void verify(@NotNull List<String> args) {
    assertThat(args).contains("-Didea.resolveSourceSetDependencies=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY + "=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
    assertThat(args).contains("-P" + PROPERTY_INVOKED_FROM_IDE + "=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");

    int indexOfInitScript = args.indexOf("--init-script");
    assertThat(indexOfInitScript).isNotEqualTo(-1); // index was found

    String initScriptFilePath = args.get(indexOfInitScript + 1);
    assertNotNull(initScriptFilePath);

    File initScriptFile = new File(initScriptFilePath);
    assertTrue(initScriptFile.isFile());
  }
}