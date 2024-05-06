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

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Locates the appropriate licenses to display from the Studio installation. This includes:
 * <ol>
 *   <li>The main IDE LICENSE.txt and NOTICE.txt files</li>
 *   <li>The licenses for the libraries shared IDE wide, located at $root/license/* </li>
 *   <li>The licenses for 3rd party libraries used in Google provided plugins. plugins/$googlePlugins/licenses/*</li>
 * </ol>
 */
public class LicensesLocator {
  private final Path myIdeHome;
  private final boolean myOnMac;

  private final List<String> myGooglePlugins = Arrays.asList(
    "android",
    "firebase",
    "firebase-testing",
    "google-appindexing",
    "google-cloud-tools",
    "google-cloud-tools-core",
    "google-login",
    "google-services",
    "test-recorder"
  );

  public LicensesLocator(@NotNull Path ideHome, boolean isMacLayout) {
    myIdeHome = ideHome;
    myOnMac = isMacLayout;
  }

  @NotNull
  public List<Path> getLicenseFiles() {
    List<Path> licenses = new ArrayList<>();

    licenses.addAll(getIdeLicenseAndNotice());
    licenses.addAll(getIdeWideThirdPartyLibLicenses());

    for (String plugin : myGooglePlugins) {
      licenses.addAll(getThirdPartyLibrariesForPlugin(plugin));
    }

    return licenses;
  }

  @NotNull
  private List<Path> getIdeLicenseAndNotice() {
    Path root = myIdeHome;
    if (myOnMac) {
      root = root.resolve("Resources");
    }

    return ImmutableList.of(
      root.resolve("NOTICE.txt"),
      root.resolve("LICENSE.txt")
    );
  }

  @NotNull
  private List<Path> getIdeWideThirdPartyLibLicenses() {
    try (Stream<Path> stream = Files.list(myIdeHome.resolve("license"))) {
      return stream.sorted().collect(Collectors.toList());
    }
    catch (IOException e) {
      Logger.getInstance(LicensesLocator.class).error(e);
      return ImmutableList.of();
    }
  }

  @NonNull
  private List<Path> getThirdPartyLibrariesForPlugin(@NonNull String plugin) {
    Path pluginLicenseFolder = Paths.get(myIdeHome.toString(), "plugins", plugin, "lib", "licenses");
    if (Files.isDirectory(pluginLicenseFolder)) {
      try (Stream<Path> stream = Files.list(pluginLicenseFolder)) {
        return stream.sorted().collect(Collectors.toList());
      }
      catch (IOException e) {
        Logger.getInstance(LicensesLocator.class).warn(e);
        return ImmutableList.of();
      }
    }
    else {
      return ImmutableList.of();
    }
  }
}
