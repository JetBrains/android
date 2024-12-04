/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.jetpack;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.truth.Truth;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBList;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class RoomQueryCodeCompletionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private static final String JAVA_FILE = "app/src/main/java/com/example/roomsampleproject/dao/WordDao.java";
  private static final String CLASS_NAME ="com.intellij.codeInsight.lookup.impl.LookupImpl$LookupList";

  /**
   * Verifies Code Completion With a Query
   * <p>
   * Code Completion With a Query
   * <p>
   * TT ID: 84f148ca-c8a4-4d11-8fa1-10931ed07cfa
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import project (Architecture Components Basic). (File -> new -> Import Sample)
   *   2. Go to versions.gradle file and update "versions.android_gradle_plugin"  to latest plugin.
   *   3. Quick fix all the necessary sdk and imports.   *
   *   4. Go to CommentDao or ProductDao.
   *   To reduce the flakiness. All the above 4 steps care completed in the imported project..
   *   5. Click on the @Query  and click on the Crtl + Spacebar on table name or column
   *   Verify:
   *   1. Verify that the query is recognized by studio and code completion in suggested
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void codeCompletionTest() throws Exception{
    IdeFrameFixture ideFrame = guiTest
      .importProjectAndWaitForProjectSyncToFinish("RoomSampleProject",
                                                  Wait.seconds(600));
    Truth.assertThat(ideFrame.invokeProjectMake().isBuildSuccessful()).isTrue();

    KeyPressInfo keyPressInfo;
    if(SystemInfo.isMac){
      // command + space in mac
      keyPressInfo = KeyPressInfo.keyCode(KeyEvent.VK_SPACE)
        .modifiers(InputEvent.META_MASK);
    }else{
      // ctrl + space in linux and windows
      keyPressInfo = KeyPressInfo.keyCode(KeyEvent.VK_SPACE)
        .modifiers(InputEvent.CTRL_MASK);
    }

    ideFrame.getEditor()
      .open(JAVA_FILE)
      .waitUntilErrorAnalysisFinishes()
      .moveBetween("", "word_" )
      .moveBetween("", "word_" ) // To reduce flakiness
      .pressAndReleaseKey(keyPressInfo);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    JListFixture jListFixture = getLookupList();
    Truth.assertThat(Arrays.stream(jListFixture.contents()).anyMatch(str -> str.contains("word_table"))).isTrue();
  }

  private JListFixture getLookupList(){

    JBList jbList =
      GuiTests.waitUntilShowingAndEnabled(guiTest.robot(),
                                          guiTest.ideFrame().target(), new GenericTypeMatcher<JBList>(JBList.class) {
          @Override
          protected boolean isMatching(@NotNull JBList list) {
            return list.getClass().getName().equals(CLASS_NAME);
          }
        });
    return new JListFixture(guiTest.robot(), jbList);
  }

}
