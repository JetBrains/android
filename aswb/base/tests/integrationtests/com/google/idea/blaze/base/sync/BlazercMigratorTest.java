/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.BlazercMigrator;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BlazercMigratorTest extends BlazeIntegrationTestCase {

  private final BlazeContext context = BlazeContext.create();
  private static final String USER_BLAZERC = ".blazerc";

  @Test
  public void testCopyBlazercToWorkspace() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(true);
    ApplicationManager.getApplication()
        .runWriteAction(() -> blazercMigrator.promptAndMigrate(context));

    VirtualFile workspaceBlazerc = blazercMigrator.getWorkspaceBlazercDir().findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNotNull();
    assertThat(workspaceBlazerc.exists()).isTrue();
  }

  @Test
  public void testDoNotCopyBlazercToWorkspace() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(false);
    ApplicationManager.getApplication()
        .runWriteAction(() -> blazercMigrator.promptAndMigrate(context));

    VirtualFile workspaceBlazerc = blazercMigrator.getWorkspaceBlazercDir().findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNull();
  }

  private MockBlazercMigrator getMockBlazercMigrator(boolean userResponseToYesNoDialog) {
    VirtualFile homeBlazerc = fileSystem.createFile(USER_BLAZERC);
    assertThat(homeBlazerc).isNotNull();
    assertThat(homeBlazerc.exists()).isTrue();

    VirtualFile workspaceDir = fileSystem.findFile("workspace");
    assertThat(workspaceDir).isNotNull();
    assertThat(workspaceDir.exists()).isTrue();

    VirtualFile workspaceBlazerc = workspaceDir.findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNull();

    return new MockBlazercMigrator(homeBlazerc, workspaceDir, userResponseToYesNoDialog);
  }

  static class MockBlazercMigrator extends BlazercMigrator {
    private final boolean userResponseToYesNoDialog;
    private final VirtualFile workspaceBlazercDir;

    public MockBlazercMigrator(
        VirtualFile homeBlazerc,
        VirtualFile workspaceBlazercDir,
        boolean userResponseToYesNoDialog) {
      super(homeBlazerc, workspaceBlazercDir);
      this.userResponseToYesNoDialog = userResponseToYesNoDialog;
      this.workspaceBlazercDir = workspaceBlazercDir;
    }

    public VirtualFile getWorkspaceBlazercDir() {
      return workspaceBlazercDir;
    }

    @Override
    protected int showYesNoDialog() {
      return userResponseToYesNoDialog ? Messages.YES : Messages.NO;
    }
  }
}
