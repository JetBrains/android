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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Test;

public class StamperTest {

  private ArrayList<String> readZip(Path zip) throws Exception {
    return readZip(new FileInputStream(zip.toFile()));
  }

  private ArrayList<String> readZip(InputStream zip) throws Exception {
    ArrayList<String> ret = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(zip)) {
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {
        byte[] bytes = Stamper.toByteArray(zis);
        if (zipEntry.getName().endsWith(".zip") || zipEntry.getName().endsWith(".jar")) {
          ArrayList<String> inner = readZip(new ByteArrayInputStream(bytes));
          for (int i = 0; i < inner.size(); i += 2) {
            ret.add(zipEntry.getName() + "!" + inner.get(i * 2));
            ret.add(inner.get(i * 2 + 1));
          }
        }
        else {
          ret.add(zipEntry.getName());
          ret.add(new String(bytes, StandardCharsets.UTF_8));
        }
        zipEntry = zis.getNextEntry();
      }
    }
    return ret;
  }

  private Path createZip(String zip, String... args) throws Exception {
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> contents = new ArrayList<>();
    for (int i = 0; i < args.length; i += 2) {
      names.add(args[i]);
      contents.add(args[i + 1]);
    }
    byte[] bytes = createZip(names, contents);
    Path file = getPath(zip);
    Files.write(file, bytes);
    return file;
  }

  private byte[] createZip(ArrayList<String> names, ArrayList<String> contents) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        int ix = name.indexOf('!');
        if (ix != -1) {
          String prefix = name.substring(0, ix + 1);
          ArrayList<String> innerNames = new ArrayList<>();
          ArrayList<String> innerContents = new ArrayList<>();
          i--;
          while (i + 1 < names.size() && names.get(i + 1).startsWith(prefix)) {
            i++;
            innerNames.add(names.get(i).substring(ix + 1));
            innerContents.add(contents.get(i));
          }
          zipOut.putNextEntry(new ZipEntry(name.substring(0, ix)));
          byte[] bytes = createZip(innerNames, innerContents);
          zipOut.write(bytes);
        }
        else {
          zipOut.putNextEntry(new ZipEntry(name));
          byte[] bytes = contents.get(i).getBytes(StandardCharsets.UTF_8);
          zipOut.write(bytes);
        }
      }
    }
    return out.toByteArray();
  }


  private Path getPath(String path) {
    String tmp = System.getenv("TEST_TEMPDIR");
    if (tmp == null) {
      tmp = System.getProperty("java.io.tmpdir");
    }
    return Paths.get(tmp, path);
  }

  private Path createFile(String name, String content) throws IOException {
    Path path = getPath(name);
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    return path;
  }

  @Test
  public void testChangeVersion() throws Exception {
    Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    Path vol = createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
    Path platform = createZip("platform.zip",
                              "build.txt", "AI-1234.__BUILD_NUMBER__");
    Path before = createZip("plugin.zip",
                            "plugin/a/lib/a.jar!META-INF/plugin.xml", "<version>1</version>");
    Path res = getPath("res.zip");
    Stamper.main(new String[]{
      "--os", "linux",
      "--info_file", stable.toString(),
      "--version_file", vol.toString(),
      "--platform", platform.toString(),
      "--stamp_plugin", before.toString(), res.toString()});

    Assert.assertEquals(Arrays.asList(
      "plugin/a/lib/a.jar!META-INF/plugin.xml", "<version>1234.3333</version>"
    ), readZip(res));
  }

  @Test
  public void testChangeSinceUntil() throws Exception {
    Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    Path vol = createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
    Path platform = createZip("platform.zip", "build.txt", "AI-1234.__BUILD_NUMBER__");
    Path before = createZip("plugin.zip",
                            "plugin/a/lib/a.jar!META-INF/plugin.xml", "<idea-version since-build=\"1.1\" until-build=\"2.1\">");
    Path res = getPath("res.zip");
    Stamper.main(new String[]{
      "--os", "linux",
      "--info_file", stable.toString(),
      "--version_file", vol.toString(),
      "--platform", platform.toString(),
      "--stamp_plugin", before.toString(), res.toString(),
    });

    Assert.assertEquals(Arrays.asList(
                          "plugin/a/lib/a.jar!META-INF/plugin.xml", "<idea-version since-build=\"1234.3333\" until-build=\"1234.3333\">"),
                        readZip(res));
  }

   @Test
   public void testChangeSince() throws Exception {
     Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
     Path vol = createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
     Path platform = createZip("platform.zip", "build.txt", "AI-1234.__BUILD_NUMBER__");
     Path before = createZip("plugin.zip", "plugin/a/lib/a.jar!META-INF/plugin.xml", "<idea-version since-build=\"1.1\">");
     Path res = getPath("res.zip");
     Stamper.main(new String[] {
       "--os", "linux",
       "--info_file", stable.toString(),
       "--version_file",vol.toString(),
     "--platform", platform.toString(),
       "--stamp_plugin", before.toString(), res.toString()
     });

     Assert.assertEquals(Arrays.asList("plugin/a/lib/a.jar!META-INF/plugin.xml", "<idea-version since-build=\"1234.3333\">"), readZip(res));
   }

   @Test
   public void testStampLinuxPlatform() throws Exception {
     Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
     Path vol =createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
     Path platform = createZip("platform.zip",
                               "bin/appInfo.xml", "<build number=\"AI-__BUILD__\" date=\"__BUILD_DATE__\">\n" +
                                                  "<version major=\"4\" minor=\"3\" micro=\"2\" patch=\"1\" full=\"a\" eap=\"false\" >",
                               "build.txt", "AI-1234.__BUILD_NUMBER__",
                               "product-info.json", "Info __BUILD_NUMBER__ __BUILD_NUMBER__",
                               "lib/resources.jar!idea/AndroidStudioApplicationInfo.xml", "<build number=\"AI-__BUILD__\" date=\"__BUILD_DATE__\">\n" +
                                                                                          "<version major=\"4\" minor=\"3\" micro=\"2\" patch=\"1\" full=\"a\" eap=\"false\" >");
     Path res = getPath("res.zip");
     Stamper.main(new String[] {
       "--os", "linux",
       "--info_file", stable.toString(),
       "--version_file", vol.toString(),
       "--platform", platform.toString(),
       "--version_micro", "33",
       "--version_patch", "44",
       "--version_full", "{0} Canary 5",
       "--eap", "true",
       "--stamp_platform", res.toString(),
     });

     Assert.assertEquals(Arrays.asList(
       "build.txt", "AI-1234.3333",
       "lib/resources.jar!idea/AndroidStudioApplicationInfo.xml", "<build number=\"AI-1234.3333\" date=\"202008192252\">\n" +
                                                                  "<version major=\"4\" minor=\"3\" micro=\"33\" patch=\"44\" full=\"{0} Canary 5\" eap=\"true\" >",
                         "bin/appInfo.xml", "<build number=\"AI-1234.3333\" date=\"202008192252\">\n" +
                                            "<version major=\"4\" minor=\"3\" micro=\"33\" patch=\"44\" full=\"{0} Canary 5\" eap=\"true\" >",
                         "product-info.json", "Info 3333 3333"),
        readZip(res));
   }

   @Test
   public void testStampMacPlatform() throws Exception {
     Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
     Path vol =createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
     Path platform = createZip("platform.zip",
       "Contents/bin/appInfo.xml", "<build number=\"AI-__BUILD__\" date=\"__BUILD_DATE__\">\n" +
                                   "<version major=\"4\" minor=\"3\" micro=\"2\" patch=\"1\" full=\"a\" eap=\"false\" >",
       "Contents/Resources/build.txt", "AI-1234.__BUILD_NUMBER__",
       "Contents/Info.plist", "Info __BUILD_NUMBER__ __BUILD_NUMBER__",
       "Contents/lib/resources.jar!idea/AndroidStudioApplicationInfo.xml", "<build number=\"AI-__BUILD__\" date=\"__BUILD_DATE__\">\n" +
                                                                           "<version major=\"4\" minor=\"3\" micro=\"2\" patch=\"1\" full=\"a\" eap=\"false\" >");
     Path res = getPath("res.zip");
     Stamper.main(new String[] {
       "--os", "mac",
       "--info_file", stable.toString(),
       "--version_file", vol.toString(),
     "--platform", platform.toString(),
       "--version_micro", "33",
       "--version_patch", "44",
       "--version_full", "{0} Canary 5",
       "--eap", "true",
       "--stamp_platform", res.toString()
     });

     Assert.assertEquals(Arrays.asList(
                           "Contents/Resources/build.txt", "AI-1234.3333",
                           "Contents/lib/resources.jar!idea/AndroidStudioApplicationInfo.xml", "<build number=\"AI-1234.3333\" date=\"202008192252\">\n" +
                                                                                               "<version major=\"4\" minor=\"3\" micro=\"33\" patch=\"44\" full=\"{0} Canary 5\" eap=\"true\" >",
                           "Contents/bin/appInfo.xml", "<build number=\"AI-1234.3333\" date=\"202008192252\">\n" +
                                                    "<version major=\"4\" minor=\"3\" micro=\"33\" patch=\"44\" full=\"{0} Canary 5\" eap=\"true\" >",
                           "Contents/Info.plist", "Info 3333 3333"),
     readZip(res));
   }

   @Test
   public void testInject() throws Exception {
     Path stable = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
     Path vol =createFile("volatile.txt", "BUILD_TIMESTAMP 1597877532");
     Path platform = createZip("platform.zip", "build.txt", "AI-1234.__BUILD_NUMBER__");
     Path before = createZip("plugin.zip", "plugin/a/lib/a.jar!META-INF/plugin.xml", "<id>x</id>");
     Path res = getPath("res.zip");
     Stamper.main(new String[] {
       "--os", "linux",
       "--info_file", stable.toString(),
       "--version_file", vol.toString(),
       "--platform", platform.toString(),
       "--stamp_plugin", before.toString(), res.toString(),
     });

     Assert.assertEquals(Arrays.asList(
                        "plugin/a/lib/a.jar!META-INF/plugin.xml", "<id>x</id>\n" +
                             "  <idea-version since-build=\"1234.3333\" until-build=\"1234.3333\"/>\n" +
                             "  <version>1234.3333</version>"),
                         readZip(res));
   }
}