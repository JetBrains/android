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
package com.android.tools.idea.io;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link FilePaths}.
 */
public class FilePathsTest {
  @Test
  public void pathToIdeaUrlWithRegularFile() {
    File path = new File("Users/foo/myFolder/file.txt");
    String url = FilePaths.pathToIdeaUrl(path);
    Assert.assertEquals("file://Users/foo/myFolder/file.txt", url);
  }

  @Test
  public void pathToIdeaUrlWithJarFile() {
    File path = new File("Users/foo/myFolder/file.jar");
    String url = FilePaths.pathToIdeaUrl(path);
    Assert.assertEquals("jar://Users/foo/myFolder/file.jar!/", url);
  }

  @Test
  public void pathToIdeaUrlWithZipFile() {
    File path = new File("Users/foo/myFolder/file.zip");
    String url = FilePaths.pathToIdeaUrl(path);
    Assert.assertEquals("jar://Users/foo/myFolder/file.zip!/", url);
  }
}