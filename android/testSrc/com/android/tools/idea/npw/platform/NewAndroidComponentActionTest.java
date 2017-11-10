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
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.android.builder.model.AndroidProject.*;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NewAndroidComponentActionTest {
  private Disposable myDisposable;
  private AnActionEvent myActionEvent;
  private MockAndroidFacet mySelectedAndroidFacet;
  private MockAndroidFacet myOtherAndroidFacet;

  @Before
  public void setUp() throws Exception {
    myDisposable = Disposer.newDisposable();

    FacetTypeRegistry facetTypeRegistry = mock(FacetTypeRegistry.class);
    when(facetTypeRegistry.findFacetType(AndroidFacet.ID)).thenReturn(mock(AndroidFacetType.class));

    MockApplicationEx mockApplication = new MockApplicationEx(myDisposable);
    ApplicationManager.setApplication(mockApplication, myDisposable);
    mockApplication.getPicoContainer().registerComponentInstance(FacetTypeRegistry.class.getName(), facetTypeRegistry);

    ModuleManager myModuleManager = mock(ModuleManager.class);

    Project project = mock(Project.class);
    Disposer.register(myDisposable, project);
    when(project.getPicoContainer()).thenReturn(mockApplication.getPicoContainer());
    when(project.getComponent(ModuleManager.class)).thenReturn(myModuleManager);

    mySelectedAndroidFacet = new MockAndroidFacet(project);
    myOtherAndroidFacet = new MockAndroidFacet(project);

    when(myModuleManager.getModules()).thenReturn(new Module[] {mySelectedAndroidFacet.getModule(), myOtherAndroidFacet.getModule()});

    DataContext dataContext = mock(DataContext.class);
    when(dataContext.getData(LangDataKeys.MODULE.getName())).thenReturn(mySelectedAndroidFacet.getModule());

    Presentation presentation = new Presentation();
    presentation.setEnabled(false);

    myActionEvent = mock(AnActionEvent.class);
    when(myActionEvent.getDataContext()).thenReturn(dataContext);
    when(myActionEvent.getPresentation()).thenReturn(presentation);
  }

  @After
  public void tearDown() throws Exception {
    Disposer.dispose(myDisposable);
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
  public void appTypePresentationShouldBeDisabledForIapp() {
    myOtherAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_APP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
  }

  @Test
  public void instantTypePresentationShouldBeDisabledForIapp() {
    myOtherAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_INSTANTAPP);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
  }

  @Test
  public void libraryTypePresentationShouldBeEnabledForIapp() {
    myOtherAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_LIBRARY);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void testTypePresentationShouldBeEnabledForIapp() {
    myOtherAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_TEST);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void featureTypePresentationShouldBeEnabledForIapp() {
    myOtherAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);
    mySelectedAndroidFacet.setProjectType(PROJECT_TYPE_FEATURE);

    new NewAndroidComponentAction("templateCategory", "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }


  private static class MockAndroidFacet extends AndroidFacet {
    private int myProjectType = PROJECT_TYPE_APP;

    protected MockAndroidFacet(Project project) {
      super(mock(Module.class), AndroidFacet.NAME, mock(AndroidFacetConfiguration.class));

      FacetManager facetManager = mock(FacetManager.class);

      Module module = getModule();
      Disposer.register(project, module);
      when(module.getComponent(FacetManager.class)).thenReturn(facetManager);
      when(module.getProject()).thenReturn(project);

      when(facetManager.getFacetByType(AndroidFacet.ID)).thenReturn(this);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      AndroidModuleInfo androidModuleInfo = mock(AndroidModuleInfo.class);
      when(androidModuleInfo.getMinSdkVersion()).thenReturn(new AndroidVersion(HIGHEST_KNOWN_API));

      //noinspection unchecked
      return (T)androidModuleInfo;
    }

    @Nullable
    @Override
    public AndroidModel getAndroidModel() {
      return mock(AndroidModel.class);
    }

    @Override
    public int getProjectType() {
      return myProjectType;
    }

    @Override
    public void setProjectType(int type) {
      myProjectType = type;
    }
  }
}
