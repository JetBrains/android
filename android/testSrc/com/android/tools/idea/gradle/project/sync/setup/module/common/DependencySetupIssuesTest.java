/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.List;

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DependencySetupIssues}.
 */
public class DependencySetupIssuesTest extends IdeaTestCase {
  @Mock private GradleSyncState mySyncState;
  @Mock private GradleSyncSummary mySyncSummary;

  private GradleSyncMessagesStub mySyncMessages;
  private DependencySetupIssues myIssues;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(mySyncState.getSummary()).thenReturn(mySyncSummary);

    Project project = getProject();
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);
    myIssues = new DependencySetupIssues(project, mySyncState, mySyncMessages);
  }

  public void testAddMissingModule() {
    myIssues.addMissingModule(":lib2", "app2", "library2.jar");
    myIssues.addMissingModule(":lib2", "app1", "library2.jar");
    myIssues.addMissingModule(":lib3", "app1", "library3.jar");
    myIssues.addMissingModule(":lib1", "app1", null);

    List<DependencySetupIssues.MissingModule> missingModules = myIssues.getMissingModules();
    assertThat(missingModules).hasSize(1);

    DependencySetupIssues.MissingModule missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib1");
    assertThat(missingModule.dependentNames).containsExactly("app1");

    missingModules = myIssues.getMissingModulesWithBackupLibraries();
    assertThat(missingModules).hasSize(2);

    missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib2");
    assertThat(missingModule.dependentNames).containsExactly("app1", "app2").inOrder();

    missingModule = missingModules.get(1);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib3");
    assertThat(missingModule.dependentNames).containsExactly("app1");

    verify(mySyncSummary, times(1)).setSyncErrorsFound(true);
  }

  public void testAddMissingModuleWithBackupLibrary() {
    myIssues.addMissingModule(":lib2", "app2", "library2.jar");
    verify(mySyncSummary, never()).setSyncErrorsFound(true);
  }

  public void testAddMissingModuleWithoutBackupLibrary() {
    myIssues.addMissingModule(":lib2", "app2", null);
    verify(mySyncSummary, times(1)).setSyncErrorsFound(true);
  }

  public void testAddDependentOnLibraryWithoutBinaryPath() {
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app1");
    assertThat(myIssues.getDependentsOnLibrariesWithoutBinaryPath()).containsExactly("app1", "app2").inOrder();

    verify(mySyncSummary, times(3)).setSyncErrorsFound(true);
  }

  public void testReportIssues() {
    myIssues.addMissingModule(":lib1", "app1", null);
    myIssues.addMissingModule(":lib2", "app2", "library2.jar");
    myIssues.addMissingModule(":lib2", "app1", "library2.jar");
    myIssues.addMissingModule(":lib3", "app1", "library3.jar");
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app1");

    myIssues.reportIssues();

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(5);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib1' (needed by module 'app1'.)", 0);
    // @formatter:on

    message = messages.get(1);
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app1' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on

    message = messages.get(2);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app2' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on

    message = messages.get(3);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(WARNING)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib2' (needed by modules: 'app1', 'app2'.)", 0)
                                            .hasMessageLine("Linking to library 'library2.jar' instead.", 1) ;
    // @formatter:on

    message = messages.get(4);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(WARNING)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib3' (needed by module 'app1'.)", 0)
                                            .hasMessageLine("Linking to library 'library3.jar' instead.", 1) ;
    // @formatter:on
  }
}
