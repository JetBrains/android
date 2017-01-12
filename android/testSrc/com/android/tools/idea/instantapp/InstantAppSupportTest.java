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
package com.android.tools.idea.instantapp;

import com.android.builder.model.AndroidAtom;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.io.File;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_ATOM;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;

public class InstantAppSupportTest extends AndroidGradleTestCase {

  private static final String APP_NAME = "instant-app";
  private static final String BASE_ATOM_NAME = "baseatom";

  public void testLoadInstantAppProject() throws Exception {
    loadProject(INSTANT_APP);

    Module appModule = myModules.getModule(APP_NAME);
    AndroidModuleModel appModel = AndroidModuleModel.get(appModule);
    assertNotNull(appModel);
    assertEquals(PROJECT_TYPE_INSTANTAPP, appModel.getProjectType());
    AndroidAtom baseAtom = appModel.getMainArtifact().getDependencies().getBaseAtom();
    assertNotNull(baseAtom);
    assertEquals(BASE_ATOM_NAME, baseAtom.getAtomName());

    Module baseAtomModule = myModules.getModule(BASE_ATOM_NAME);
    AndroidModuleModel baseAtomModel = AndroidModuleModel.get(baseAtomModule);
    assertNotNull(baseAtomModel);
    assertEquals(PROJECT_TYPE_ATOM, baseAtomModel.getProjectType());

    generateSources();

    Project project = getProject();
    assertFileHasNoErrors(project, new File("instant-app/src/main/AndroidManifest.xml"));
    assertFileHasNoErrors(project, new File("baseatom/src/main/AndroidManifest.xml"));

    assertFileHasNoErrors(project, new File("baseatom/src/main/java/com/example/instantapp/MainActivity.java"));
    assertFileHasNoErrors(project, new File("baseatom/src/androidTest/java/com/example/instantapp/ExampleInstrumentedTest.java"));
    assertFileHasNoErrors(project, new File("baseatom/src/test/java/com/example/instantapp/ExampleUnitTest.java"));
  }

}
