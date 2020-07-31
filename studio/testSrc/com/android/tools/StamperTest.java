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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class StamperTest {

  @Test
  public void testChangeVersion() throws Exception {
    File info = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    File build = createFile("build.txt", "AI-1234.__BUILD_NUMBER__");
    File before = createFile("p.xml", "<version>1</version>");
    File res = newFile("res.xml");
    Stamper.main(new String[]{
      "--info_file",
      info.getAbsolutePath(),
      "--build_file",
      build.getAbsolutePath(),
      "--stamp_plugin",
      before.getAbsolutePath(),
      res.getAbsolutePath(),
    });

    assertEquals("<version>1234.3333</version>", readFile(res));
  }

  @Test
  public void testChangeSinceUntil() throws Exception {
    File info = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    File build = createFile("build.txt", "AI-1234.__BUILD_NUMBER__");
    File before = createFile("p.xml", "<idea-version since-build=\"1.1\" until-build=\"2.1\">");
    File res = newFile("res.xml");
    Stamper.main(new String[]{
      "--info_file",
      info.getAbsolutePath(),
      "--build_file",
      build.getAbsolutePath(),
      "--stamp_plugin",
      before.getAbsolutePath(),
      res.getAbsolutePath(),
    });

    assertEquals("<idea-version since-build=\"1234.3333\" until-build=\"1234.3333\">", readFile(res));
  }

  @Test
  public void testChangeSince() throws Exception {
    File info = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    File build = createFile("build.txt", "AI-1234.__BUILD_NUMBER__");
    File before = createFile("p.xml", "<idea-version since-build=\"1.1\">");
    File res = newFile("res.xml");
    Stamper.main(new String[]{
      "--info_file",
      info.getAbsolutePath(),
      "--build_file",
      build.getAbsolutePath(),
      "--stamp_plugin",
      before.getAbsolutePath(),
      res.getAbsolutePath(),
    });

    assertEquals("<idea-version since-build=\"1234.3333\">", readFile(res));
  }

  @Test
  public void testInject() throws Exception {
    File info = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    File build = createFile("build.txt", "AI-1234.__BUILD_NUMBER__");
    File before = createFile("p.xml", "<id>x</id>");
    File res = newFile("res.xml");
    Stamper.main(new String[]{
      "--info_file",
      info.getAbsolutePath(),
      "--build_file",
      build.getAbsolutePath(),
      "--stamp_plugin",
      before.getAbsolutePath(),
      res.getAbsolutePath(),
    });

    assertEquals(
      "<id>x</id>\n" +
      "  <idea-version since-build=\"1234.3333\" until-build=\"1234.3333\"/>\n" +
      "  <version>1234.3333</version>",
      readFile(res));
  }

  @Test
  public void testStampFile() throws Exception {
    File info = createFile("info.txt", "BUILD_EMBED_LABEL 3333");
    File build = createFile("build.txt", "AI-1234.__BUILD_NUMBER__");
    File before = createFile("file.txt", "Change the __BUILD_NUMBER__");
    File res = newFile("res.txt");
    Stamper.main(new String[]{
      "--info_file",
      info.getAbsolutePath(),
      "--build_file",
      build.getAbsolutePath(),
      "--stamp_build",
      before.getAbsolutePath(),
      res.getAbsolutePath(),
    });

    assertEquals("Change the 3333", readFile(res));
  }

  private String readFile(File file) throws Exception {
    byte[] bytes = Files.readAllBytes(file.toPath());
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private File newFile(String name) {
    String tmp = System.getenv("TEST_TMPDIR");
    return new File(tmp, name);
  }

  private File createFile(String name, String content) throws Exception {
    File file = newFile(name);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(content.getBytes(StandardCharsets.UTF_8));
    };
    return file;
  }
}
