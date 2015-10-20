/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.intellij.pom.java.LanguageLevel;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link GradleBuildModel}.
 */
public class GradleBuildModelTest extends GradleFileModelTestCase {
  public void testAddAndResetBlockElements() throws Exception {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());

    buildModel.addAndroidModel();
    buildModel.addExtModel();
    buildModel.addJavaModel();

    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());

    buildModel.resetState();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());
  }

  public void testRemoveAndResetBlockElements() throws Exception {
    String text = "android { \n" +
                  "}\n" +
                  "ext {\n" +
                  "}\n" +
                  "sourceCompatibility = 1.6";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());

    buildModel.removeAndroidModel();
    buildModel.removeExtModel();
    buildModel.removeJavaModel();

    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());

    buildModel.resetState();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());
  }

  public void testAddAndApplyBlockElements() throws Exception {
    String text = "";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    buildModel.addAndroidModel();
    buildModel.addExtModel();
    buildModel.addJavaModel();

    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    JavaModel java = buildModel.java();
    assertNotNull(java); // Add source compatibility as an empty java element can't be added to the gradle file.
    java.setSourceCompatibility(LanguageLevel.JDK_1_5);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());

    buildModel.reparse();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());
  }

  public void testRemoveAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "}\n" +
                  "ext {\n" +
                  "}\n" +
                  "sourceCompatibility = 1.6";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
    assertNotNull(buildModel.java());

    buildModel.removeAndroidModel();
    buildModel.removeExtModel();
    buildModel.removeJavaModel();

    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());

    buildModel.reparse();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
    assertNull(buildModel.java());
  }
}
