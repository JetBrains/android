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
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.project.CustomProjectTypeImporter;
import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.mockito.Mock;

/**
 * Tests for {@link ImportApkAction}.
 */
public class ImportApkActionTest extends HeavyPlatformTestCase {
  @Mock private ImportApkAction.FileChooserDialogFactory myFileChooserDialogFactory;
  @Mock private FileChooserDialog myFileChooserDialog;
  @Mock private ExternalSystemManager<?, ?, ?, ?, ?> myExternalSystemManager;

  private MainProjectTypeImporter myProjectTypeImporter;
  private PropertiesComponentMock myPropertiesComponent;
  private File myRecentProjectLocation;
  private File myLastImportedProjectLocation;
  private RecentProjectsManagerStub myRecentProjectsManager;
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
    myRecentProjectsManager = new RecentProjectsManagerStub(myRecentProjectLocation.getPath());

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
    assertEquals(FileUtilRt.toSystemDependentName(myApkToImport.getPath()), myPropertiesComponent.getValue(LAST_IMPORTED_LOCATION));

    // See: https://issuetracker.google.com/67708415
    assertEquals(myRecentProjectLocation.getPath(), myRecentProjectsManager.getLastProjectCreationLocation());
  }

  private static class MainProjectTypeImporter extends CustomProjectTypeImporter.MainImporter {
    private final @NotNull RecentProjectsManager myRecentProjectsManager;
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

  private static final class RecentProjectsManagerStub implements RecentProjectsManager {
    private @NotNull String myLastProjectLocation;

    RecentProjectsManagerStub(@NotNull String lastProjectLocation) {
      myLastProjectLocation = lastProjectLocation;
    }

    @Override
    public @NotNull String getLastProjectCreationLocation() {
      return myLastProjectLocation;
    }

    @Override
    public void setLastProjectCreationLocation(@Nullable String lastProjectLocation) {
      myLastProjectLocation = lastProjectLocation;
    }

    @Override
    public void updateLastProjectPath() {
    }

    @Override
    public void removePath(@Nullable String path) {
    }

    @Override
    public AnAction @NotNull [] getRecentProjectsActions(boolean addClearListItem) {
      return AnAction.EMPTY_ARRAY;
    }

    @Override
    public boolean willReopenProjectOnStart() {
      return false;
    }

    @Override
    public @NotNull String suggestNewProjectLocation() {
      return "";
    }

    @Override
    public void setLastProjectCreationLocation(@Nullable Path value) {
    }

    @Override
    public @NotNull List<ProjectGroup> getGroups() {
      return Collections.emptyList();
    }

    @Override
    public void addGroup(@NotNull ProjectGroup group) {
    }

    @Override
    public void removeGroup(@NotNull ProjectGroup group) {
    }

    @Override
    public void moveProjectToGroup(@NotNull String projectPath, @NotNull ProjectGroup to) {
    }

    @Override
    public void removeProjectFromGroup(@NotNull String projectPath, @NotNull ProjectGroup from) {
    }

    @Override
    public boolean hasPath(@Nullable @SystemIndependent String path) {
      return false;
    }

    @Override
    public AnAction @NotNull [] getRecentProjectsActions(boolean addClearListItem, boolean useGroups) {
      return AnAction.EMPTY_ARRAY;
    }

    @Override
    public @Nullable Object reopenLastProjectsOnStart(@NotNull Continuation<? super Boolean> $completion) {
      return null;
    }

    @Override
    public void setActivationTimestamp(@NotNull Project project, long timestamp) {
    }
  }
}