/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Atomics;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public abstract class AndroidGradleImportTestCase extends AndroidGradleTestCase {
  protected static final String LIB_DIR_NAME = "lib";
  protected static final String LIBS_DEPENDENCY = "compile fileTree(include: ['*.jar'], dir: '" + LIB_DIR_NAME + "')";
  protected static final String ARCHIVE_NAME = "library";
  protected static final String ARCHIVE_JAR_NAME = ARCHIVE_NAME + ".jar";
  protected static final String ARCHIVE_DEFAULT_GRADLE_PATH = ":library";
  protected static final String SOURCE_MODULE_NAME = "sourcemodule";
  protected static final String SOURCE_MODULE_GRADLE_PATH = SdkConstants.GRADLE_PATH_SEPARATOR + SOURCE_MODULE_NAME;
  protected static final String PARENT_MODULE_NAME = "parent";
  protected static final String PARENT_MODULE_GRADLE_PATH = SdkConstants.GRADLE_PATH_SEPARATOR + PARENT_MODULE_NAME;
  protected static final String TOP_LEVEL_BUILD_GRADLE = "buildscript {\n" +
                                                         "    repositories {\n" +
                                                         GradleImport.MAVEN_REPOSITORY +
                                                         "    }\n" +
                                                         "    dependencies {\n" +
                                                         "        classpath 'com.android.tools.build:gradle:" +
                                                         SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION + "'\n" +
                                                         "    }\n" +
                                                         "}\n" +
                                                         "\n" +
                                                         "allprojects {\n" +
                                                         "    repositories {\n" +
                                                         GradleImport.MAVEN_REPOSITORY +
                                                         "    }\n" +
                                                         "}\n";
  protected static final String BUILD_GRADLE_TEMPLATE = "apply plugin: 'java'\n\n" +
                                                        "dependencies {\n" +
                                                        "    compile 'com.android.support:support-v4:23.4.+'\n" +
                                                        "    %s\n" +
                                                        "}\n\n";
  @Nullable private File myWorkingDir;
  @Nullable private File myJarNotInProject;

  /**
   * We need real Jar contents as this test will actually run Gradle that will peek inside the archive.
   */
  @NotNull
  protected static byte[] createRealJarArchive() throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jar = new JarOutputStream(buffer, manifest)) {
      jar.putNextEntry(new JarEntry("/dummy.txt"));
      jar.write(TOP_LEVEL_BUILD_GRADLE.getBytes(Charset.defaultCharset()));
    }
    return buffer.toByteArray();
  }

  @NotNull
  protected File getWorkingDir() {
    assertThat(myWorkingDir).isNotNull();
    return myWorkingDir;
  }

  @NotNull
  protected File getJarNotInProject() {
    assertThat(myJarNotInProject).isNotNull();
    return myJarNotInProject;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDir = Files.createTempDir();
    myJarNotInProject = new File(getWorkingDir(), ARCHIVE_JAR_NAME);
    Files.write(createRealJarArchive(), myJarNotInProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myWorkingDir != null && myWorkingDir.isDirectory()) {
        ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Boolean, IOException>() {
          @Override
          public Boolean compute() throws IOException {
            VirtualFile vFile = VfsUtil.findFileByIoFile(myWorkingDir, true);
            if (vFile != null) {
              vFile.delete(this);
            }
            return true;
          }
        });
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  @NotNull
  protected File createArchiveInModuleWithinCurrentProject(boolean nested, String sourceModuleBuildGradleBody) {
    File newArchiveFile = new CreateGradleModuleWithinProjectAction(
      getProject(),
      nested,
      sourceModuleBuildGradleBody).execute().getResultObject();
    assertThat(newArchiveFile).isNotNull();
    return newArchiveFile;
  }

  private static class CreateGradleModuleWithinProjectAction extends WriteCommandAction<File> {
    private final Project myProject;
    private final boolean myIsNested;
    @NotNull private final String mySourceModuleBuildGradleBody;

    CreateGradleModuleWithinProjectAction(Project project, boolean nested, @NotNull String sourceModuleBuildGradleBody) {
      super(project);
      myProject = project;
      mySourceModuleBuildGradleBody = sourceModuleBuildGradleBody;
      myIsNested = nested;
    }

    private static VirtualFile createGradleBuildFile(VirtualFile directory, String contents) throws IOException {
      VirtualFile archive = directory.createChildData(Integer.valueOf(0), SdkConstants.FN_BUILD_GRADLE);
      VfsUtil.saveText(archive, contents);
      return archive;
    }

    @Override
    protected void run(@NotNull Result<File> result) throws Throwable {
      String moduleDirectoryName = SOURCE_MODULE_NAME + "/" + LIB_DIR_NAME;
      if (myIsNested) {
        moduleDirectoryName = PARENT_MODULE_NAME + "/" + moduleDirectoryName;
      }
      File path = new File(virtualToIoFile(myProject.getBaseDir()), moduleDirectoryName);
      VirtualFile archiveDirectory = VfsUtil.createDirectories(path.getAbsolutePath());
      VirtualFile moduleDirectory = archiveDirectory.getParent();

      VirtualFile archive = archiveDirectory.createChildData(this, ARCHIVE_JAR_NAME);
      archive.setBinaryContent(createRealJarArchive());

      VirtualFile moduleBuildGradle = createGradleBuildFile(moduleDirectory, mySourceModuleBuildGradleBody);
      VirtualFile topBuildGradle = createGradleBuildFile(myProject.getBaseDir(), TOP_LEVEL_BUILD_GRADLE);
      VirtualFile parentBuildGradle =
        myIsNested ? createGradleBuildFile(moduleDirectory.getParent(), String.format(BUILD_GRADLE_TEMPLATE, ""))
                   : null;
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      GradleSettingsFile settingsGradle = GradleSettingsFile.getOrCreate(myProject);

      String gradlePath = myIsNested ? PARENT_MODULE_GRADLE_PATH + SOURCE_MODULE_GRADLE_PATH : SOURCE_MODULE_GRADLE_PATH;
      if (myIsNested) {
        settingsGradle.addModule(PARENT_MODULE_GRADLE_PATH, virtualToIoFile(moduleDirectory.getParent()));
      }
      settingsGradle.addModule(gradlePath, virtualToIoFile(moduleDirectory));

      AtomicReference<String> error = Atomics.newReference();
      AtomicBoolean done = new AtomicBoolean(false);
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, new GradleSyncListener.Adapter() {
        private void createFacet(String moduleName,
                                 String gradlePath,
                                 VirtualFile gradleFile) {
          Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleName);
          assertThat(module).isNotNull();
          FacetManager facetManager = FacetManager.getInstance(module);
          ModifiableFacetModel modifiableModel = facetManager.createModifiableModel();
          GradleFacet facet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
          GradleProject gradleProject = new GradleProjectStub(myProject.getName(),
                                                                    gradlePath,
                                                                    virtualToIoFile(topBuildGradle),
                                                                    "compile");
          GradleModuleModel gradleModuleModel = new GradleModuleModel(moduleName, gradleProject, virtualToIoFile(gradleFile), "2.2.1");
          facet.setGradleModuleModel(gradleModuleModel);
          modifiableModel.addFacet(facet);
          modifiableModel.commit();
          assertThat(Projects.isBuildWithGradle(module)).isTrue();
        }

        @Override
        public void syncSucceeded(@NotNull Project project) {
          createFacet(SOURCE_MODULE_NAME, gradlePath, moduleBuildGradle);
          if (myIsNested) {
            assertThat(parentBuildGradle).isNotNull();
            createFacet(PARENT_MODULE_NAME, PARENT_MODULE_GRADLE_PATH, parentBuildGradle);
          }
          done.set(true);
        }

        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          error.set(errorMessage);
        }
      });
      if (error.get() != null) {
        throw new IllegalStateException(error.get());
      }
      if (!done.get()) {
        throw new IllegalStateException("Sync should've been complete by now");
      }
      result.setResult(virtualToIoFile(archive));
    }

  }
}
