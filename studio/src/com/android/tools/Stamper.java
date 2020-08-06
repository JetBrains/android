/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Stamper {

  public static void main(String[] strings) throws Exception {
    Iterator<String> args = Arrays.asList(strings).iterator();
    Map<String, String> plugins = new HashMap<>();
    Map<String, String> files = new HashMap<>();
    String buildFile = null;
    String infoFile = "";
    while (args.hasNext()) {
      String arg = args.next();
      if (arg.equals("--stamp_plugin") && args.hasNext()) {
        String from = args.next();
        if (!args.hasNext()) {
          printUsage();
          return;
        }
        plugins.put(from, args.next());
      } else if (arg.equals("--stamp_build") && args.hasNext()) {
        String from = args.next();
        if (!args.hasNext()) {
          printUsage();
          return;
        }
        files.put(from, args.next());
      } else if (arg.equals("--build_file") && args.hasNext()) {
        buildFile = args.next();
      } else if (arg.equals("--info_file") && args.hasNext()){
        infoFile = args.next();
      } else {
        printUsage();
        return;
      }
    }

    String buildId = buildFile != null ? readBuildFile(buildFile) : "";
    Map<String, String> buildInfo = infoFile != null ? readInfoFile(infoFile) : new HashMap<>();
    for (Map.Entry<String, String> plugin : plugins.entrySet()) {
      stampPlugin(buildInfo, buildId, plugin.getKey(), plugin.getValue());
    }
    for (Map.Entry<String, String> file : files.entrySet()) {
      stampFile(buildInfo, file.getKey(), file.getValue());
    }
  }

  private static Map<String, String> readInfoFile(String infoFile) throws Exception {
    List<String> lines = Files.readAllLines(Paths.get(infoFile), StandardCharsets.UTF_8);
    Map<String, String> ret = new HashMap<>();
    for (String line : lines) {
      String[] parts = line.split(" ", 2);
      ret.put(parts[0], parts[1]);
    }
    return ret;
  }

  private static String readBuildFile(String buildFile) throws Exception {
    return new String(Files.readAllBytes(Paths.get(buildFile)), StandardCharsets.UTF_8);
  }

  private static String stampBuildNumber(Map<String, String> buildInfo, String data) {
    String label = buildInfo.get("BUILD_EMBED_LABEL");
    if (label == null || label.isEmpty()) {
      label = "SNAPSHOT";
    }
    return data.replaceAll("__BUILD_NUMBER__", label);
  }

  private static String version(String buildId, Map<String, String> buildInfo) {
    if (!buildId.startsWith("AI-")) {
      throw new IllegalStateException("Unexpected product code in build id: " + buildId);
    }
    return stampBuildNumber(buildInfo, buildId.substring(3));
  }

  public static void stampFile(Map<String, String> buildInfo, String in, String out) throws Exception {
    byte[] before = Files.readAllBytes(Paths.get(in));
    String content = new String(before, StandardCharsets.UTF_8);
    content = stampBuildNumber(buildInfo, content);
    Files.write(Paths.get(out), content.getBytes(StandardCharsets.UTF_8));
  }

  public static void stampPlugin(Map<String, String> buildInfo, String buildId, String in, String out) throws Exception {
    String version = version(buildId, buildInfo);
    byte[] before = Files.readAllBytes(Paths.get(in));
    String content = new String(before, StandardCharsets.UTF_8);
    // TODO: Start with the IJ way of doing this, but move to a more robust/strict later.
    content = content.replaceFirst(
        "<version>[\\d.]*</version>",
        String.format("<version>%s</version>", version))
      .replaceFirst(
        "<idea-version\\s+since-build=\"\\d+\\.\\d+\"\\s+until-build=\"\\d+\\.\\d+\"",
        String.format("<idea-version since-build=\"%s\" until-build=\"%s\"", version, version))
      .replaceFirst(
        "<idea-version\\s+since-build=\"\\d+\\.\\d+\"",
        String.format("<idea-version since-build=\"%s\"", version));

    String anchor = content.contains("</id>") ? "</id>" : "</name>";
    if (!content.contains("<version>")) {
      content = content.replace(anchor, String.format("%s\n  <version>%s</version>", anchor, version));
    }
    if (!content.contains("<idea-version since-build")) {
      content = content.replace(anchor, String.format("%s\n  <idea-version since-build=\"%s\" until-build=\"%s\"/>", anchor, version, version));
    }

    Files.write(Paths.get(out), content.getBytes(StandardCharsets.UTF_8));
  }

  private static void printUsage() {
    System.out.println("stamper A tool to stamp Android Studio files with build information.");
    System.out.println("Usage:");
    System.out.println("   stamper <options>");
    System.out.println("Args (--stamp* arguments can be added multiple times):");
    System.out.println("     --stamp_plugin in out: Creates a stamped version of the plugin.xml <in> in <out>.");
    System.out.println("     --stamp_build in out: Replaces __BUILD_NUMBER__ with the final value.");
    System.out.println("     --build_file: Path to the build.txt distribution file.");
    System.out.println("     --info_file: Path to the bazel build info file.");
  }
}
