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
package com.android.tools.idea.templates.recipe;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateUtils.*;

/**
 * Context for a recipe that contains and accumulates state while executing its instructions and
 * modifying the project.
 */
public final class RecipeContext {

  private static final Logger LOG = Logger.getInstance(RecipeContext.class);

  /**
   * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
   */
  private static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  @NotNull private final Project myProject;
  @NotNull private final StudioTemplateLoader myLoader;
  @NotNull private final Configuration myFreemarker;
  @NotNull private final Map<String, Object> myParamMap;
  @NotNull private final File myOutputRoot;
  @NotNull private final File myModuleRoot;
  private final boolean mySyncGradleIfNeeded; // User can disable gradle syncing if they know they're going to sync themselves anyway

  private boolean myNeedsGradleSync;

  public RecipeContext(@NotNull Project project,
                       @NotNull StudioTemplateLoader loader,
                       @NotNull Configuration freemarker,
                       @NotNull Map<String, Object> paramMap,
                       @NotNull File outputRoot,
                       @NotNull File moduleRoot,
                       boolean syncGradleIfNeeded) {
    myProject = project;
    myLoader = loader;
    myFreemarker = freemarker;
    myParamMap = paramMap;
    myOutputRoot = outputRoot;
    myModuleRoot = moduleRoot;
    mySyncGradleIfNeeded = syncGradleIfNeeded;
  }

  public RecipeContext(@NotNull Module module,
                       @NotNull StudioTemplateLoader loader,
                       @NotNull Configuration freemarker,
                       @NotNull Map<String, Object> paramMap,
                       boolean syncGradleIfNeeded) {
    File moduleRoot = new File(module.getModuleFilePath()).getParentFile();

    myProject = module.getProject();
    myLoader = loader;
    myFreemarker = freemarker;
    myParamMap = paramMap;
    myOutputRoot = moduleRoot;
    myModuleRoot = moduleRoot;
    mySyncGradleIfNeeded = syncGradleIfNeeded;
  }

