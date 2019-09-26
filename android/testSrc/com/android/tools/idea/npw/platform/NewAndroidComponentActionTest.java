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
package com.android.tools.idea.npw.platform;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import java.util.EnumSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.android.builder.model.AndroidProject.*;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.ANDROIDX;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public final class NewAndroidComponentActionTest {
  private AnActionEvent myActionEvent;
  private AndroidFacet mySelectedAndroidFacet;

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.inMemory();

  @Before
  public void setUp() {
    mySelectedAndroidFacet = AndroidFacet.getInstance(projectRule.getModule());
    mySelectedAndroidFacet.getConfiguration().setModel(mock(AndroidModel.class));

    AndroidModuleInfo mockAndroidModuleInfo = mock(AndroidModuleInfo.class);
    when(mockAndroidModuleInfo.getMinSdkVersion()).thenReturn(new AndroidVersion(1));
    when(mockAndroidModuleInfo.getBuildSdkVersion()).thenReturn(new AndroidVersion(1));

    AndroidFacet mockAndroidFacet = spy(mySelectedAndroidFacet);
    doReturn(mockAndroidModuleInfo).when(mockAndroidFacet).getUserData(any(Key.class));

    FacetManager mockFacetManager = mock(FacetManager.class);
    when(mockFacetManager.getFacetByType(AndroidFacet.ID)).thenReturn(mockAndroidFacet);

    Module mockModel = mock(Module.class);
    doReturn(mockFacetManager).when(mockModel).getComponent(FacetManager.class);
    when(mockModel.getProject()).thenReturn(projectRule.getProject());

    DataContext dataContext = mock(DataContext.class);
    when(dataContext.getData(LangDataKeys.MODULE.getName())).thenReturn(mockModel);

    Presentation presentation = new Presentation();
    presentation.setEnabled(false);

    myActionEvent = mock(AnActionEvent.class);
    when(myActionEvent.getDataContext()).thenReturn(dataContext);
    when(myActionEvent.getPresentation()).thenReturn(presentation);
  }

  @Test
  public void nonInstantAppPresentationShouldBeEnabled() {
    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void lowMinSdkApiPresentationShouldBeDisabled() {
    new NewAndroidComponentAction("templateCategory", "templateName", HIGHEST_KNOWN_API + 1).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires minSdk");
  }

  @Test
  public void lowMinBuildSdkApiPresentationShouldBeDisabled() {
    new NewAndroidComponentAction("templateCategory", "templateName", 0, HIGHEST_KNOWN_API + 1).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires compileSdkVersion");
  }


  @Test
  public void noAndroidXSupportPresentationShouldBeDisabled() {
    new NewAndroidComponentAction("templateCategory", "templateName", 0, 0, EnumSet.of(ANDROIDX)).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires AndroidX support");
  }

  @Test
  public void appTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_APP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void instantTypePresentationShouldBeDisabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_INSTANTAPP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
  }

  @Test
  public void libraryTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void testTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_TEST);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void featureTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_FEATURE);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }
}
