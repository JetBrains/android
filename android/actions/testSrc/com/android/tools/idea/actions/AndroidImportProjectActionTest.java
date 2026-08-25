/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.adtimport.actions.AndroidImportProjectAction;
import com.google.common.base.Joiner;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link AndroidImportProjectAction}.
 */
@SuppressWarnings("UnstableApiUsage")
public class AndroidImportProjectActionTest extends HeavyPlatformTestCase {
  private static final String LAST_IMPORTED_LOCATION = "last.imported.location";

  private VirtualFile myProjectRootDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectRootDir = PlatformTestUtil.getOrCreateProjectBaseDir(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Workaround for b/356483696: XDebuggerManagerImpl preloading races with test tear-down, causing false-positive leaks.
      com.intellij.xdebugger.XDebuggerManager.getInstance(myProject);
    }
    super.tearDown();
  }

  public void testFindImportTargetWithDirectoryAndWithoutGradleOrEclipseFiles() {
    assertEquals(myProjectRootDir, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithDirectoryAndGradleBuildFile() throws IOException {
    VirtualFile file = createChildFile(SdkConstants.FN_BUILD_GRADLE);
    assertEquals(file, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithDirectoryAndGradleSettingsFile() throws IOException {
    VirtualFile file = createChildFile(SdkConstants.FN_SETTINGS_GRADLE);
    assertEquals(file, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testActionPerformed_withLastImportedLocation() throws IOException {
    createChildFile(SdkConstants.FN_BUILD_GRADLE);
    VirtualFile[] toSelectCaptured = new VirtualFile[1];
    mockFileChooserFactory(toSelectCaptured);

    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, myProjectRootDir.getPath());

    AndroidImportProjectAction action = new AndroidImportProjectAction("Import Project...", null, null);
    AnActionEvent event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(myProject));
    action.actionPerformed(event);

    assertEquals(myProjectRootDir, toSelectCaptured[0]);
    assertEquals(myProjectRootDir.getPath(), PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION));
  }

  public void testActionPerformed_withoutLastImportedLocation() throws IOException {
    createChildFile(SdkConstants.FN_BUILD_GRADLE);
    VirtualFile[] toSelectCaptured = new VirtualFile[1];
    mockFileChooserFactory(toSelectCaptured);

    PropertiesComponent.getInstance().unsetValue(LAST_IMPORTED_LOCATION);

    AndroidImportProjectAction action = new AndroidImportProjectAction("Import Project...", null, null);
    AnActionEvent event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(myProject));
    action.actionPerformed(event);

    assertNull(toSelectCaptured[0]);
    assertEquals(myProjectRootDir.getPath(), PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION));
  }

  public void testActionPerformed_withInvalidLastImportedLocation() throws IOException {
    createChildFile(SdkConstants.FN_BUILD_GRADLE);
    VirtualFile[] toSelectCaptured = new VirtualFile[1];
    mockFileChooserFactory(toSelectCaptured);

    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, "/non/existent/path/that/does/not/exist");

    AndroidImportProjectAction action = new AndroidImportProjectAction("Import Project...", null, null);
    AnActionEvent event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(myProject));
    action.actionPerformed(event);

    assertNull(toSelectCaptured[0]);
    assertEquals(myProjectRootDir.getPath(), PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION));
  }

  private void mockFileChooserFactory(VirtualFile[] toSelectCaptured) {
    ServiceContainerUtil.replaceService(
      ApplicationManager.getApplication(),
      FileChooserFactory.class,
      new FileChooserFactoryImpl() {
        @NotNull
        @Override
        public FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                   @Nullable Project project,
                                                   @Nullable Component parent) {
          return (proj, toSelect) -> {
            toSelectCaptured[0] = toSelect.length > 0 ? toSelect[0] : null;
            return new VirtualFile[]{myProjectRootDir};
          };
        }
      },
      getTestRootDisposable()
    );
  }

  @NotNull
  private VirtualFile createChildFile(@NotNull String name, @NotNull String... contents) throws IOException {
    File file = new File(myProjectRootDir.getPath(), name);
    assertTrue(FileUtilRt.createIfNotExists(file));
    if (contents.length > 0) {
      String text = Joiner.on(System.lineSeparator()).join(contents);
      FileUtil.writeToFile(file, text);
    }
    VirtualFile vFile = myProjectRootDir.getFileSystem().refreshAndFindFileByPath(file.getPath());
    assertNotNull(vFile);
    return vFile;
  }
}
