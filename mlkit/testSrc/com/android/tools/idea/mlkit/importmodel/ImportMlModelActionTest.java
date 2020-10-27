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

import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.MIN_SDK_VERSION;
import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.MIN_AGP_VERSION;
import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.TITLE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ImportMlModelAction}.
 */
public class ImportMlModelActionTest {

  @Mock private AndroidModuleModel myMockAndroidModuleModel;
  @Mock private AndroidModuleInfo myMockAndroidModuleInfo;
  @Mock private AnActionEvent myMockActionEvent;
  private ImportMlModelAction myAction;

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.inMemory();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    AndroidFacet selectedAndroidFacet = AndroidFacet.getInstance(projectRule.getModule());

    when(myMockAndroidModuleModel.getAndroidProject()).thenReturn(mock(IdeAndroidProject.class));
    when(myMockAndroidModuleModel.getModelVersion()).thenReturn(GradleVersion.parse(MIN_AGP_VERSION));
    AndroidModel.set(selectedAndroidFacet, myMockAndroidModuleModel);

    when(myMockAndroidModuleInfo.getMinSdkVersion()).thenReturn(new AndroidVersion(MIN_SDK_VERSION));
    AndroidModuleInfo.setInstanceForTest(selectedAndroidFacet, myMockAndroidModuleInfo);

    FacetManager mockFacetManager = mock(FacetManager.class);
    when(mockFacetManager.getFacetByType(AndroidFacet.ID)).thenReturn(selectedAndroidFacet);

    Module mockModel = mock(Module.class);
    doReturn(mockFacetManager).when(mockModel).getComponent(FacetManager.class);
    when(mockModel.getProject()).thenReturn(projectRule.getProject());

    DataContext dataContext = mock(DataContext.class);
    when(dataContext.getData(LangDataKeys.MODULE.getName())).thenReturn(mockModel);

    when(myMockActionEvent.getDataContext()).thenReturn(dataContext);
    when(myMockActionEvent.getPresentation()).thenReturn(new Presentation());
    when(myMockActionEvent.getProject()).thenReturn(projectRule.getProject());

    myAction = new ImportMlModelAction();
  }

  @Test
  public void allConditionsMet_shouldEnabledPresentation() {
    myAction.update(myMockActionEvent);
    assertThat(myMockActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void lowAgpVersion_shouldDisablePresentation() {
    when(myMockAndroidModuleModel.getModelVersion()).thenReturn(GradleVersion.parse("3.6.0"));

    myAction.update(myMockActionEvent);

    assertThat(myMockActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myMockActionEvent.getPresentation().getText()).isEqualTo(
      AndroidBundle.message("android.wizard.action.requires.new.agp", TITLE, MIN_AGP_VERSION));
  }

  @Test
  public void lowMinSdkApi_shouldDisablePresentation() {
    when(myMockAndroidModuleInfo.getMinSdkVersion()).thenReturn(new AndroidVersion(MIN_SDK_VERSION - 2));

    myAction.update(myMockActionEvent);

    assertThat(myMockActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myMockActionEvent.getPresentation().getText()).isEqualTo(
      AndroidBundle.message("android.wizard.action.requires.minsdk", TITLE, MIN_SDK_VERSION));
  }
}
