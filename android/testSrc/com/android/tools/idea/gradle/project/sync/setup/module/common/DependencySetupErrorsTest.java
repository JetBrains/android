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

import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependencySetupErrors}.
 */
public class DependencySetupErrorsTest extends IdeaTestCase {
  private SyncMessagesStub mySyncMessages;
  private DependencySetupErrors myErrors;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    mySyncMessages = SyncMessagesStub.replaceSyncMessagesService(project);
    myErrors = new DependencySetupErrors(project, mySyncMessages);
  }

  public void testAddMissingModule() {
    myErrors.addMissingModule(":lib2", "app2", "library2.jar");
    myErrors.addMissingModule(":lib2", "app1", "library2.jar");
    myErrors.addMissingModule(":lib3", "app1", "library3.jar");
    myErrors.addMissingModule(":lib1", "app1", null);

    List<DependencySetupErrors.MissingModule> missingModules = myErrors.getMissingModules();
    assertThat(missingModules).hasSize(1);

    DependencySetupErrors.MissingModule missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib1");
    assertThat(missingModule.dependentNames).containsExactly("app1");

    missingModules = myErrors.getMissingModulesWithBackupLibraries();
    assertThat(missingModules).hasSize(2);

    missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib2");
    assertThat(missingModule.dependentNames).containsExactly("app1", "app2").inOrder();

    missingModule = missingModules.get(1);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib3");
    assertThat(missingModule.dependentNames).containsExactly("app1");
  }

  public void testAddDependentOnModuleWithoutName() {
    myErrors.addMissingName("app2");
    myErrors.addMissingName("app2");
    myErrors.addMissingName("app1");
    assertThat(myErrors.getMissingNames()).containsExactly("app1", "app2").inOrder();
  }

  public void testAddDependentOnLibraryWithoutBinaryPath() {
    myErrors.addMissingBinaryPath("app2");
    myErrors.addMissingBinaryPath("app2");
    myErrors.addMissingBinaryPath("app1");
    assertThat(myErrors.getDependentsOnLibrariesWithoutBinaryPath()).containsExactly("app1", "app2").inOrder();
  }

  public void testReportErrors() {
    myErrors.addMissingModule(":lib1", "app1", null);
    myErrors.addMissingModule(":lib2", "app2", "library2.jar");
    myErrors.addMissingModule(":lib2", "app1", "library2.jar");
    myErrors.addMissingModule(":lib3", "app1", "library3.jar");
    myErrors.addMissingName("app2");
    myErrors.addMissingName("app1");
    myErrors.addMissingBinaryPath("app2");
    myErrors.addMissingBinaryPath("app1");

    myErrors.reportErrors();

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(7);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib1' (needed by module 'app1'.)", 0);
    // @formatter:on

    message = messages.get(1);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app1' depends on modules that do not have a name.", 0);
    // @formatter:on

    message = messages.get(2);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app2' depends on modules that do not have a name.", 0);
    // @formatter:on

    message = messages.get(3);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app1' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on

    message = messages.get(4);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app2' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on

    message = messages.get(5);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(WARNING)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib2' (needed by modules: 'app1', 'app2'.)", 0)
                                            .hasMessageLine("Linking to library 'library2.jar' instead.", 1) ;
    // @formatter:on

    message = messages.get(6);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(WARNING)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib3' (needed by module 'app1'.)", 0)
                                            .hasMessageLine("Linking to library 'library3.jar' instead.", 1) ;
    // @formatter:on
  }
}