  /**
   * Add a library dependency into the project.
   */
  public void addDependency(@NotNull String mavenUrl) {
    //noinspection unchecked
    List<String> dependencyList = (List<String>)myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
    dependencyList.add(mavenUrl);
  }

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  public void copy(@NotNull File from, @NotNull File to) {
    try {
      copyTemplateResource(from, to);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Instantiates the given template file into the given output file (running the freemarker
   * engine over it)
   */
  public void instantiate(@NotNull File from, @NotNull File to) {
    try {
      // For now, treat extension-less files as directories... this isn't quite right
      // so I should refine this! Maybe with a unique attribute in the template file?
      boolean isDirectory = from.getName().indexOf('.') == -1;
      if (isDirectory) {
        // It's a directory
        copyTemplateResource(from, to);
      }
      else {
        String contents = processFreemarkerTemplate(myFreemarker, myParamMap, from, null);

        contents = format(contents, to);
        File targetFile = getTargetFile(to);
        VfsUtil.createDirectories(targetFile.getParentFile().getAbsolutePath());
        writeFile(this, contents, targetFile);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (TemplateProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   * <p/>
   * Only XML and Gradle files are currently supported.
   */
  public void merge(@NotNull File from, @NotNull File to) {
    try {
      String targetText = null;

      to = getTargetFile(to);
      if (!(hasExtension(to, DOT_XML) || hasExtension(to, DOT_GRADLE))) {
        throw new RuntimeException("Only XML or Gradle files can be merged at this point: " + to);
      }

      if (to.exists()) {
        targetText = Files.toString(to, Charsets.UTF_8);
      }
      else if (to.getParentFile() != null) {
        //noinspection ResultOfMethodCallIgnored
        checkedCreateDirectoryIfMissing(to.getParentFile());
      }

      if (targetText == null) {
        // The target file doesn't exist: don't merge, just copy
        boolean instantiate = hasExtension(from, DOT_FTL);
        if (instantiate) {
          instantiate(from, to);
        }
        else {
          copyTemplateResource(from, to);
        }
        return;
      }

      String sourceText;
      if (hasExtension(from, DOT_FTL)) {
        // Perform template substitution of the template prior to merging
        sourceText = processFreemarkerTemplate(myFreemarker, myParamMap, from, null);
      }
      else {
        from = myLoader.getSourceFile(from);
        sourceText = readTextFile(from);
        if (sourceText == null) {
          return;
        }
      }

      String contents;
      if (to.getName().equals(GRADLE_PROJECT_SETTINGS_FILE)) {
        contents = RecipeMergeUtils.mergeGradleSettingsFile(sourceText, targetText);
        myNeedsGradleSync = true;
      }
      else if (to.getName().equals(SdkConstants.FN_BUILD_GRADLE)) {
        String compileSdkVersion = (String)myParamMap.get(TemplateMetadata.ATTR_BUILD_API_STRING);
        contents = GradleFileMerger.mergeGradleFiles(sourceText, targetText, myProject, compileSdkVersion);
        myNeedsGradleSync = true;
      }
      else if (hasExtension(to, DOT_XML)) {
        contents = RecipeMergeUtils.mergeXml(myProject, sourceText, targetText, to);
      }
      else {
        throw new RuntimeException("Only XML or Gradle settings files can be merged at this point: " + to);
      }

      writeFile(this, contents, to);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (TemplateException e) {
      throw new RuntimeException(e);
    }
    catch (TemplateProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  public void mkDir(@NotNull File at) {
    try {
      checkedCreateDirectoryIfMissing(getTargetFile(at));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Open the target file in the editor.
   */
  public void open(@NotNull File file) {
    // Do nothing - it is up to an external class to query this recipe for files it should open
  }

  /**
   * Execute another recipe file.
   */
  public void execute(@NotNull File file) {
    try {
      processFreemarkerTemplate(myFreemarker, myParamMap, file, new FreemarkerUtils.TemplatePostProcessor() {
        @Override
        public void process(@NotNull String xml) throws TemplateProcessingException {
          try {
            xml = XmlUtils.stripBom(xml);

            Recipe recipe = Recipe.parse(new StringReader(xml));
            recipe.execute(RecipeContext.this);
          }
          catch (JAXBException ex) {
            throw new TemplateProcessingException(ex);
          }
        }
      });
    }
    catch (TemplateProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  public void updateAndSyncGradle() {
    // Handle dependencies
    if (myParamMap.containsKey(TemplateMetadata.ATTR_DEPENDENCIES_LIST)) {
      Object maybeDependencyList = myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
      if (maybeDependencyList instanceof List) {
        //noinspection unchecked
        List<String> dependencyList = (List<String>)maybeDependencyList;
        if (!dependencyList.isEmpty()) {
          try {
            mergeDependenciesIntoGradle();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    if (myNeedsGradleSync &&
        mySyncGradleIfNeeded &&
        !myProject.isDefault() &&
        isBuildWithGradle(myProject)) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  @NotNull
  public File getTargetFile(@NotNull File file) throws IOException {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myOutputRoot, file.getPath());
  }

  /**
   * Merge the URLs from our gradle template into the target module's build.gradle file
   */
  private void mergeDependenciesIntoGradle() throws Exception {
    File gradleBuildFile = GradleUtil.getGradleBuildFilePath(myModuleRoot);
    String templateRoot = TemplateManager.getTemplateRootFolder().getPath();
    File gradleTemplate = new File(templateRoot, FileUtil.join("gradle", "utils", "dependencies.gradle.ftl"));
    String contents = processFreemarkerTemplate(myFreemarker, myParamMap, gradleTemplate, null);
    String destinationContents = null;
    if (gradleBuildFile.exists()) {
      destinationContents = readTextFile(gradleBuildFile);
    }
    if (destinationContents == null) {
      destinationContents = "";
    }
    String compileSdkVersion = (String)myParamMap.get(TemplateMetadata.ATTR_BUILD_API_STRING);
    String result = GradleFileMerger.mergeGradleFiles(contents, destinationContents, myProject, compileSdkVersion);
    writeFile(this, result, gradleBuildFile);
    myNeedsGradleSync = true;
  }

  /**
   * VfsUtil#copyDirectory messes up the undo stack, most likely by trying to
   * create a directory even if it already exists. This is an undo-friendly
   * replacement.
   */
  private void copyDirectory(@NotNull final VirtualFile src, @NotNull final VirtualFile dest) throws IOException {
    final File destinationFile = VfsUtilCore.virtualToIoFile(dest);
    VfsUtilCore.visitChildrenRecursively(src, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        try {
          return copyFile(file, src, destinationFile, dest);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }
    }, IOException.class);
  }

  private String format(@NotNull String contents, File to) {
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(to.getName());
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(to.getName(), type, StringUtil.convertLineSeparators(contents));
    CodeStyleManager.getInstance(myProject).reformat(file);
    return file.getText();
  }

  private void copyTemplateResource(@NotNull File from, @NotNull File to) throws IOException {
    from = myLoader.getSourceFile(from);
    to = getTargetFile(to);

    VirtualFile sourceFile = VfsUtil.findFileByIoFile(from, true);
    assert sourceFile != null : from;
    sourceFile.refresh(false, false);
    File destPath = (from.isDirectory() ? to : to.getParentFile());
    VirtualFile destFolder = checkedCreateDirectoryIfMissing(destPath);
    if (from.isDirectory()) {
      copyDirectory(sourceFile, destFolder);
    }
    else {
      Document document = FileDocumentManager.getInstance().getDocument(sourceFile);
      if (document != null) {
        writeFile(this, document.getText(), to);
      }
      else {
        VfsUtilCore.copyFile(this, sourceFile, destFolder, to.getName());
      }
    }
  }

  private boolean copyFile(VirtualFile file, VirtualFile src, File destinationFile, VirtualFile dest) throws IOException {
    String relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar);
    if (relativePath == null) {
      LOG.error(file.getPath() + " is not a child of " + src, new Exception());
      return false;
    }
    if (file.isDirectory()) {
      checkedCreateDirectoryIfMissing(new File(destinationFile, relativePath));
    }
    else {
      VirtualFile targetDir = dest;
      if (relativePath.indexOf(File.separatorChar) > 0) {
        String directories = relativePath.substring(0, relativePath.lastIndexOf(File.separatorChar));
        File newParent = new File(destinationFile, directories);
        targetDir = checkedCreateDirectoryIfMissing(newParent);
      }
      File target = new File(destinationFile, relativePath);
      if (target.exists()) {
        LOG.warn("Target file already exists, skipping the copy of: " + target.getPath());
      }
      else {
        VfsUtilCore.copyFile(this, file, targetDir);
      }
    }
    return true;
  }
}
