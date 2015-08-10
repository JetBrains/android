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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.java.JavaProjectElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionElement;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.pom.java.LanguageLevel;
import org.fest.swing.edt.GuiTask;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslJavaProjectParsingTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testSetSourceCompatibility() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    final GradleBuildModel buildModel = projectFrame.openAndParseBuildFileForModule("library2").getTarget();

    final JavaProjectElement javaProject = buildModel.getExtendedDslElement(JavaProjectElement.class);
    assertNotNull(javaProject);

    final JavaVersionElement sourceCompatibility = javaProject.getSourceCompatibility();
    assertNotNull(sourceCompatibility);

    assertEquals(LanguageLevel.JDK_1_5, sourceCompatibility.getVersion());

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            sourceCompatibility.setVersion(LanguageLevel.JDK_1_7);
          }
        });
        buildModel.reparse();
      }
    });

    JavaProjectElement newJavaProject = buildModel.getExtendedDslElement(JavaProjectElement.class);
    assertNotNull(newJavaProject);

    JavaVersionElement newSourceCompatibility = newJavaProject.getSourceCompatibility();
    assertNotNull(newSourceCompatibility);
    assertEquals(LanguageLevel.JDK_1_7, newSourceCompatibility.getVersion());
  }

  /**
   * If sourceCompatibility exists but targetCompatibility does not, check if newly added targetCompatibility has the right
   * default value and position.
   */
  @Test @IdeGuiTest
  public void testAddNonExistedTargetCompatibility() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    final GradleBuildModel buildModel = projectFrame.openAndParseBuildFileForModule("library2").getTarget();

    final JavaProjectElement javaProject = buildModel.getExtendedDslElement(JavaProjectElement.class);
    assertNotNull(javaProject);

    final JavaVersionElement sourceCompatibility = javaProject.getSourceCompatibility();
    assertNotNull(sourceCompatibility);
    assertEquals(LanguageLevel.JDK_1_5, sourceCompatibility.getVersion());

    JavaVersionElement targetCompatibility = javaProject.getTargetCompatibility();
    assertNull(targetCompatibility);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            javaProject.addTargetCompatibility();
          }
        });
      }
    });

    // newly added targetCompatibility should have the same value with sourceCompatibility
    targetCompatibility = javaProject.getTargetCompatibility();
    assertEquals(LanguageLevel.JDK_1_5, targetCompatibility.getVersion());

    // targetCompatibility should be next to sourceCompatibility
    assertEquals(targetCompatibility.getPsiElement().getParent(),
                 sourceCompatibility.getPsiElement().getParent().getNextSibling().getNextSibling());
  }
}
