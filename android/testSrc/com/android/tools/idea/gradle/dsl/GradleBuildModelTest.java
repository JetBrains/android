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
package com.android.tools.idea.gradle.dsl;

import com.android.tools.idea.gradle.dsl.parser.GradleBuildModelParserTestCase;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link GradleBuildModel}.
 */
public class GradleBuildModelTest extends GradleBuildModelParserTestCase {
  public void testAddAndResetBlockElements() throws Exception {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    buildModel.addAndroidElement();
    buildModel.addExtModel();

    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    buildModel.resetState();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
  }

  public void testRemoveAndResetBlockElements() throws Exception {
    String text = "android { \n" +
                  "}\n" +
                  "ext {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    buildModel.removeProperty("android");
    buildModel.removeProperty("ext");

    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    buildModel.resetState();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
  }

  public void testAddAndApplyBlockElements() throws Exception {
    String text = "";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    buildModel.addAndroidElement();
    buildModel.addExtModel();

    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    buildModel.reparse();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());
  }

  public void testRemoveAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "}\n" +
                  "ext {\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel.android());
    assertNotNull(buildModel.ext());

    buildModel.removeProperty("android");
    buildModel.removeProperty("ext");

    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    assertNull(buildModel.android());
    assertNull(buildModel.ext());

    buildModel.reparse();
    assertNull(buildModel.android());
    assertNull(buildModel.ext());
  }
}
