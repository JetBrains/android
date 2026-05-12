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
package com.android.tools.idea.npw.assetstudio;

import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.npw.assetstudio.IconGenerator.IconOptions;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link IconGenerator}.
 */
@RunWith(JUnit4.class)
@RunsInEdt
public final class IconGeneratorTest {
  @Rule
  public final EdtAndroidProjectRule myProjectRule = onEdt(AndroidProjectRule.inMemory());

  @Test
  public void generateIconsIsCancelledWhenDisposed() throws InterruptedException {
    CountDownLatch taskGenerationStartedLatch = new CountDownLatch(1);
    CountDownLatch taskGenerationCancelledLatch = new CountDownLatch(1);

    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull
      @Override
      public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) {
        throw new RuntimeException("Should not be called");
      }

      @NotNull
      @Override
      public IconOptions createOptions(boolean forPreview) {
        throw new RuntimeException("Should not be called");
      }

      @NotNull
      @Override
      protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context,
                                                                        @NotNull IconOptions options,
                                                                        @NotNull String name) {
        List<Callable<GeneratedIcon>> tasks = new ArrayList<>();
        CountDownLatch waitForDisposal = new CountDownLatch(1);
        tasks.add(() -> {
          taskGenerationStartedLatch.countDown(); // Broadcast that we are now starting to execute a task
          try {
            waitForDisposal.await();
          }
          catch (InterruptedException interruptedException) {
            taskGenerationCancelledLatch.countDown();
            throw interruptedException;
          }
          return new GeneratedXmlResource("name", new PathString(""), IconCategory.REGULAR, "xmlText");
        });

        return tasks;
      }
    };

    Disposer.register(myProjectRule.getFixture().getTestRootDisposable(), iconGenerator);

    new Thread(() -> {
      try {
        boolean taskGenerationStarted = taskGenerationStartedLatch.await(1, TimeUnit.SECONDS); // Wait for the task to start.
        assertThat(taskGenerationStarted).isTrue();
        Disposer.dispose(iconGenerator);
      }
      catch (InterruptedException ignored) {
      }
    }).start();

    iconGenerator.generateIcons(new IconOptions(true));

    boolean wasCancelled = taskGenerationCancelledLatch.await(1, TimeUnit.SECONDS); // Wait for the task to be cancelled.
    assertThat(wasCancelled).isTrue();
  }

  @Test
  public void generateIntoFileMap() {
    VectorIconGenerator generator = new VectorIconGenerator(myProjectRule.getProject(), 24);
    generator.sourceAsset().setValue(new VectorAsset());
    generator.outputName().set("foo");
    Set<File> files = generator.generateIntoFileMap(new File("/app/main/res")).keySet();
    assertThat(files).containsExactly(new File("/app/main/res/drawable/foo.xml"));
  }

  @Test
  public void writeTextToDisk_addsLicenseHeader() throws Exception {
    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull @Override public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) { return PLACEHOLDER_IMAGE; }
      @NotNull @Override public IconOptions createOptions(boolean forPreview) { return new IconOptions(forPreview); }
      @NotNull @Override protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) { return Collections.emptyList(); }
    };

    File outputDir = FileUtilRt.createTempDirectory("IconGeneratorTest", null);
    String xmlText = "<vector/>";
    String fileName = "test_icon.xml";
    GeneratedXmlResource resource = new GeneratedXmlResource("test", new PathString(fileName), IconCategory.REGULAR, xmlText);
    resource.setClipart(true);

    generateIconsToDisk(iconGenerator, Collections.singletonList(resource), outputDir);
    VirtualFile outputFile = VfsUtil.findFileByIoFile(new File(outputDir, fileName), true);
    assertThat(outputFile).isNotNull();
    String content = new String(outputFile.contentsToByteArray(), StandardCharsets.UTF_8);
    assertThat(content).contains("Copyright (C)");
    assertThat(content).contains("Licensed under the Apache License, Version 2.0");
    assertThat(content).endsWith("<vector/>");
  }

  @Test
  public void writeTextToDisk_doesNotAddLicenseHeader() throws Exception {
    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull @Override public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) { return PLACEHOLDER_IMAGE; }
      @NotNull @Override public IconOptions createOptions(boolean forPreview) { return new IconOptions(forPreview); }
      @NotNull @Override protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) { return Collections.emptyList(); }
    };

    File outputDir = FileUtilRt.createTempDirectory("IconGeneratorTest", null);
    String xmlText = "<vector/>";
    String fileName = "test_icon.xml";
    GeneratedXmlResource resource = new GeneratedXmlResource("test", new PathString(fileName), IconCategory.REGULAR, xmlText);
    resource.setClipart(false);

    generateIconsToDisk(iconGenerator, Collections.singletonList(resource), outputDir);
    VirtualFile outputFile = VfsUtil.findFileByIoFile(new File(outputDir, fileName), true);
    assertThat(outputFile).isNotNull();
    String content = new String(outputFile.contentsToByteArray(), StandardCharsets.UTF_8);
    assertThat(content).doesNotContain("Copyright (C)");
    assertThat(content).doesNotContain("Licensed under the Apache License, Version 2.0");
    assertThat(content).isEqualTo("<vector/>");
  }

  @Test
  public void isClipart_returnsTrueForClipart() {
    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull @Override public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) { return PLACEHOLDER_IMAGE; }
      @NotNull @Override public IconOptions createOptions(boolean forPreview) { return new IconOptions(forPreview); }
      @NotNull @Override protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) { return Collections.emptyList(); }
    };
    VectorAsset asset = new VectorAsset();
    asset.setClipart(true);
    iconGenerator.sourceAsset().setValue(asset);

    assertThat(iconGenerator.isClipart()).isTrue();
  }

  @Test
  public void isClipart_returnsFalseForLocalFile() {
    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull @Override public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) { return PLACEHOLDER_IMAGE; }
      @NotNull @Override public IconOptions createOptions(boolean forPreview) { return new IconOptions(forPreview); }
      @NotNull @Override protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) { return Collections.emptyList(); }
    };
    VectorAsset asset = new VectorAsset();
    asset.setClipart(false);
    iconGenerator.sourceAsset().setValue(asset);

    assertThat(iconGenerator.isClipart()).isFalse();
  }

  private void generateIconsToDisk(IconGenerator iconGenerator, Collection<? extends GeneratedIcon> icons, File outputDir) {
    Map<File, GeneratedIcon> pathIconMap = new HashMap<>();
    for (GeneratedIcon icon : icons) {
      PathString iconPath = icon.getOutputPath();
      if (iconPath != null) {
        pathIconMap.put(new File(outputDir, iconPath.getNativePath()), icon);
      }
    }
    WriteAction.run(() -> iconGenerator.writeIconsToDisk(pathIconMap));
  }
}
