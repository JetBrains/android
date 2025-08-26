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
package com.android.tools.idea.gradle.project.sync.idea.data;

import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.application.ActionsKt.runWriteAction;

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.utils.FileUtils;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServicesKt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class IdeaSyncCachesTest {
  private IdeaSyncCachesInvalidator myInvalidator;

  @Rule
  public IntegrationTestEnvironmentRule rule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Before
  public void setup() throws Exception {
    myInvalidator = new IdeaSyncCachesInvalidator();
  }

  @Test
  public void testCacheIsInvalidated() {
    PreparedTestProject p = prepareTestProject(rule, AndroidCoreTestProject.SIMPLE_APPLICATION, "project");
    p.open((it) -> it, project -> {
      assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
      return null;
    });
    p.open((it) -> it, project -> {
      assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED);
      myInvalidator.invalidateCaches();
      return null;
    });
    p.open((it) -> it, project -> {
      assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
      return null;
    });
  }

  @Test
  public void testMissingJarTriggersSync() throws IOException {
    PreparedTestProject p = prepareTestProject(rule, AndroidCoreTestProject.SIMPLE_APPLICATION, "project");
    p.open((it) -> it, project -> {
      assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
      return null;
    });
    List<VirtualFile> lifecycleLiveDataLibraryPaths =
      p.open((it) -> it,
             project -> {
               assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
                 .isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED);
               return
                 ContainerUtil
                   .map(
                     Arrays.stream(LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries())
                       .filter(it -> it.getName().startsWith("Gradle: android.arch.lifecycle:livedata:"))
                       .findAny()
                       .get()
                       .getFiles(OrderRootType.CLASSES),
                     it -> {
                       VirtualFile file = VfsUtilCore.getVirtualFileForJar(it);
                       if (file == null) file = it;
                       return file;
                     });
             });
    // In order to ensure that future tests don't fail due to Gradle maintaining state we stop the daemons before deleting the
    // library files.
    //noinspection UnstableApiUsage
    GradleDaemonServicesKt.stopDaemons();

    deleteLibraryFilesFromGradleCache(lifecycleLiveDataLibraryPaths);
    for (VirtualFile file : lifecycleLiveDataLibraryPaths) {
      while(!file.getName().equals("transformed")) {
        file = file.getParent();
      }
      // Delete the workspace of artifact transforms which has the format of .../hashes/transformed/...., because Gradle doesn't allow the
      // workspace of immutable transforms to be corrupted. However, it is okay the workspace is missing and Gradle would create a new one.
      FileUtils.deleteRecursivelyIfExists(new File(file.getParent().getCanonicalPath()));
    }
    p.open((it) -> it, project -> {
      assertThat(ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
      return null;
    });
  }

  private void deleteLibraryFilesFromGradleCache(List<VirtualFile> lifecycleLiveDataLibraryPaths) {
    assertThat(lifecycleLiveDataLibraryPaths).isNotEmpty();
    // Delete all CLASSES files from the Gradle cache. When a library expires in the Gradle cache all files are deleted.
    runWriteAction(() ->{
      lifecycleLiveDataLibraryPaths.forEach(file -> {
        try {
          file.delete(this);
        }
        catch (IOException e) {
          Assert.fail(e.getMessage());
        }
      });
      return null;
    });
  }
}
