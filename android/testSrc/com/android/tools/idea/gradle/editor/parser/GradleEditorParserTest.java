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
package com.android.tools.idea.gradle.editor.parser;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.editor.entity.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Processor;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData.*;
import static com.android.tools.idea.gradle.editor.parser.GradleEditorParserTestUtil.externalDependency;
import static com.android.tools.idea.gradle.editor.parser.GradleEditorParserTestUtil.property;

public class GradleEditorParserTest extends LightPlatformCodeInsightFixtureTestCase {

  public static final String GRADLE_EDITOR_TEST_DATA_ROOT = "editor";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testSingleFile_hardCodedProperties() throws Exception {
    prepare("singleFileHardCodedProperties");
    List<GradleEditorEntityGroup> parsed = parse("build.gradle");

    VersionGradleEditorEntity pluginVersion =
      findEntity("android.gradle.editor.version.gradle.plugin", VersionGradleEditorEntity.class, parsed);
    property("android gradle plugin").value("1.0.0").entityText("classpath 'com.android.tools.build:gradle:1.0.0'")
      .declarationValue("1.0.0").metaData(OUTGOING).check(pluginVersion);

    VersionGradleEditorEntity compileSdk = findEntity("android.gradle.editor.version.sdk.compile", VersionGradleEditorEntity.class, parsed);
    property("compile sdk version").value("20").entityText("compileSdkVersion 20").declarationValue("20").check(compileSdk);

    VersionGradleEditorEntity buildTools = findEntity("android.gradle.editor.version.build.tools", VersionGradleEditorEntity.class, parsed);
    property("build tools version").value("20.0.0").entityText("buildToolsVersion '20.0.0'").declarationValue("20.0.0").check(buildTools);

    checkDependencies(parsed, externalDependency().scope("compile").group("group1").artifact("artifact1").version("version1")
                        .entityText("compile 'group1:artifact1:version1'").versionDeclarationValue("version1").metaData(REMOVABLE),
                      externalDependency().scope("test").group("group2").artifact("artifact2").version("version2")
                        .entityText("test 'group2:artifact2:version2'").versionDeclarationValue("version2").metaData(REMOVABLE));
  }

  public void testSingleFile_referencedProperties() throws IOException {
    prepare("singleFileReferencedProperties");
    List<GradleEditorEntityGroup> parsed = parse("build.gradle");

    VersionGradleEditorEntity pluginVersion =
      findEntity("android.gradle.editor.version.gradle.plugin", VersionGradleEditorEntity.class, parsed);
    property("android gradle plugin").value("1.0.0").entityText("classpath \"com.android.tools.build:gradle:$GRADLE_PLUGIN_VERSION\"")
      .declarationValue("$GRADLE_PLUGIN_VERSION").metaData(OUTGOING).check(pluginVersion);

    VersionGradleEditorEntity compileSdk = findEntity("android.gradle.editor.version.sdk.compile", VersionGradleEditorEntity.class, parsed);
    property("compile sdk version").value("").value("").definitionValueBindings("20", "21").entityText("compileSdkVersion COMPILE_SDK_VERSION")
      .declarationValue("COMPILE_SDK_VERSION").check(compileSdk);

    VersionGradleEditorEntity buildTools = findEntity("android.gradle.editor.version.build.tools", VersionGradleEditorEntity.class, parsed);
    property("build tools version").value("").definitionValueBindings("'20.0.' + 0").entityText("buildToolsVersion BUILD_TOOLS_VERSION")
      .declarationValue("BUILD_TOOLS_VERSION").check(buildTools);

    checkDependencies(parsed,
                      externalDependency().scope("compile").group("g").artifact("a").version("").versionBinding("0 + 1")
                        .versionDeclarationValue("$VERSION0").entityText("compile \"g:a:$VERSION0\"").metaData(REMOVABLE),
                      externalDependency().scope("compile").group("group1").artifact("artifact1").version("1")
                        .versionDeclarationValue("$VERSION1").entityText("compile \"$GROUP1:$ARTIFACT1:$VERSION1\"").metaData(REMOVABLE),
                      externalDependency().scope("test").groupBinding("group2", "group22").artifactBinding("'group' + tmpLocalVar", "2")
                        .version("2").versionDeclarationValue("$VERSION2").entityText("test \"$GROUP2:$ARTIFACT2:$VERSION2\"")
                        .metaData(REMOVABLE),
                      externalDependency().scope("test").group("g1").artifact("a1").version("").versionBinding("1 + 2")
                        .versionDeclarationValue("${1 + 2}").entityText("test \"g1:a1:${1 + 2}\"").metaData(REMOVABLE));
  }

