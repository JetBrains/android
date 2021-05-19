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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LicenseTextCollector {
  private final Path myHome;
  private final List<Path> myLicenses;

  public LicenseTextCollector(@NotNull Path ideHome, @NotNull List<Path> licenses) {
    myHome = ideHome;
    myLicenses = licenses;
  }

  @NotNull
  public CompletableFuture<String> getLicenseText() {
    return CompletableFuture.supplyAsync(this::getLicenseTextSync);
  }

  private String getLicenseTextSync() {
    StringBuilder sb = new StringBuilder(10*1024);

    sb.append("<html>");
    for (Path license : myLicenses) {
      sb.append("------------ License file: ");
      sb.append(myHome.relativize(license).toString());
      sb.append("------------");
      sb.append("<br><br>");
      sb.append(getLicenseText(license.toFile()));
      sb.append("<br><br>");
    }
    sb.append("</html>");

    return sb.toString();
  }

  @NotNull
  private static String getLicenseText(@NotNull File f) {
    try {
      return Files
        .toString(f, Charsets.UTF_8).replaceAll("\\<.*?\\>", "").replace("\n", "<br>");
    }
    catch (IOException e) {
      return "";
    }
  }
}
