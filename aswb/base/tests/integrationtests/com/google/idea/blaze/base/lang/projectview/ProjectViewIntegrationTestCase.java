/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import org.junit.Before;

/** Project view file specific integration test base */
public abstract class ProjectViewIntegrationTestCase extends BlazeIntegrationTestCase {
  protected EditorTestHelper editorTest;

  @Before
  public final void doSetup() {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    registerProjectService(ProjectViewManager.class, new MockProjectViewManager());
    editorTest = new EditorTestHelper(getProject(), testFixture);
  }
}
