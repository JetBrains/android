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
package com.android.tools.idea.mlkit;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.flags.StudioFlags;
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
import com.intellij.ui.components.JBLabel;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class TfliteModelFileEditorTest extends AndroidTestCase {

  private VirtualFile modelFile;
  private VirtualFile modelFileNotInMlFolder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);
    ((DefaultModuleSystem)ProjectSystemUtil.getModuleSystem(myModule)).setMlModelBindingEnabled(true);
    myFixture.setTestDataPath(TestUtils.getWorkspaceFile("prebuilts/tools/common/mlkit/testData/models").getPath());

    // Add models to project.
    modelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getParent());
    modelFileNotInMlFolder = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "assets/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFileNotInMlFolder.getParent());

    // Set it up as an Android project.
    AndroidFacet androidFacet = AndroidFacet.getInstance(myModule);
    VirtualFile manifestFile = ManifestUtils.getMainManifest(androidFacet).getVirtualFile();
    NamedIdeaSourceProvider ideSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFile.getUrl())
      .withMlModelsDirectoryUrls(ImmutableList.of(manifestFile.getParent().getUrl() + "/ml")).build();
    SourceProviders.replaceForTest(androidFacet, myModule, ideSourceProvider);
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

  public void testShouldDisplaySampleCodeSection_normalModel_returnTrue() {
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    assertThat(editor.myIsSampleCodeSectionVisible).isTrue();
  }

  public void testShouldDisplaySampleCodeSection_ModelInWrongPath_returnFalse() {
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFileNotInMlFolder);
    assertThat(editor.myIsSampleCodeSectionVisible).isFalse();
  }

  public void testViewerOpenEventIsLogged() throws Exception {
    TestUsageTracker usageTracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(usageTracker);

    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    List<MlModelBindingEvent> loggedUsageList =
      usageTracker.getUsages().stream()
        .filter(it -> it.getStudioEvent().getKind() == EventKind.ML_MODEL_BINDING)
        .map(usage -> usage.getStudioEvent().getMlModelBindingEvent())
        .filter(it -> it.getEventType() == MlModelBindingEvent.EventType.MODEL_VIEWER_OPEN)
        .collect(Collectors.toList());
    assertThat(loggedUsageList.size()).isEqualTo(1);

    UsageTracker.cleanAfterTesting();
    usageTracker.close();
  }

  public void testCreateHtmlBody_normalModel() {
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    JPanel contentPanel = ((JPanel) ((JScrollPane) editor.getComponent()).getViewport().getView());
    assertThat(contentPanel.getComponentCount()).isEqualTo(3);
    verifySectionPanelContainsLabel((JPanel) contentPanel.getComponent(0), "Model");
    verifySectionPanelContainsLabel((JPanel) contentPanel.getComponent(1), "Tensors");
    verifySectionPanelContainsLabel((JPanel) contentPanel.getComponent(2), "Sample Code");
  }

  public void testCreateHtmlBody_modelWithoutMetadata() {
    modelFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_model.tflite");
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    JPanel contentPanel = ((JPanel) ((JScrollPane) editor.getComponent()).getViewport().getView());
    assertThat(contentPanel.getComponentCount()).isEqualTo(2);
    verifySectionPanelContainsLabel((JPanel) contentPanel.getComponent(0), "Model");
    verifySectionPanelContainsLabel((JPanel) contentPanel.getComponent(1), "Sample Code");
  }

  private static void verifySectionPanelContainsLabel(@NonNull JPanel sectionPanel, @NonNull String labelText) {
    assertThat(((JBLabel) sectionPanel.getComponent(0)).getText()).isEqualTo(labelText);
  }
}
