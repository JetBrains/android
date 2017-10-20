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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.util.PositionInFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;

import java.util.concurrent.Future;

import static com.android.tools.idea.testing.ProjectFiles.createFileInProjectRoot;

/**
 * Tests for {@link DependencyPositionFinder}.
 */
public class DependencyPositionFinderTest extends IdeaTestCase {
  private VirtualFile myBuildFile;
  private DependencyPositionFinder myDependencyPositionFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuildFile = createFileInProjectRoot(getProject(), "build.gradle");
    myDependencyPositionFinder = new DependencyPositionFinder();
  }

  // http://b/68017092
  public void testFindDependencyPositionAccessesDocumentWithReadAccess() throws Exception {
    // Run DependencyPositionFinder#findDependencyPosition in a different thread to make sure that there should be the method invocation
    // occurs in an explicit ReadAction. Otherwise the method will run in the EDT, which has read access, and we wouldn't be able to verify
    // the fix.
    Future<PositionInFile> future = ApplicationManager.getApplication().executeOnPooledThread(
      () -> myDependencyPositionFinder.findDependencyPosition("foo", myBuildFile));
    future.get();

    // If the test passes, the document was successfully retrieved from the virtual file, with read access.
  }
}