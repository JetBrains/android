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
package com.android.tools.idea.actions.license;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class LicenseTextCollectorTest {
  @Test
  public void collectAllLicenses() throws Exception {
    Path testRoot = Paths.get(AndroidTestBase.getTestDataPath(), "licenseLocator");
    Path ideHome = testRoot.resolve("linux");

    CompletableFuture<String> cf =
      new LicenseTextCollector(ideHome, new LicensesLocator(ideHome, false).getLicenseFiles()).getLicenseText();

    String expected =
      "------------ License file: NOTICE.txt------------\n" +
      "\n" +
      "main notice\n" +
      "\n" +
      "------------ License file: LICENSE.txt------------\n" +
      "\n" +
      "main license\n" +
      "\n" +
      "------------ License file: license/ant_license.txt------------\n" +
      "\n" +
      "ant\n" +
      "\n" +
      "------------ License file: license/third-party-libraries.html------------\n" +
      "\n" +
      "\n" +
      "\n" +
      "  SoftwareLicense\n" +
      "  \n" +
      "    ANTLR 4.9 Runtime 4.9.2\n" +
      "    BSD 3-Clause\n" +
      "  \n" +
      "\n" +
      "\n" +
      "------------ License file: plugins/android/lib/licenses/antlr4-runtime-4.5.3.jar-NOTICE------------\n" +
      "\n" +
      "antlr4\n" +
      "\n" +
      "------------ License file: plugins/android/lib/licenses/asm-5.0.3-NOTICE------------\n" +
      "\n" +
      "asm5\n" +
      "\n";

    if (SystemInfo.isWindows) {
      expected = expected.replace('/', '\\');
    }

    assertThat(cf.get(20, TimeUnit.SECONDS)).isEqualTo(expected);
  }
}
