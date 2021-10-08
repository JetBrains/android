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
package com.android.tools.idea;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;


public class Stamper {

  static final Map<String, String> RES_PATH;
  static final Map<String, String> BASE_PATH;

  static {
    RES_PATH = new HashMap<>();
    RES_PATH.put("linux", "");
    RES_PATH.put("win", "");
    RES_PATH.put("mac", "Contents/Resources/");
    RES_PATH.put("mac_arm", "Contents/Resources/");
    BASE_PATH = new HashMap<>();
    BASE_PATH.put("linux", "");
    BASE_PATH.put("win", "");
    BASE_PATH.put("mac", "Contents/");
    BASE_PATH.put("mac_arm", "Contents/");
  }

  private static Map<String, String> readStatusFile(String infoFile) throws IOException {
    List<String> lines = Files.readAllLines(Paths.get(infoFile));
    Map<String, String> ret = new HashMap<>();
    for (String line : lines) {
      String[] parts = line.split(" ", 2);
      ret.put(parts[0], parts[1]);
    }
    return  ret;
  }

  private static String getBuildId(Map<String, String> buildInfo) {
    String label = buildInfo.get("BUILD_EMBED_LABEL");
    return label == null || label.isEmpty() ? "SNAPSHOT" : label;
  }


  private static String formatBuildDate(Map<String, String> buildVersion) {
    long timestamp = Long.parseLong(buildVersion.get("BUILD_TIMESTAMP"));
    long epochMilli = timestamp * 1000L;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    return sdf.format(new Date(epochMilli));
  }


  private static String stampAppInfo(String buildDate, String build, String micro, String patch, String full, String eap, String content) {
    content = content.replaceAll("__BUILD__", build.substring(3)); // Without the product code, e.g. 'AI-'
    content = content.replaceAll("__BUILD_DATE__", buildDate);
    content = content.replaceAll("__BUILD_NUMBER__", build);

    String versionProp = "(<version[^/]* %s=\")[^\"]*(\")";
    String value = "$1%s$2";

    content = content.replaceFirst(String.format(versionProp, "micro"), String.format(value, micro));
    content = content.replaceFirst(String.format(versionProp, "patch"), String.format(value, patch));
    content = content.replaceFirst(String.format(versionProp, "full"), String.format(value, full));
    content = content.replaceFirst(String.format(versionProp, "eap"), String.format(value, eap));

    return content;
  }


  /**
   * Stamps a plugin.xml with the build ids
   */
  private static String stampPluginFile(String build, String content) {
    // TODO: Start with the IJ way of doing this, but move to a more robust / strict later.
    content = content.replaceFirst("<version>[\\d.]*</version>", String.format("<version>%s</version>", build));
    content = content.replaceFirst("<idea-version\\s+since-build=\"\\d+\\.\\d+\"\\s+until-build=\"\\d+\\.\\d+\"",
                     String.format("<idea-version since-build=\"%s\" until-build=\"%s\"", build, build));
    content = content.replaceFirst("<idea-version\\s+since-build=\"\\d+\\.\\d+\"",
                     String.format("<idea-version since-build=\"%s\"", build));

    String anchor = content.contains("</id>") ? "</id>" : "</name>";
    if (!content.contains("<version>")) {
      content = content.replaceFirst(anchor, String.format("%s\n  <version>%s</version>", anchor, build));
    }
    if (!content.contains("<idea-version since-build")) {
      content = content.replaceFirst(anchor, String.format("%s\n  <idea-version since-build=\"%s\" until-build=\"%s\"/>", anchor, build, build));
    }

    return content;
  }

  public static byte[] toByteArray(InputStream is) throws IOException {
    ByteArrayOutputStream data = new ByteArrayOutputStream();
    byte[] buf = new byte[65536];
    int zr = 0;
    while ((zr = is.read(buf)) != -1) {
      data.write(buf, 0, zr);
    }
    return data.toByteArray();
  }

  private static class Entry {
    public String name;
    public String content;
  }

