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
import com.android.tools.idea.testing.FileSubject;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.mlmodels.ImportMlModelWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ImportMlModelTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  @Test
  public void testImportMlModel_flavorMain() throws IOException {
    testImportMlModel("main");
  }

  @Test
  public void testImportMlModel_flavorDebug() throws IOException {
    testImportMlModel("debug");
  }

  private void testImportMlModel(String flavor) throws IOException {
    String modelFilePath =
      TestUtils.resolveWorkspacePath("prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite").toString();

    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(ImportMlModelWizardFixture::find, "File", "New", "Other", "TensorFlow Lite Model")
      .getImportModelStep()
      .enterModelPath(modelFilePath)
      .enterFlavor(flavor)
      .wizard()
      .clickFinish();

    File modelFile = new File(guiTest.ideFrame().getProjectPath(), String.format("/app/src/%s/ml/mobilenet_quant_metadata.tflite", flavor));
    assertAbout(FileSubject.file()).that(modelFile).isFile();
  }
}
