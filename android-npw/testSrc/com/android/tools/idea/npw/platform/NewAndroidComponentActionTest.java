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
package com.android.tools.idea.npw.platform;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.npw.actions.NewAndroidComponentAction;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.template.Category;
import com.android.tools.idea.wizard.template.TemplateConstraint;
import com.android.tools.module.AndroidModuleInfo;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class NewAndroidComponentActionTest {
  private AnActionEvent myActionEvent;
  private AndroidFacet mySelectedAndroidFacet;

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.inMemory();

  @Before
  public void setUp() {
    VirtualFile file = projectRule.getFixture().addFileToProject(
      "src/Test.kt", "fun a() {}"
    ).getVirtualFile();

    mySelectedAndroidFacet = AndroidFacet.getInstance(projectRule.getModule());
    assertNotNull(mySelectedAndroidFacet);
    AndroidModel.set(mySelectedAndroidFacet, mock(AndroidModel.class));

    AndroidModuleInfo mockAndroidModuleInfo = mock(AndroidModuleInfo.class);
    when(mockAndroidModuleInfo.getMinSdkVersion()).thenReturn(new AndroidVersion(1));
    when(mockAndroidModuleInfo.getBuildSdkVersion()).thenReturn(new AndroidVersion(1));

    StudioAndroidModuleInfo.setInstanceForTest(mySelectedAndroidFacet, mockAndroidModuleInfo);

    DataContext dataContext = SimpleDataContext.builder()
      .add(PlatformCoreDataKeys.MODULE, projectRule.getModule())
      .add(PlatformCoreDataKeys.VIRTUAL_FILE, file)
      .build();

    Presentation presentation = new Presentation();
    presentation.setEnabled(false);

    myActionEvent = TestActionEvent.createTestEvent(dataContext);
  }

  @Test
  public void nonInstantAppPresentationShouldBeEnabled() {
    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void lowMinSdkApiPresentationShouldBeDisabled() {
    new NewAndroidComponentAction(Category.Other, "templateName", HIGHEST_KNOWN_API + 1).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires minSdk");
  }

  @Test
  public void noAndroidXSupportPresentationShouldBeDisabled() {
    Collection<TemplateConstraint> constraints = new ArrayList<>();
    constraints.add(TemplateConstraint.AndroidX);
    new NewAndroidComponentAction(Category.Other, "templateName", 0, constraints).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myActionEvent.getPresentation().getText()).contains("Requires AndroidX support");
  }

  @Test
  public void appTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_APP);

    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void instantTypePresentationShouldBeDisabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_INSTANTAPP);

    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isFalse();
  }

  @Test
  public void libraryTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);

    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void testTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_TEST);

    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void featureTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_FEATURE);

    new NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent);

    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void verifyTemplateDialog() {
    TestProjectSystem testProjectSystem = new TestProjectSystem(projectRule.getProject());
    testProjectSystem.useInTests();

    mySelectedAndroidFacet.getConfiguration().setProjectType(PROJECT_TYPE_FEATURE);

    AtomicReference<ModelWizard> modelWizardReference = new AtomicReference<>(null);
    NewAndroidComponentAction action = new NewAndroidComponentAction(
      Category.Other,
      "Empty Activity",
      0,
      ImmutableSet.of(),
      (modelWizard, dialogTitle, project) -> {
        modelWizardReference.set(modelWizard);
        return Unit.INSTANCE;
      }
      );
    action.update(myActionEvent);
    assertThat(myActionEvent.getPresentation().isEnabled()).isTrue();

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> action.actionPerformed(myActionEvent));
    ModelWizard modelWizard = modelWizardReference.get();
    assertNotNull(modelWizard);

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      modelWizard.getContentPanel().setSize(640, 480);
      FakeUi fakeUi = new FakeUi(modelWizard.getContentPanel(), 1.0f, false, projectRule.getTestRootDisposable());
      try {
        fakeUi.layoutAndDispatchEvents();
      }
      catch (InterruptedException ignored) {
      }
      assertNull(fakeUi.findComponent(ComboBox.class, (combo) -> "ModuleTemplateCombo".equals(combo.getName())));
    });

    Disposer.dispose(modelWizard);
  }
}
