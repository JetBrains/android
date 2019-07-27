/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.SdkUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link ThemeEditorUtils} and indirectly {@link com.android.tools.idea.javadoc.AndroidJavaDocRenderer}.
 */
public class ThemeEditorUtilsTest extends AndroidTestCase {
  private static final Pattern OPERATION_PATTERN = Pattern.compile("\\$\\$([A-Z_]+)\\{\\{(.*?)\\}\\}");
  private static final Pattern TEMPORARY_RENDER_FILE_PATTERN = Pattern.compile("'file:\\S+([/\\\\])render\\d*.png'");

  private String mySdkPlatformPath;
  private String mySdkPlatformRes;

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  private void compareWithGoldenFile(@NotNull String text, @NotNull String goldenFile) throws IOException {
    Path file = Paths.get(goldenFile);
    String goldenText = new String(Files.readAllBytes(file), UTF_8);
    goldenText = goldenText.replace("$$ANDROID_SDK_PATH", mySdkPlatformPath);
    goldenText = goldenText.replace("$$ANDROID_SDK_RES", mySdkPlatformRes);
    Matcher matcher = OPERATION_PATTERN.matcher(goldenText);
    StringBuffer processedGoldenText = new StringBuffer();

    while (matcher.find()) {
      String operation = matcher.group(1);
      String value = matcher.group(2);
      if (operation.equals("MAKE_URL")) {
        value = SdkUtils.fileToUrl(new File(value)).toString();
      }
      else if (operation.equals("MAKE_SYSTEM_DEPENDENT_PATH")) {
        value = PathUtil.toSystemDependentName(value);
        // Escape all the backslashes so they don't get treated as back references by the regex engine later.
        if (File.separatorChar == '\\') {
          value = value.replace("\\", "\\\\");
        }
      }
      else {
        // Ignore if we don't know how to handle that - may be accidental pattern match
        continue;
      }
      matcher.appendReplacement(processedGoldenText, value);
    }
    matcher.appendTail(processedGoldenText);

    // Add line breaks after "<BR/>" tags for results that are easier to read.
    // Golden files already have these line breaks, so there's no need to process them the same way.
    text = StringUtil.replace(text, "<BR/>", "<BR/>\n");
    matcher = TEMPORARY_RENDER_FILE_PATTERN.matcher(text);
    text = matcher.replaceAll("'file:/some/directory/render.png'");

    assertEquals(String.format("Comparing to golden file %s failed", file.normalize()), processedGoldenText.toString(), text);
  }

  public void testMinApiLevel() {
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", FN_ANDROID_MANIFEST_XML);
    assertEquals(11, ThemeEditorUtils.getMinApiLevel(myModule));
  }

  public void testGenerateWordEnumeration() {
    assertEquals("", ThemeEditorUtils.generateWordEnumeration(Collections.emptyList()));
    assertEquals("one", ThemeEditorUtils.generateWordEnumeration(Collections.singletonList("one")));
    assertEquals("one and two", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two")));
    assertEquals("one, two and Three", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two", "Three")));
  }
}