  /**
   * Finds a file in a zip of zips. The file to look for is called
   * <subEntry> and it is looked for in all the entries of
   * <zipPath> that match the end <entryEndsWith>. It returns
   * a pair with the entry where it was found, and its content.
   */
  private static Entry findFile(String zipPath, String entryEndsWith, String subEntry) throws IOException {
    Entry ret = null;
    try (ZipFile zip = new ZipFile(zipPath)) {
      Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();
        if (entry.getName().endsWith(entryEndsWith)) {
          try (ZipArchiveInputStream zis = new ZipArchiveInputStream(zip.getInputStream(entry))) {
            ArchiveEntry nested;
            while ((nested = zis.getNextEntry()) != null) {
              if (nested.getName().equals(subEntry)) {
                if (ret != null) {
                  System.out.println("Multiple " + subEntry + " found in " + zipPath + "!" + entry);
                  System.exit(1);
                }
                ret = new Entry();
                ret.name = entry.getName();
                ret.content = new String(toByteArray(zis), StandardCharsets.UTF_8);
              }
            }
          }
        }
      }
    }
    if (ret == null) {
      System.out.println(subEntry + " not found in " + zipPath);
      System.exit(1);
    }
    return ret;
  }


  private static String readFile(String zipPath, String entry, String subEntry) throws IOException {
    String data;
    try (FileSystem zip = FileSystems.newFileSystem(Paths.get(zipPath), null)) {
      Path path = zip.getPath(entry);
      if (subEntry != null) {
        try (FileSystem sub = FileSystems.newFileSystem(path, null)) {
          data = new String(Files.readAllBytes(sub.getPath(subEntry)), StandardCharsets.UTF_8);
        }
      } else {
        data = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      }
    }
    return data;
  }

  private static void writeFile(ZipArchiveOutputStream zip, String data, String name, String subEntry) throws IOException {
    ZipArchiveEntry entry = new ZipArchiveEntry(name);
    entry.setExternalAttributes(0100664L << 16);
    zip.putArchiveEntry(entry);
    if (subEntry == null) {
      zip.write(data.getBytes(StandardCharsets.UTF_8));
    } else {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipArchiveOutputStream nested = new ZipArchiveOutputStream(bytes)) {
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(subEntry);
        nested.putArchiveEntry(zipEntry);
        nested.write(data.getBytes(StandardCharsets.UTF_8));
        nested.closeArchiveEntry();
      }
      zip.write(bytes.toByteArray());
    }
    zip.closeArchiveEntry();
  }

  /**
   * Stamps a plugin's plugin.xml file. if <full> is true it performs
   * a full stamping and tag fixing (such as adding missing tags, fixing
   * from and to versions etc). Otherwise it simple replaces the build
   * number.
   */
  private static void stampPlugin(String platform, String os, Map<String, String> buildInfo, boolean full, String src, String dst) throws IOException {
    String resourcePath = RES_PATH.get(os);
    Entry jar = findFile(src, ".jar", "META-INF/plugin.xml");
    String content = jar.content;
    String bid = getBuildId(buildInfo);
    if (full) {
      String buildTxt = readFile(platform, resourcePath + "build.txt", null);
      content = stampPluginFile(buildTxt.substring(3), content);
    }

    content = content.replaceAll("__BUILD_NUMBER__", bid);
    try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(new FileOutputStream(dst))) {
      writeFile(zip, content, jar.name.toString(), "META-INF/plugin.xml");
    }
  }


  private static void stampPlatform(String platform,
                                    String os,
                                    Map<String, String> buildInfo,
                                    Map<String, String> buildVersion,
                                    String eap,
                                    String micro,
                                    String patch,
                                    String full,
                                    String out) throws IOException {
    String resourcePath = RES_PATH.get(os);
    String basePath = BASE_PATH.get(os);
    String bid = getBuildId(buildInfo);

    String buildTxt = readFile(platform, resourcePath + "build.txt", null);
    String appInfo = readFile(platform, basePath + "lib/resources.jar", "idea/AndroidStudioApplicationInfo.xml");

    String info = null;
    if (os.equals("linux")) {
      info = readFile(platform, basePath + "product-info.json", null);
      info = info.replaceAll("__BUILD_NUMBER__", bid);
    } else if (os.equals("mac") || os.equals("mac_arm")) {
      info = readFile(platform, basePath + "Info.plist", null);
      info = info.replaceAll("__BUILD_NUMBER__", bid);
    }

    buildTxt = buildTxt.replaceAll("__BUILD_NUMBER__", bid);
    String buildDate = formatBuildDate(buildVersion);
    appInfo = stampAppInfo(buildDate, buildTxt, micro, patch, full, eap, appInfo);

    try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(new FileOutputStream(out))) {
      writeFile(zip, buildTxt, resourcePath + "build.txt", null);
      writeFile(zip, appInfo, basePath + "lib/resources.jar", "idea/AndroidStudioApplicationInfo.xml");

      if (os.equals("linux")) {
        writeFile(zip, info, basePath + "product-info.json", null);
      }
      else if (os.equals("mac") || os.equals("mac_arm")) {
        writeFile(zip, info, basePath + "Info.plist", null);
      }
    }
  }

  private static void exitWithUsage() {
    System.exit(1);
  }

  public static void main(String[] strings) throws IOException {
    Iterator<String> args = Arrays.asList(strings).iterator();
    String stampPluginSrc = null;
    String stampPluginDst = null;
    String stampPlatformPluginSrc = null;
    String stampPlatformPluginDst = null;
    String os = null;
    String micro = null;
    String patch = null;
    String full = null;
    String eap = null;
    String platformSrc = null;
    String platformDst = null;
    String infoFile = null;
    String versionFile = null;
    while (args.hasNext()) {
      String arg = args.next();
      switch (arg) {
        case "--stamp_plugin":
          if (!args.hasNext()) exitWithUsage();
          stampPluginSrc = args.next();
          if (!args.hasNext()) exitWithUsage();
          stampPluginDst = args.next();
          break;
        case "--stamp_platform_plugin":
          if (!args.hasNext()) exitWithUsage();
          stampPlatformPluginSrc = args.next();
          if (!args.hasNext()) exitWithUsage();
          stampPlatformPluginDst = args.next();
          break;
        case "--stamp_platform":
          if (!args.hasNext()) exitWithUsage();
          platformDst = args.next();
          break;
        case "--os":
          if (!args.hasNext()) exitWithUsage();
          os = args.next();
          if (!new HashSet<>(Arrays.asList("linux", "mac", "mac_arm", "win")).contains(os)) {
            exitWithUsage();
          }
          break;
        case "--version_micro":
          if (!args.hasNext()) exitWithUsage();
          micro = args.next();
          break;
        case "--version_patch":
          if (!args.hasNext()) exitWithUsage();
          patch = args.next();
          break;
        case "--version_full":
          if (!args.hasNext()) exitWithUsage();
          full = args.next();
          break;
        case "--eap":
          if (!args.hasNext()) exitWithUsage();
          eap = args.next();
          break;
        case "--platform":
          if (!args.hasNext()) exitWithUsage();
          platformSrc = args.next();
          break;
        case "--info_file":
          if (!args.hasNext()) exitWithUsage();
          infoFile = args.next();
          break;
        case "--version_file":
          if (!args.hasNext()) exitWithUsage();
          versionFile = args.next();
          break;
        default:
          exitWithUsage();
          break;
      }
    }
    Map<String, String> buildInfo = readStatusFile(infoFile);
    Map<String, String> buildVersion = readStatusFile(versionFile);

    if (platformDst != null) {
      stampPlatform(platformSrc, os, buildInfo, buildVersion, eap, micro, patch, full, platformDst);
    }

    if (stampPlatformPluginDst != null) {
      stampPlugin(platformSrc, os, buildInfo, false, stampPlatformPluginSrc, stampPlatformPluginDst);
    }

    if (stampPluginDst != null) {
      stampPlugin(platformSrc, os, buildInfo, true, stampPluginSrc, stampPluginDst);
    }
  }
}
