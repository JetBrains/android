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
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.android.AndroidProjectTypes;
import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl;
import com.android.ide.common.gradle.model.impl.IdeDependenciesFactory;
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.DefaultModuleSystem;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.WaitFor;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class TfliteModelFileEditorTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);
    ((DefaultModuleSystem)ProjectSystemUtil.getModuleSystem(myModule)).setMlModelBindingEnabled(true);
    myFixture.setTestDataPath(TestUtils.getWorkspaceFile("prebuilts/tools/common/mlkit/testData/models").getPath());

    // Set it up as an Android project.
    AndroidFacet androidFacet = AndroidFacet.getInstance(myModule);
    VirtualFile manifestFile = ManifestUtils.getMainManifest(androidFacet).getVirtualFile();
    NamedIdeaSourceProvider ideSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFile.getUrl())
      .withMlModelsDirectoryUrls(ImmutableList.of(manifestFile.getParent().getUrl() + "/ml")).build();
    SourceProviders.replaceForTest(androidFacet, myModule, ideSourceProvider);

    // Mock test to have gradle version 4.2.0-alpha8
    File rootFile = new File(myFixture.getProject().getBasePath());
    AndroidProjectStub androidProjectStub = spy(new AndroidProjectStub("4.2.0-alpha8"));
    doReturn(AndroidProjectTypes.PROJECT_TYPE_APP).when(androidProjectStub).getProjectType();
    AndroidModuleModel androidModuleModel =
      spy(AndroidModuleModel.create(myFixture.getProject().getName(), rootFile, IdeAndroidProjectImpl
        .createFrom(androidProjectStub, new HashMap<>(), new IdeDependenciesFactory(), null, emptyList()), "debug"));
    doReturn(new AndroidVersion(28, null)).when(androidModuleModel).getMinSdkVersion();
    AndroidModel.set(androidFacet, androidModuleModel);
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
    TestUsageTracker usageTracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(usageTracker);
    VirtualFile modelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getParent());

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
    VirtualFile modelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getParent());
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
    VirtualFile modelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata_v1.2.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getParent());
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
                 "val detectionResult = outputs.detectionResultList\n" +
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
                 "    List<DetectionResult> detectionResult = outputs.getDetectionResultList();\n" +
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
    VirtualFile modelFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getParent());
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
