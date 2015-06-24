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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.parser.DependenciesElement;
import com.android.tools.idea.gradle.dsl.parser.ExternalDependencyElement;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildFile;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.assertions.AssertExtension;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.vfs.VfsUtil.saveText;
import static com.intellij.openapi.vfs.VfsUtilCore.loadText;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslDependenciesParsingTest extends GuiTestCase {
  @NonNls private static final String APP_BUILD_GRADLE_RELATIVE_PATH = "app/build.gradle";

  @Test @IdeGuiTest
  public void testParseExternalDependenciesWithCompactNotation() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    GradleBuildFile parsedBuildFile = openAndParseAppBuildFile(projectFrame);

    List<DependenciesElement> dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);

    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    assertThat(dependency(dependencies.get(0))).hasConfigurationName("compile")
                                               .hasGroup("com.android.support")
                                               .hasName("appcompat-v7")
                                               .hasVersion("22.1.1");

    assertThat(dependency(dependencies.get(1))).hasConfigurationName("compile")
                                               .hasGroup("com.google.guava")
                                               .hasName("guava")
                                               .hasVersion("18.0");
  }

  @Test @IdeGuiTest
  public void testAddExternalDependencyWithCompactNotation() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();
    final GradleBuildFile parsedBuildFile = openAndParseAppBuildFile(projectFrame);

    List<DependenciesElement> dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    final Ref<DependenciesElement> dependenciesBlockRef = new Ref<DependenciesElement>(dependenciesBlocks.get(0));

    List<ExternalDependencyElement> dependencies = dependenciesBlockRef.get().getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            dependenciesBlockRef.get().addExternalDependency("compile", "joda-time:joda-time:2.3");
          }
        });
        parsedBuildFile.reparse();
      }
    });

    dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);

    dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(3);

    assertThat(dependency(dependencies.get(0))).hasConfigurationName("compile")
                                               .hasGroup("com.android.support")
                                               .hasName("appcompat-v7")
                                               .hasVersion("22.1.1");

    assertThat(dependency(dependencies.get(1))).hasConfigurationName("compile")
                                               .hasGroup("com.google.guava")
                                               .hasName("guava")
                                               .hasVersion("18.0");

    assertThat(dependency(dependencies.get(2))).hasConfigurationName("compile")
                                               .hasGroup("joda-time")
                                               .hasName("joda-time")
                                               .hasVersion("2.3");
  }

  @Test @IdeGuiTest
  public void testSetVersionOnExternalDependencyWithCompactNotation() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();
    final GradleBuildFile parsedBuildFile = openAndParseAppBuildFile(projectFrame);

    List<DependenciesElement> dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);

    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    final ExternalDependencyElement appCompat = dependencies.get(0);
    assertThat(dependency(appCompat)).hasConfigurationName("compile")
                                     .hasGroup("com.android.support")
                                     .hasName("appcompat-v7").hasVersion("22.1.1");

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            appCompat.setVersion("1.2.3");
          }
        });
        parsedBuildFile.reparse();
      }
    });

    dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    dependenciesBlock = dependenciesBlocks.get(0);

    dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    assertThat(dependency(dependencies.get(0))).hasConfigurationName("compile")
                                               .hasGroup("com.android.support")
                                               .hasName("appcompat-v7")
                                               .hasVersion("1.2.3");
  }

  @Test @IdeGuiTest
  public void testParseDependenciesWithMapNotation() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    replaceDependenciesInAppBuildFile(projectFrame, "compile group: 'com.google.code.guice', name: 'guice', version: '1.0'");

    GradleBuildFile parsedBuildFile = openAndParseAppBuildFile(projectFrame);
    List<DependenciesElement> dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);

    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(1);

    assertThat(dependency(dependencies.get(0))).hasConfigurationName("compile")
                                               .hasGroup("com.google.code.guice")
                                               .hasName("guice")
                                               .hasVersion("1.0");
  }

  @Test @IdeGuiTest
  public void testSetVersionOnExternalDependencyWithMapNotation() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();
    replaceDependenciesInAppBuildFile(projectFrame, "compile group: 'com.google.code.guice', name: 'guice', version: '1.0'");

    final GradleBuildFile parsedBuildFile = openAndParseAppBuildFile(projectFrame);
    List<DependenciesElement> dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);

    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(1);

    final ExternalDependencyElement guice = dependencies.get(0);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            guice.setVersion("1.2.3");
          }
        });
        parsedBuildFile.reparse();
      }
    });

    dependenciesBlocks = parsedBuildFile.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    dependenciesBlock = dependenciesBlocks.get(0);

    dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(1);

    assertThat(dependency(dependencies.get(0))).hasConfigurationName("compile")
                                               .hasGroup("com.google.code.guice")
                                               .hasName("guice")
                                               .hasVersion("1.2.3");
  }

  private static void replaceDependenciesInAppBuildFile(@NotNull IdeFrameFixture projectFrame, @NotNull final String...dependencies) {
    final VirtualFile appBuildFile = projectFrame.findFileByRelativePath(APP_BUILD_GRADLE_RELATIVE_PATH, true);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        String text = loadText(appBuildFile);
        int indexOfDependenciesBlock = text.indexOf("dependencies {");
        if (indexOfDependenciesBlock != -1) {
          final StringBuilder newText = new StringBuilder();
          newText.append(text.substring(0, indexOfDependenciesBlock));
          newText.append("dependencies {\n");
          for (String dependency : dependencies) {
            newText.append("  ").append(dependency).append('\n');
          }
          newText.append("}");
          ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, IOException>() {
            @Override
            public Void compute() throws IOException {
              saveText(appBuildFile, newText.toString());
              return null;
            }
          });
        }
      }
    });

  }

  @NotNull
  private static GradleBuildFile openAndParseAppBuildFile(@NotNull final IdeFrameFixture projectFrame) {
    String relativePath = APP_BUILD_GRADLE_RELATIVE_PATH;
    projectFrame.getEditor().open(relativePath).getCurrentFile();
    final VirtualFile appBuildFile = projectFrame.findFileByRelativePath(relativePath, true);
    GradleBuildFile parsed = execute(new GuiQuery<GradleBuildFile>() {
      @Override
      @NotNull
      protected GradleBuildFile executeInEDT() throws Throwable {
        return GradleBuildFile.parseFile(appBuildFile, projectFrame.getProject());
      }
    });
    assertNotNull(parsed);
    return parsed;
  }

  @NotNull
  private static ExternalDependencyAssertion dependency(@NotNull ExternalDependencyElement target) {
    return new ExternalDependencyAssertion(target);
  }

  private static class ExternalDependencyAssertion implements AssertExtension {
    @NotNull private final ExternalDependencyElement myTarget;

    ExternalDependencyAssertion(@NotNull ExternalDependencyElement target) {
      myTarget = target;
    }

    @NotNull
    ExternalDependencyAssertion hasConfigurationName(@NotNull String expected) {
      assertEquals("configurationName", expected, myTarget.getConfigurationName());
      return this;
    }

    @NotNull
    ExternalDependencyAssertion hasGroup(@Nullable String expected) {
      assertEquals("groupId", expected, myTarget.getGroup());
      return this;
    }

    @NotNull
    ExternalDependencyAssertion hasName(@Nullable String expected) {
      assertEquals("artifactId", expected, myTarget.getName());
      return this;
    }

    @NotNull
    ExternalDependencyAssertion hasVersion(@Nullable String expected) {
      assertEquals("fullRevision", expected, myTarget.getVersion());
      return this;
    }
  }
}
