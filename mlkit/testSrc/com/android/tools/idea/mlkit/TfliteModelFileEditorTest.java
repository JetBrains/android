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

import com.android.testutils.TestUtils;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.project.DefaultModuleSystem;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
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

  public void testCreateHtmlBody_normalModel_normalHtml() {
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    String html = editor.createHtmlBody();

    // At least contains Model section.
    assertThat(html).contains("<td valign=\"top\">Name</td>\n<td valign=\"top\">mobilenet_v1_1.0_224_quant</td>\n");
  }

  public void testCreateHtmlBody_modelWithoutMetadata() {
    modelFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_model.tflite");
    TfliteModelFileEditor editor = new TfliteModelFileEditor(myFixture.getProject(), modelFile);
    String html = editor.createHtmlBody();

    // At least contains Model section.
    assertThat(html).contains("<h2>Model</h2>");
  }
}
