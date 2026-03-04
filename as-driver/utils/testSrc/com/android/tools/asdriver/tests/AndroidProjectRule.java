/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.tools.testlib.TestFileSystem;
import java.io.IOException;
import org.junit.rules.ExternalResource;

public class AndroidProjectRule extends ExternalResource {
  private final AndroidProject project;
  private final TestFileSystem fileSystem;

  public AndroidProjectRule(String path, TestFileSystem fileSystem) {
    this.project = new AndroidProject(path);
    this.fileSystem = fileSystem;
  }

  public AndroidProject getProject() {
    return project;
  }

  @Override
  protected void before() throws Throwable {
    super.before();
    project.install(fileSystem.getRoot());
  }

  @Override
  protected void after() {
    super.after();
    try {
      project.stopGradleDaemon();
    }
    catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
