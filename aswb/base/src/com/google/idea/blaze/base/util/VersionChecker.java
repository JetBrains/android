/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Help class provides function to check if IDE version meet requirements.
 */
public class VersionChecker {
  private static final Logger logger = Logger.getInstance(VersionChecker.class);

  /**
   * Returns true if the version of IDE is not the same as product-info. If mismatch is found, a background upgrade may happen when IDE is running. In that case, user need to restart IDE to run with latest lib.
   */
  public static boolean versionMismatch() {
    BuildNumber buildNumberFromProductInfo =
      BuildNumber.fromString(readBuildNumberFromProductInfo());
    BuildNumber buildNumberFromApplicationInfo = ApplicationInfo.getInstance().getBuild();

    return !buildNumberFromProductInfo.equals(buildNumberFromApplicationInfo);
  }

  static String readBuildNumberFromProductInfo() {
    Path location = Paths.get(PathManager.getHomePath());
    // Version mismatch is unexpected on macOS (due to different installation process), but this
    // check is included as a precaution.
    if (SystemInfo.isMac) {
      location = location.resolve("Resources");
    }

    Path info = location.resolve("product-info.json");
    if (Files.exists(info)) {
      try {
        String json = Files.readString(info);
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
        return jsonObject.get("productCode").getAsString()
               + "-"
               + jsonObject.get("buildNumber").getAsString();
      }
      catch (IOException e) {
        logger.error("Error parsing product-info.json", e);
      }
    }
    return "";
  }
}
