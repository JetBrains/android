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
package com.android.tools.idea.tests.gui.assetstudio;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.NewVectorAssetStepFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class NewVectorAssetTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private AssetStudioWizardFixture myDialog;
  private NewVectorAssetStepFixture myStep;

  @Before
  public void openAssetStudioWizard() throws Exception {
    IdeFrameFixture frame = guiTest.importSimpleApplication();
    frame.getProjectView().selectAndroidPane().clickPath("app");

    myDialog = frame.openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset");
    myStep = myDialog.getVectorAssetStep();

    assertThat(myDialog.findWizardButton("Next").isEnabled()).isTrue();
    assertThat(myStep.getError()).isEqualTo("");
  }

  @Test
  public void testNameValidation() {
    myStep.setName("");

    assertThat(myDialog.findWizardButton("Next").isEnabled()).isFalse();
    assertThat(myStep.getError()).isEqualTo("Enter a new name");

    myStep.setName("my name");

    assertThat(myDialog.findWizardButton("Next").isEnabled()).isFalse();
    assertThat(myStep.getError()).isEqualTo("' ' is not a valid resource name character");
    myDialog.clickCancel();
  }

  @Test
  public void testSaveDefaultMaterialIcon() {
    myDialog.clickNext();
    myDialog.clickFinish();
    guiTest.ideFrame().getEditor().open("app/src/main/res/drawable/ic_android_black_24dp.xml");
  }

  // TODO: There is no tests to cover non default icons, different sizes or a Local SVG File
}
