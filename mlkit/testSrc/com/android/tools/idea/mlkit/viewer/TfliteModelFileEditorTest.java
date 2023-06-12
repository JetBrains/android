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
package com.android.tools.idea.mlkit.viewer;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.MlProjectTestUtil;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.WaitFor;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.android.AndroidTestCase;

public class TfliteModelFileEditorTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);

    myFixture.setTestDataPath(TestUtils.resolveWorkspacePath("prebuilts/tools/common/mlkit/testData/models").toString());
  }

  private VirtualFile setupProject(String mlSourceFile, String mlTargetFile) {
    VirtualFile result = myFixture.copyFileToProject(mlSourceFile, mlTargetFile);
    MlProjectTestUtil.setupTestMlProject(myFixture, "4.2.0-alpha08", 28);
    return result;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.ML_MODEL_BINDING.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testViewerOpenEventIsLogged() throws Exception {
    VirtualFile modelFile = setupProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");
    TestUsageTracker usageTracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(usageTracker);

    new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    new WaitFor((int)TimeUnit.SECONDS.toMillis(5)) {
      @Override
      protected boolean condition() {
        List<MlModelBindingEvent> loggedUsageList =
          usageTracker.getUsages().stream()
            .filter(it -> it.getStudioEvent().getKind() == EventKind.ML_MODEL_BINDING)
            .map(usage -> usage.getStudioEvent().getMlModelBindingEvent())
            .filter(it -> it.getEventType() == MlModelBindingEvent.EventType.MODEL_VIEWER_OPEN)
            .collect(Collectors.toList());
        return !loggedUsageList.isEmpty();
      }
    }.assertCompleted("Model viewer open event is not logged within 5000 ms.");

    UsageTracker.cleanAfterTesting();
    usageTracker.close();
  }

  public void testCreateHtmlBody_imageClassificationModel() {
    VirtualFile modelFile = setupProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    JPanel contentPanel = ((JPanel)((JScrollPane)editor.getComponent()).getViewport().getView());
    assertThat(contentPanel.getComponentCount()).isEqualTo(3);
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(0), "Model");
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(1), "Tensors");
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(2), "Sample Code");

    JBTabbedPane tabbedPane = (JBTabbedPane)((JPanel)((JPanel)contentPanel.getComponent(2)).getComponent(1)).getComponent(0);
    assertThat(tabbedPane.getTabCount()).isEqualTo(2);
    assertThat(((JLabel)tabbedPane.getTabComponentAt(0)).getText()).isEqualTo("Kotlin");
    assertThat(((EditorTextField)tabbedPane.getComponentAt(0)).getText())
      .isEqualTo("val model = MyModel.newInstance(context)\n" +
                 "\n" +
                 "// Creates inputs for reference.\n" +
                 "val image = TensorImage.fromBitmap(bitmap)\n" +
                 "\n" +
                 "// Runs model inference and gets result.\n" +
                 "val outputs = model.process(image)\n" +
                 "val probability = outputs.probabilityAsCategoryList\n" +
                 "\n" +
                 "// Releases model resources if no longer used.\n" +
                 "model.close()\n");

    assertThat(((JLabel)tabbedPane.getTabComponentAt(1)).getText()).isEqualTo("Java");
    assertThat(((EditorTextField)tabbedPane.getComponentAt(1)).getText())
      .isEqualTo("try {\n" +
                 "    MyModel model = MyModel.newInstance(context);\n" +
                 "\n" +
                 "    // Creates inputs for reference.\n" +
                 "    TensorImage image = TensorImage.fromBitmap(bitmap);\n" +
                 "\n" +
                 "    // Runs model inference and gets result.\n" +
                 "    MyModel.Outputs outputs = model.process(image);\n" +
                 "    List<Category> probability = outputs.getProbabilityAsCategoryList();\n" +
                 "\n" +
                 "    // Releases model resources if no longer used.\n" +
                 "    model.close();\n" +
                 "} catch (IOException e) {\n" +
                 "    // TODO Handle the exception\n" +
                 "}\n");
  }

  public void testCreateHtmlBody_objectDetectionModel() {
    VirtualFile modelFile = setupProject("ssd_mobilenet_odt_metadata_v1.2.tflite", "ml/my_model.tflite");
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    JPanel contentPanel = ((JPanel)((JScrollPane)editor.getComponent()).getViewport().getView());
    assertThat(contentPanel.getComponentCount()).isEqualTo(3);
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(0), "Model");
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(1), "Tensors");
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(2), "Sample Code");

    JBTabbedPane tabbedPane = (JBTabbedPane)((JPanel)((JPanel)contentPanel.getComponent(2)).getComponent(1)).getComponent(0);
    assertThat(tabbedPane.getTabCount()).isEqualTo(2);
    assertThat(((JLabel)tabbedPane.getTabComponentAt(0)).getText()).isEqualTo("Kotlin");
    assertThat(((EditorTextField)tabbedPane.getComponentAt(0)).getText())
      .isEqualTo("val model = MyModel.newInstance(context)\n" +
                 "\n" +
                 "// Creates inputs for reference.\n" +
                 "val image = TensorImage.fromBitmap(bitmap)\n" +
                 "\n" +
                 "// Runs model inference and gets result.\n" +
                 "val outputs = model.process(image)\n" +
                 "val detectionResult = outputs.detectionResultList.get(0)\n" +
                 "\n" +
                 "// Gets result from DetectionResult.\n" +
                 "val locations = detectionResult.locationsAsRectF;\n" +
                 "val classes = detectionResult.classesAsString;\n" +
                 "val scores = detectionResult.scoresAsFloat;\n" +
                 "\n" +
                 "// Releases model resources if no longer used.\n" +
                 "model.close()\n");

    assertThat(((JLabel)tabbedPane.getTabComponentAt(1)).getText()).isEqualTo("Java");
    assertThat(((EditorTextField)tabbedPane.getComponentAt(1)).getText())
      .isEqualTo("try {\n" +
                 "    MyModel model = MyModel.newInstance(context);\n" +
                 "\n" +
                 "    // Creates inputs for reference.\n" +
                 "    TensorImage image = TensorImage.fromBitmap(bitmap);\n" +
                 "\n" +
                 "    // Runs model inference and gets result.\n" +
                 "    MyModel.Outputs outputs = model.process(image);\n" +
                 "    DetectionResult detectionResult = outputs.getDetectionResultList().get(0);\n" +
                 "\n" +
                 "    // Gets result from DetectionResult.\n" +
                 "    RectF locations = detectionResult.getLocationsAsRectF();\n" +
                 "    String classes = detectionResult.getClassesAsString();\n" +
                 "    float scores = detectionResult.getScoresAsFloat();\n" +
                 "\n" +
                 "    // Releases model resources if no longer used.\n" +
                 "    model.close();\n" +
                 "} catch (IOException e) {\n" +
                 "    // TODO Handle the exception\n" +
                 "}\n");
  }

  public void testCreateHtmlBody_modelWithoutMetadata() {
    VirtualFile modelFile = setupProject("mobilenet_quant_no_metadata.tflite", "ml/my_model.tflite");
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    JPanel contentPanel = ((JPanel)((JScrollPane)editor.getComponent()).getViewport().getView());
    assertThat(contentPanel.getComponentCount()).isEqualTo(2);
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(0), "Model");
    verifySectionPanelContainsLabel((JPanel)contentPanel.getComponent(1), "Sample Code");
  }

  private static void verifySectionPanelContainsLabel(@NonNull JPanel sectionPanel, @NonNull String labelText) {
    assertThat(((JBLabel)sectionPanel.getComponent(0)).getText()).isEqualTo(labelText);
  }
}
