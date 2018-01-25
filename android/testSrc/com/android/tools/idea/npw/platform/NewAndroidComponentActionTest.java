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

import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.android.builder.model.AndroidProject.*;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NewAndroidComponentActionTest {
  private AnActionEvent myActionEvent;
  private AndroidFacet mySelectedAndroidFacet;

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.inMemory();

  @Before
  public void setUp() throws Exception {
    mySelectedAndroidFacet = AndroidFacet.getInstance(projectRule.getModule());
    mySelectedAndroidFacet.getConfiguration().setModel(mock(AndroidModel.class));

    DataContext dataContext = mock(DataContext.class);
    when(dataContext.getData(LangDataKeys.MODULE.getName())).thenReturn(mySelectedAndroidFacet.getModule());

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
  public void lowLevelApiPresentationShouldBeDisabled() {
    new NewAndroidComponentAction("templateCategory", "templateName", HIGHEST_KNOWN_API + 1).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires minSdk");
  }

  @Test
  public void appTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_APP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void instantTypePresentationShouldBeDisabledForIapp() {
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_INSTANTAPP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
  }

  @Test
  public void libraryTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_LIBRARY);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void testTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_TEST);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void featureTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }
}
