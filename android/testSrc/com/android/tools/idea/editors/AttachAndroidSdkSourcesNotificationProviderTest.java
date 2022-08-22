/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.flags.junit.RestoreFlagRule;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider.MyEditorNotificationPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AttachAndroidSdkSourcesNotificationProviderTest {
  @Rule public final AndroidProjectRule myAndroidProjectRule = AndroidProjectRule.withSdk();
  @Rule public final MockitoRule myMockitoRule = MockitoJUnit.rule();
  @Rule public final RestoreFlagRule myRestoreFlagRule = new RestoreFlagRule(StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE);

  @Mock private FileEditor myFileEditor;
  @Mock private ModelWizardDialog myModelWizardDialog;

  private TestAttachAndroidSdkSourcesNotificationProvider myProvider;

  @Before
  public void setup() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(true);

    when(myModelWizardDialog.showAndGet()).thenReturn(true);

    myProvider = new TestAttachAndroidSdkSourcesNotificationProvider(myAndroidProjectRule.getProject());
  }

  @Test
  public void getKey() {
    assertThat(myProvider.getKey().toString()).isEqualTo("add sdk sources to class");
  }

  @Test
  public void createNotificationPanel_fileIsNotJavaClass_returnsNull() {
    VirtualFile javaFile = myAndroidProjectRule.getFixture().createFile("somefile.java", "file contents");

    assertThat(javaFile.getFileType()).isEqualTo(JavaFileType.INSTANCE);
    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(javaFile);
    assertThat(panel).isNull();
  }

  @Test
  public void createNotificationPanel_javaClassNotInAndroidSdk_returnsNull() {
    VirtualFile javaClassFile = myAndroidProjectRule.getFixture().createFile("someclass.class", "file contents");

    assertThat(javaClassFile.getFileType()).isEqualTo(JavaClassFileType.INSTANCE);

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(javaClassFile);
    assertThat(panel).isNull();
  }

  @Test
  public void createNotificationPanel_javaClassInAndroidSdkAndSourcesAvailable_nullReturned() {
    VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
      PsiClass cls = JavaPsiFacade.getInstance(myAndroidProjectRule.getProject())
        .findClass("android.view.View", GlobalSearchScope.allScope(myAndroidProjectRule.getProject()));
      PsiFile file = cls.getContainingFile();
      return file.getVirtualFile();
    });

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);
    assertThat(panel).isNull();
  }

  @Test
  public void createNotificationPanel_virtualFileHasRequiredSourcesKeyButIsNull_nullReturned() {
    VirtualFile javaFile = myAndroidProjectRule.getFixture().createFile("somefile.java", "file contents");
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, null);

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(javaFile);
    assertThat(panel).isNull();
  }

  @Test
  public void createNotificationPanel_virtualFileHasRequiredSourcesKeyButIsEmpty_nullReturned() {
    VirtualFile javaFile = myAndroidProjectRule.getFixture().createFile("somefile.java", "file contents");
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, ImmutableList.of());

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(javaFile);
    assertThat(panel).isNull();
  }

  @Test
  public void createNotificationPanel_flagOff_panelHasCorrectLabel() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false);
    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);
    assertThat(panel).isNotNull();
    assertThat(panel.getText()).isEqualTo("Sources for 'SDK' not found.");
  }

  @Test
  public void createNotificationPanel_panelHasCorrectLabel() {
    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);
    assertThat(panel).isNotNull();
    assertThat(panel.getText()).isEqualTo("Android SDK sources not found.");
  }

  @Test
  public void createNotificationPanel_flagOff_panelHasDownloadAndRefreshLinks() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false);
    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);

    Map<String, Runnable> links = panel.getLinks();

    assertThat(links.keySet()).containsExactly("Download", "Refresh (if already downloaded)");
  }

  @Test
  public void createNotificationPanel_panelHasDownloadLink() {
    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);

    Map<String, Runnable> links = panel.getLinks();

    assertThat(links.keySet()).containsExactly("Download SDK Sources");
  }

  @Test
  public void createNotificationPanel_flagOff_downloadLinkDownloadsSources() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false);

    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);

    RootProvider rootProvider = AndroidSdks.getInstance().getAllAndroidSdks().get(0).getRootProvider();
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0);

    // Invoke the "Download" link, which is first in the components.
    ApplicationManager.getApplication().invokeAndWait(() -> panel.getLinks().get("Download").run());

    // Check that the link requested the correct paths, and that then sources became available.
    assertThat(myProvider.getRequestedPaths()).isNotNull();
    assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-32");
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES).length).isGreaterThan(0);
  }

  @Test
  public void createNotificationPanel_downloadLinkDownloadsSources() {
    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);

    RootProvider rootProvider = AndroidSdks.getInstance().getAllAndroidSdks().get(0).getRootProvider();
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0);

    // Invoke the "Download" link, which is first in the components.
    ApplicationManager.getApplication().invokeAndWait(() -> panel.getLinks().get("Download SDK Sources").run());

    // Check that the link requested the correct paths, and that then sources became available.
    assertThat(myProvider.getRequestedPaths()).isNotNull();
    assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-32");
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES).length).isGreaterThan(0);
  }

  @Test
  public void createNotificationPanel_virtualFileHasRequiredSourcesKey_downloadLinkHasRequestedSources() {
    List<AndroidVersion> requiredSourceVersions = ImmutableList.of(
      new AndroidVersion(30),
      new AndroidVersion(31)
    );

    VirtualFile javaFile = myAndroidProjectRule.getFixture().createFile("somefile.java", "file contents");
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, requiredSourceVersions);

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(javaFile);

    ApplicationManager.getApplication().invokeAndWait(() -> panel.getLinks().get("Download SDK Sources").run());

    // Check that the link requested the correct paths, and that then sources became available.
    assertThat(myProvider.getRequestedPaths()).isNotNull();
    assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-30", "sources;android-31");
  }

  @Test
  public void createNotificationPanel_flagOff_refreshLinkUpdatesSources() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false);

    VirtualFile virtualFile = getAndroidSdkClassWithoutSources();

    MyEditorNotificationPanel panel = invokeCreateNotificationPanel(virtualFile);

    RootProvider rootProvider = AndroidSdks.getInstance().getAllAndroidSdks().get(0).getRootProvider();
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0);

    // Invoke the "Refresh" link, which is second in the components.
    ApplicationManager.getApplication().invokeAndWait(() -> panel.getLinks().get("Refresh (if already downloaded)").run());

    assertThat(rootProvider.getFiles(OrderRootType.SOURCES).length).isGreaterThan(0);
  }

  private MyEditorNotificationPanel invokeCreateNotificationPanel(VirtualFile virtualFile) {
    return ApplicationManager.getApplication().runReadAction(
      (Computable<MyEditorNotificationPanel>)() ->
        (MyEditorNotificationPanel)myProvider.createNotificationPanel(virtualFile, myFileEditor));
  }

  private VirtualFile getAndroidSdkClassWithoutSources() {
    for (Sdk sdk : AndroidSdks.getInstance().getAllAndroidSdks()) {
      SdkModificator sdkModificator = sdk.getSdkModificator();
      sdkModificator.removeRoots(OrderRootType.SOURCES);
      ApplicationManager.getApplication().invokeAndWait(() -> sdkModificator.commitChanges());
    }

    VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
      PsiClass cls = JavaPsiFacade.getInstance(myAndroidProjectRule.getProject())
        .findClass("android.view.View", GlobalSearchScope.allScope(myAndroidProjectRule.getProject()));
      PsiFile file = cls.getContainingFile();
      return file.getVirtualFile();
    });
    return virtualFile;
  }

  /**
   * Test implementation of {@link AttachAndroidSdkSourcesNotificationProvider} that mocks the call to create an SDK download dialog.
   */
  private class TestAttachAndroidSdkSourcesNotificationProvider extends AttachAndroidSdkSourcesNotificationProvider {

    private List<String> requestedPaths;

    public TestAttachAndroidSdkSourcesNotificationProvider(@NotNull Project project) {
      super(project);
    }

    public List<String> getRequestedPaths() {
      return requestedPaths;
    }

    @Override
    protected ModelWizardDialog createSdkDownloadDialog(List<String> requestedPaths) {
      this.requestedPaths = requestedPaths;
      return myModelWizardDialog;
    }
  }
}
