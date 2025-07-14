/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle.sync;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.ProjectFiles;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidModuleDataService}.
 */
@RunsInEdt
public class AndroidModuleDataServiceTest {
  @Mock private AndroidModuleValidator myValidator;

  private AndroidModuleDataService myService;

  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public RuleChain rule = RuleChain.outerRule(new EdtRule()).around(projectRule);

  @Before
  public void setup() throws Exception {
    initMocks(this);
    AndroidModuleValidator.Factory validatorFactory = mock(AndroidModuleValidator.Factory.class);
    when(validatorFactory.create(projectRule.getProject())).thenReturn(myValidator);

    myService = new AndroidModuleDataService(validatorFactory);
  }

  @Test
  public void testGetTargetDataKey() {
    assertThat(myService.getTargetDataKey()).isSameAs(ANDROID_MODEL);
  }

  @Test
  public void testImportDataWithoutModels() {
    Project project = projectRule.getProject();
    Module appModule = ProjectFiles.createModule(project, "app");
    FacetManager.getInstance(appModule).createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project);

    myService.importData(Collections.emptyList(), project, modelsProvider, Collections.emptyMap());
    assertThat(FacetManager.getInstance(appModule).findFacet(AndroidFacet.ID, AndroidFacet.NAME)).isNull();
  }

}

