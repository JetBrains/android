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
package com.android.tools.idea.templates;

import com.android.tools.idea.testing.legacy.AndroidGradleTestCase;

import java.io.File;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.util.io.FileUtil.join;

public class CreateGradleWrapperTest extends AndroidGradleTestCase {

  public void testCreateGradleWrapper() throws Exception {
    File baseDir = getBaseDirPath(getProject());
    createGradleWrapper(baseDir);

    assertFilesExist(baseDir, FN_GRADLE_WRAPPER_UNIX, FN_GRADLE_WRAPPER_WIN, FD_GRADLE, FD_GRADLE_WRAPPER,
                     join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_JAR), join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES));
  }

}
