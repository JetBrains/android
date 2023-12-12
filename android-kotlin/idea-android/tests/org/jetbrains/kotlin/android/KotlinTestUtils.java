/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.kotlin.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.testFramework.TestDataFile;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;
import org.junit.Assert;

// Adapted from the Kotlin test framework (after taking over android-kotlin sources).
public class KotlinTestUtils {

  public static String navigationMetadata(@TestDataFile String testFile) {
    return testFile;
  }

  public static void assertEqualsToFile(@NotNull File expectedFile, @NotNull String actual) {
    try {
      String actualText = StringUtilsKt.trimTrailingWhitespacesAndAddNewlineAtEOF(StringUtil.convertLineSeparators(actual.trim()));

      if (!expectedFile.exists()) {
        FileUtil.writeToFile(expectedFile, actualText);
        Assert.fail("Expected data file did not exist. Generating: " + expectedFile);
      }
      String expected = FileUtil.loadFile(expectedFile, CharsetToolkit.UTF8, true);

      String expectedText = StringUtilsKt.trimTrailingWhitespacesAndAddNewlineAtEOF(StringUtil.convertLineSeparators(expected.trim()));

      if (!Objects.equals(expectedText, actualText)) {
        throw new FileComparisonFailedError("Actual data differs from file content: " + expectedFile.getName(),
                                            expected, actual, expectedFile.getAbsolutePath());
      }
    }
    catch (IOException e) {
      throw ExceptionUtilsKt.rethrow(e);
    }
  }
}
