/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.mlmodels;

import static com.google.common.truth.Truth.assertAbout;

import com.android.testutils.TestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.FileSubject;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.mlmodels.ImportMlModelWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ImportMlModelTest {
  @Rule
  public GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void setUp() {
    StudioFlags.ML_MODEL_BINDING.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.ML_MODEL_BINDING.clearOverride();
  }

  @Test
  public void testImportMlModel() throws IOException {
    String modelFilePath =
      TestUtils.getWorkspaceFile("prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite").getAbsolutePath();

    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(ImportMlModelWizardFixture::find, "File", "New", "Other", "TensorFlow Lite Model")
      .getImportModelStep()
      .enterModelPath(modelFilePath)
      .wizard()
      .clickFinish();

    File modelFile = new File(guiTest.ideFrame().getProjectPath(), "/app/src/main/ml/mobilenet_quant_metadata.tflite");
    assertAbout(FileSubject.file()).that(modelFile).isFile();
  }
}
