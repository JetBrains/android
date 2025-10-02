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
package com.android.tools.idea.apk;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.apk.ImportApkAction.LAST_IMPORTED_LOCATION;
import static com.android.tools.idea.testing.ProjectFiles.createFileInProjectRoot;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.project.CustomProjectTypeImporter;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link ImportApkAction}.
 */
public class ImportApkActionTest extends HeavyPlatformTestCase {
  @Mock private ImportApkAction.FileChooserDialogFactory myFileChooserDialogFactory;
  @Mock private FileChooserDialog myFileChooserDialog;
  @Mock private ExternalSystemManager<?, ?, ?, ?, ?> myExternalSystemManager;
  @Mock private RecentProjectsManager myRecentProjectsManager;

  private MainProjectTypeImporter myProjectTypeImporter;
  private PropertiesComponentMock myPropertiesComponent;
  private File myRecentProjectLocation;
  private File myLastImportedProjectLocation;
  private VirtualFile myApkToImport;

  private ImportApkAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    File projectPath = getBaseDirPath(project);
    myLastImportedProjectLocation = projectPath;

    myPropertiesComponent = new PropertiesComponentMock();
    myPropertiesComponent.setValue(LAST_IMPORTED_LOCATION, myLastImportedProjectLocation.getPath());

    myRecentProjectLocation = projectPath.getParentFile();
    when(myRecentProjectsManager.getLastProjectCreationLocation()).thenReturn(myRecentProjectLocation.getPath());

    when(myFileChooserDialogFactory.create(myExternalSystemManager)).thenReturn(myFileChooserDialog);

    myApkToImport = createFileInProjectRoot(project, "test.apk");

    myProjectTypeImporter = new MainProjectTypeImporter(myRecentProjectsManager);
    myAction = new ImportApkAction(myPropertiesComponent, myProjectTypeImporter, myFileChooserDialogFactory, myRecentProjectsManager,
                                   myExternalSystemManager);
  }

  // See: https://issuetracker.google.com/73730693
  public void testUpdateWhenExternalSystemManagerIsNull() {
    myAction = new ImportApkAction(myPropertiesComponent, myProjectTypeImporter, myFileChooserDialogFactory, myRecentProjectsManager,
                                   null);
    Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(true);
    AnActionEvent actionEvent = mock(AnActionEvent.class);
    when(actionEvent.getPresentation()).thenReturn(presentation);

    myAction.update(actionEvent);
    assertFalse(presentation.isEnabledAndVisible());
  }

  public void testActionPerformed() {
    // Simulate user selecting an APK file.
    VirtualFile lastLocation = findFileByIoFile(myLastImportedProjectLocation, true);
    when(myFileChooserDialog.choose(null, lastLocation)).thenReturn(new VirtualFile[]{myApkToImport});

    myAction.actionPerformed(mock(AnActionEvent.class));

    assertSame(myApkToImport, myProjectTypeImporter.importedApkFile); // Verify that the APK file was imported.
    assertEquals(toSystemDependentName(myApkToImport.getPath()), myPropertiesComponent.getValue(LAST_IMPORTED_LOCATION));

    // See: https://issuetracker.google.com/67708415
    assertEquals(myRecentProjectLocation.getPath(), myRecentProjectsManager.getLastProjectCreationLocation());
  }

  private static class MainProjectTypeImporter extends CustomProjectTypeImporter.MainImporter {
    @NotNull private final RecentProjectsManager myRecentProjectsManager;
    VirtualFile importedApkFile;

    MainProjectTypeImporter(@NotNull RecentProjectsManager recentProjectsManager) {
      myRecentProjectsManager = recentProjectsManager;
    }

    @Override
    public boolean importFileAsProject(@NotNull VirtualFile file) {
      importedApkFile = file;
      myRecentProjectsManager.setLastProjectCreationLocation((Path)null);  // Change it to verify the original value is restored.
      return true;
    }
  }
}