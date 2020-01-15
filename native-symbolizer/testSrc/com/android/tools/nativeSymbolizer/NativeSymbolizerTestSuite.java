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
package com.android.tools.nativeSymbolizer;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.intellij.openapi.util.SystemInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses(NativeSymbolizerTestSuite.class)  // a suite mustn't contain itself
public class NativeSymbolizerTestSuite extends IdeaTestSuiteBase {
  static {
    try {
      symlinkToIdeaHome("tools/adt/idea/native-symbolizer/testData");

      String lldbPrebuiltDir;
      if (SystemInfo.isLinux) {
        lldbPrebuiltDir = "prebuilts/tools/linux-x86_64/lldb";
      }
      else if (SystemInfo.isMac) {
        lldbPrebuiltDir = "prebuilts/tools/darwin-x86_64/lldb";
      }
      else if (SystemInfo.isWindows) {
        lldbPrebuiltDir = "prebuilts/tools/windows-x86_64/lldb";
      }
      else {
        throw new RuntimeException("Platform not recognized: " + SystemInfo.OS_NAME);
      }

      // Point to lldb with a symlink.
      Path targetPath = TestUtils.getWorkspaceFile(lldbPrebuiltDir).toPath();
      Path linkName = Paths.get("tools/idea/bin/lldb");
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    }
    catch (Throwable e) {
      // See b/143359533 for why we are handling errors here
      System.err.println("ERROR: Error initializing test suite, tests will likely fail following this error");
      e.printStackTrace();
    }
  }
}