  public void testMultiProject_normal() throws IOException {
    prepare("multiProjectNormal");
    List<GradleEditorEntityGroup> subProjectParsed = parse("app/build.gradle");

    VersionGradleEditorEntity subProjectPluginVersion =
      findEntity("android.gradle.editor.version.gradle.plugin", VersionGradleEditorEntity.class, subProjectParsed);
    property("android gradle plugin").value("1.0.0").entityText("classpath 'com.android.tools.build:gradle:1.0.0'")
      .declarationValue("1.0.0").metaData(INJECTED).check(subProjectPluginVersion);

    VersionGradleEditorEntity subProjectCompileSdk = findEntity("android.gradle.editor.version.sdk.compile", VersionGradleEditorEntity.class, subProjectParsed);
    property("compile sdk version").value("20").entityText("compileSdkVersion COMPILE_SDK_VERSION")
      .declarationValue("COMPILE_SDK_VERSION").check(subProjectCompileSdk);

    checkDependencies(subProjectParsed,
                      externalDependency().scope("compile").group("group1").artifact("artifact1").version("version1")
                        .versionDeclarationValue("version1").entityText("compile 'group1:artifact1:version1'").metaData(REMOVABLE),
                      externalDependency().scope("unitTest").group("junit").artifact("junit").version("4.11")
                        .versionDeclarationValue("4.11").entityText("unitTest 'junit:junit:4.11'").metaData(INJECTED, REMOVABLE));

    checkRepositories(subProjectParsed,
                      property("jcenter repo").value(GradleEditorRepositoryEntity.JCENTER_URL).metaData(INJECTED, READ_ONLY));

    List<GradleEditorEntityGroup> baseProjectParsed = parse("build.gradle");
    VersionGradleEditorEntity baseProjectPluginVersion =
      findEntity("android.gradle.editor.version.gradle.plugin", VersionGradleEditorEntity.class, baseProjectParsed);
    property("android gradle plugin").value("1.0.0").entityText("classpath 'com.android.tools.build:gradle:1.0.0'")
      .declarationValue("1.0.0").metaData(OUTGOING).check(baseProjectPluginVersion);

    checkDependencies(baseProjectParsed,
                      externalDependency().scope("unitTest").group("junit").artifact("junit").version("4.11")
                        .versionDeclarationValue("4.11").entityText("unitTest 'junit:junit:4.11'").metaData(OUTGOING, REMOVABLE));

    checkRepositories(baseProjectParsed,
                      property("jcenter repo").value(GradleEditorRepositoryEntity.JCENTER_URL).metaData(READ_ONLY),
                      property("jcenter repo").value(GradleEditorRepositoryEntity.JCENTER_URL).metaData(READ_ONLY, OUTGOING),
                      property("maven central repo").value(GradleEditorRepositoryEntity.MAVEN_CENTRAL_URL).metaData(READ_ONLY),
                      property("custom maven repo").value("https://android-devtools-staging.corp.google.com/no_crawl/maven/full/")
                        .definitionValueBindings("https://android-devtools-staging.corp.google.com/no_crawl/maven/full/").metaData(READ_ONLY),
                      property("custom maven repo from env").value("").declarationValue("System.getenv(\"MAVEN_REPO\")")
                        .definitionValueBindings("System.getenv(\"MAVEN_REPO\")"),
                      property("custom maven repo from env via externalized variable name")
                        .declarationValue("System.getenv(\"$M2_ENV_VAR_NAME\")")
                        .definitionValueBindings("System.getenv(\"$M2_ENV_VAR_NAME\")", "M2_HOME"));
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static <T extends GradleEditorEntity> T findEntity(
    @NotNull @PropertyKey(resourceBundle = "messages.AndroidBundle") String nameKey,
    @NotNull Class<T> entityClass,
    @NotNull List<GradleEditorEntityGroup> groups)
  {
    String name = AndroidBundle.message(nameKey);
    for (GradleEditorEntityGroup group : groups) {
      for (GradleEditorEntity entity : group.getEntities()) {
        if (name.equals(entity.getName())) {
          if (entityClass.isInstance(entity)) {
            return (T)entity;
          }
          fail(String.format("Target entity with name '%s' is of unexpected class (%s). Expected it to be IS-A %s",
                             name, group.getClass().getName(), entityClass.getName()));
        }
      }
    }
    String message = String.format("Can't find an entity with name '%s' which IS-A %s", name, entityClass.getName());
    fail(message);
    throw new RuntimeException(message); // To make compiler happy
  }

  public void testRemovingEntity() throws IOException {
    prepare("singleFileHardCodedProperties");
    List<GradleEditorEntityGroup> parsed = parse("build.gradle");
    List<ExternalDependencyGradleEditorEntity> dependencies = getDependencies(parsed);
    doCheckDependencies(dependencies,
                        externalDependency().scope("compile").group("group1").artifact("artifact1").version("version1")
                          .entityText("compile 'group1:artifact1:version1'").versionDeclarationValue("version1").metaData(REMOVABLE),
                        externalDependency().scope("test").group("group2").artifact("artifact2").version("version2")
                          .entityText("test 'group2:artifact2:version2'").versionDeclarationValue("version2").metaData(REMOVABLE));

    for (ExternalDependencyGradleEditorEntity dependency : dependencies) {
      if ("compile".equals(dependency.getScope())) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          GradleEditorModelUtil.removeEntity(dependency, true);
        });
        break;
      }
    }
    parsed = parse("build.gradle");
    checkDependencies(parsed,
                      externalDependency().scope("test").group("group2").artifact("artifact2").version("version2")
                        .entityText("test 'group2:artifact2:version2'").versionDeclarationValue("version2").metaData(REMOVABLE));
  }

  private static void checkDependencies(@NotNull List<GradleEditorEntityGroup> groups, @NotNull ExternalDependencyChecker... checkers) {
    doCheckDependencies(getDependencies(groups), checkers);
  }

  @NotNull
  private static List<ExternalDependencyGradleEditorEntity> getDependencies(@NotNull List<GradleEditorEntityGroup> groups) {
    return getEntities(groups, "android.gradle.editor.header.dependencies", ExternalDependencyGradleEditorEntity.class);
  }

  private static void doCheckDependencies(@NotNull List<ExternalDependencyGradleEditorEntity> actualEntities,
                                          @NotNull ExternalDependencyChecker... checkers) {
    assertEquals("Parsed dependencies number mismatch", checkers.length, actualEntities.size());

    actualEntities = Lists.newArrayList(actualEntities);
    List<ExternalDependencyChecker> checkersBuffer = Lists.newArrayList(checkers);
    for (Iterator<ExternalDependencyGradleEditorEntity> entityIterator = actualEntities.iterator(); entityIterator.hasNext(); ) {
      ExternalDependencyGradleEditorEntity entity = entityIterator.next();
      for (Iterator<ExternalDependencyChecker> checkerIterator = checkersBuffer.iterator(); checkerIterator.hasNext(); ) {
        ExternalDependencyChecker checker = checkerIterator.next();
        if (checker.matches(entity) == null) {
          entityIterator.remove();
          checkerIterator.remove();
          break;
        }
      }
    }

    if (actualEntities.isEmpty()) {
      return;
    }

    fail(String.format("Mismatched dependencies (%d): expected but not matched: %s, unexpected dependencies: %s", actualEntities.size(),
                       StringUtil.join(checkersBuffer, ","), StringUtil.join(actualEntities, ",")));
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static <T> List<T> getEntities(@NotNull List<GradleEditorEntityGroup> groups,
                                         @NotNull @PropertyKey(resourceBundle = "messages.AndroidBundle") String groupNameKey,
                                         @NotNull Class<T> entityClass) {
    List<T> result = Lists.newArrayList();
    String groupName = AndroidBundle.message(groupNameKey);
    for (GradleEditorEntityGroup group : groups) {
      if (!groupName.equals(group.getName())) {
        continue;
      }
      for (GradleEditorEntity entity : group.getEntities()) {
        if (entityClass.isInstance(entity)) {
          result.add((T)entity);
        }
      }
    }
    return result;
  }

  private static <T extends AbstractSimpleGradleEditorEntity> void checkEntities(@NotNull List<T> actual,
                                                                                 @NotNull SimpleEntityChecker<T>... checkers) {
    assertEquals("Entities number mismatch", checkers.length, actual.size());

    actual = Lists.newArrayList(actual);
    List<SimpleEntityChecker<T>> checkersBuffer = Lists.newArrayList(checkers);
    for (Iterator<T> entityIterator = actual.iterator(); entityIterator.hasNext(); ) {
      T entity = entityIterator.next();
      for (Iterator<SimpleEntityChecker<T>> checkerIterator = checkersBuffer.iterator(); checkerIterator.hasNext(); ) {
        SimpleEntityChecker<T> checker = checkerIterator.next();
        if (checker.apply(entity) == null) {
          entityIterator.remove();
          checkerIterator.remove();
          break;
        }
      }
    }

    if (actual.isEmpty()) {
      return;
    }

    fail(String.format("Mismatched entities (%d): expected but not matched: %s, unexpected dependencies: %s", actual.size(),
                       StringUtil.join(checkersBuffer, ","), StringUtil.join(actual, ",")));
  }

  @SuppressWarnings("unchecked")
  private static void checkRepositories(@NotNull List<GradleEditorEntityGroup> groups,
                                        @NotNull SimpleEntityChecker<?>... expected) {
    List<GradleEditorRepositoryEntity> repositories =
      getEntities(groups, "android.gradle.editor.header.repositories", GradleEditorRepositoryEntity.class);
    checkEntities(repositories, (SimpleEntityChecker<GradleEditorRepositoryEntity>[])expected);
  }

  /**
   * Configures ide project to use with the target test data identified by the given argument.
   *
   * @param relativeGradleConfigPath  relative path to the project's test data root, i.e. all files under the given root
   *                                  will be copied under the project's root. Given path is relative to the
   *                                  '{@link AndroidTestBase#getTestDataPath() android test root}/{@value #GRADLE_EDITOR_TEST_DATA_ROOT}'
   * @throws IOException              in case of unexpected I/O exception occurred during files copying
   */
  private void prepare(@NotNull String relativeGradleConfigPath) throws IOException {
    File fromDir = new File(AndroidTestBase.getTestDataPath(),
                            FileUtil.join(GRADLE_EDITOR_TEST_DATA_ROOT, relativeGradleConfigPath.replace('/', File.separatorChar)));
    assertTrue(fromDir.getAbsolutePath(), fromDir.isDirectory());

    Project project = myFixture.getProject();
    VirtualFile vfsProjectRoot = project.getBaseDir();
    purgeGradleConfig(vfsProjectRoot);
    File projectRoot = VfsUtilCore.virtualToIoFile(vfsProjectRoot);
    FileUtil.copyDir(fromDir, projectRoot);
    vfsProjectRoot.refresh(false, true);
  }

  /**
   * Recursively removes all gradle config files from the given dir.
   *
   * @param rootDir  target root dir
   * @throws IOException  in case of unexpected I/O exception occurred during processing
   */
  private void purgeGradleConfig(@NotNull VirtualFile rootDir) throws IOException {
    final List<VirtualFile> toRemove = Lists.newArrayList();
    VfsUtil.processFileRecursivelyWithoutIgnored(rootDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        if (file.getName().endsWith(SdkConstants.DOT_GRADLE)) {
          toRemove.add(file);
        }
        return true;
      }
    });

    if (toRemove.isEmpty()) {
      return;
    }

    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (VirtualFile file : toRemove) {
      fileSystem.deleteFile(this, file);
    }
  }

  /**
   * Builds gradle editor model for the target gradle config file.
   *
   * @param relativeGradleConfigPath  relative path to the target gradle config file for which gradle editor model should be built.
   *                                  The path is relative to the current ide project's root
   * @return                          gradle editor model for the target gradle config file
   */
  @NotNull
  private List<GradleEditorEntityGroup> parse(@NotNull String relativeGradleConfigPath) {
    VirtualFile baseDir = myFixture.getProject().getBaseDir();
    String filePath = baseDir.getPath() + '/' + relativeGradleConfigPath;
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertNotNull(filePath, vFile);
    return new GradleEditorModelParserFacade().parse(vFile, myFixture.getProject());
  }
}
