/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.actions;

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.util.Objects;
import org.jetbrains.android.actions.CreateResourceDirectoryDialogBase.ValidatorFactory;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class CreateResourceDirectoryDialogTest {
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.withAndroidModels();
  private PsiDirectory myResDirectory;
  private CreateResourceDirectoryDialog myDialog;

  @Before
  public void setUp() throws Throwable {
    ApplicationManager.getApplication().invokeAndWait((Runnable)() -> {
      myProjectRule.setupProjectFrom(new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    });
    AndroidFacet facet = AndroidFacet.getInstance(Objects.requireNonNull(gradleModule(myProjectRule.getProject(), ":")));
    assertNotNull(facet);
    myProjectRule.getFixture().addFileToProject("src/main/res/create", "");
    VirtualFile resVirtualFile = Iterables.getOnlyElement(ResourceFolderManager.getInstance(facet).getFolders());
    myResDirectory =
      ReadAction.compute((ThrowableComputable<PsiDirectory, Throwable>)() -> PsiManager.getInstance(myProjectRule.getProject())
        .findDirectory(resVirtualFile));
  }

  @After
  public void tearDown() throws Exception {
    if (myDialog != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myDialog.getDisposable()));
    }
  }

  private void initDialog(boolean forceDirectoryDoesNotExist) {
    Project project = myProjectRule.getProject();
    Application application = ApplicationManager.getApplication();
    ValidatorFactory factory = Mockito.mock(ValidatorFactory.class);

    application.invokeAndWait(() -> myDialog =
      new CreateResourceDirectoryDialog(project, myProjectRule.getModule(), null, myResDirectory, null, factory,
                                        forceDirectoryDoesNotExist));
  }

  @Test
  public void testDoValidateWhenSubdirectoryDoesNotExist() {
    initDialog(false);

    myDialog.getDirectoryNameTextField().setText("layout");
    assertNull(myDialog.doValidate());
  }

  @Test
  public void testDoValidateWhenSubdirectoryExists() throws Throwable {
    initDialog(false);

    ThrowableComputable<PsiFileSystemItem, Throwable> createLayoutSubdirectory = () -> myResDirectory.createSubdirectory("layout");
    PsiFileSystemItem subdirectory = WriteAction.computeAndWait(createLayoutSubdirectory);

    myDialog.getDirectoryNameTextField().setText("layout");

    String expected = subdirectory.getVirtualFile().getPresentableUrl() + " already exists. Use a different qualifier.";
    assertEquals(expected, ReadAction.compute((ThrowableComputable<String, Throwable>)() -> myDialog.doValidate().message));
  }

  @Test
  public void testCanIgnoreSubdirectoryCreation() throws Throwable {
    initDialog(true);

    ThrowableComputable<PsiFileSystemItem, Throwable> createLayoutSubdirectory = () -> myResDirectory.createSubdirectory("layout");
    PsiFileSystemItem subdirectory = WriteAction.computeAndWait(createLayoutSubdirectory);

    myDialog.getDirectoryNameTextField().setText("layout");
    assertNull(ReadAction.compute((ThrowableComputable<ValidationInfo, Throwable>)() -> myDialog.doValidate()));
  }

  @Test
  public void testSourceSets() {
    myResDirectory = null;
    initDialog(true);

    assertEquals(
      "_main_ (src/main/res), debug (src/debug/res), release (src/release/res), _android_test_ (src/androidTest/res), androidTestDebug (src/androidTestDebug/res)",
      String.join(", ", myDialog.getSourceSets()));
  }
}
