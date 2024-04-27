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
package com.android.tools.idea.mlkit.importmodel;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.test.testutils.TestUtils;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.mlkit.MlConstants;
import com.intellij.openapi.util.Disposer;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test for {@link ChooseMlModelStep}
 */
public class ChooseMlModelStepTest extends AndroidTestCase {

  @Mock
  private AndroidModulePaths myAndroidModulePaths;
  private File myFile;
  private ChooseMlModelStep myChooseMlModelStep;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);

    MockitoAnnotations.initMocks(this);
    myFile = spy(new File("model.tflite"));
    when(myAndroidModulePaths.getMlModelsDirectories()).thenReturn(Arrays.asList(new File("ml")));
    MlWizardModel model = new MlWizardModel(myFixture.getModule());
    NamedModuleTemplate namedModuleTemplate = new NamedModuleTemplate("main", myAndroidModulePaths);
    myChooseMlModelStep = new ChooseMlModelStep(model, Collections.singletonList(namedModuleTemplate), getProject(), "title");
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.ML_MODEL_BINDING.clearOverride();
      Disposer.dispose(myChooseMlModelStep);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testCheckPath_notFile_returnError() {
    when(myFile.isFile()).thenReturn(false);
    assertThat(myChooseMlModelStep.checkPath(myFile).getSeverity()).isEqualTo(Validator.Severity.ERROR);
  }

  public void testCheckPath_fileTooLarge_returnError() {
    when(myFile.isFile()).thenReturn(true);
    when(myFile.length()).thenReturn(MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES + 1);
    assertThat(myChooseMlModelStep.checkPath(myFile).getSeverity()).isEqualTo(Validator.Severity.ERROR);
  }

  public void testCheckPath_invalid_returnError() {
    when(myFile.isFile()).thenReturn(true);
    when(myFile.length()).thenReturn(MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES - 1);
    assertThat(myChooseMlModelStep.checkPath(myFile).getSeverity()).isEqualTo(Validator.Severity.ERROR);
  }

  public void testCheckPath_validFile_returnOK() {
    File modelFile =
      TestUtils.resolveWorkspacePath("prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite").toFile();
    assertThat(myChooseMlModelStep.checkPath(modelFile).getSeverity()).isEqualTo(Validator.Severity.OK);
  }
}
