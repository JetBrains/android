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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.run.tasks.InstantRunAdvertisement;
import com.intellij.openapi.project.Project;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class InstantRunAdvertisementTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void checkToolBarReflection() throws NoSuchFieldException, IOException {
    guiTest.importSimpleApplication();
    Project project = guiTest.ideFrame().getProject();
    assertThat(new InstantRunAdvertisement(project).getApplyChangesActionComponent()).isNotNull();
  }
}
