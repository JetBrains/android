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
import java.nio.file.Files;
import org.jetbrains.annotations.NotNull;

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

    for (Path license : myLicenses) {
      sb.append("------------ License file: ");
      sb.append(myHome.relativize(license));
      sb.append("------------");
      sb.append("\n\n");
      sb.append(getLicenseText(license));
      sb.append("\n\n");;
    }

    return sb.toString();
  }

  @NotNull
  private static String getLicenseText(@NotNull Path path) {
    try {
      return Files.readString(path, Charsets.UTF_8)
        .replaceAll("(?s)<style(\\s*)>.*?</style(\\s*)>", "")
        .replaceAll("<.*?>", "");
    }
    catch (IOException e) {
      return "";
    }
  }
}
