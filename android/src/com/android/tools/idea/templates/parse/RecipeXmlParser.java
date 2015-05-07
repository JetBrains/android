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
package com.android.tools.idea.templates.parse;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.*;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.templates.Template.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateUtils.*;
import static com.android.tools.idea.templates.parse.SaxUtils.getPath;

/**
 * Handles parsing a recipe.xml.ftl file. A recipe file specifies a bunch of file-related actions
 * to take after a template is processed, such as copying files over, merging them, or opening them
 * in the main editor.
 * <p/>
 * A recipe, when parsed, will additionally execute its instructions, unless you call
 * {@link #setDryRun(boolean)} and set it to {@code true}, first.
 */
// TODO: This class is doing too much. Create classes for each schema element and delegate?
// TODO: dry-run is a short term solution. We should separate parsing from recipe execution
public final class RecipeXmlParser extends DefaultHandler {
  private static final Logger LOG = Logger.getInstance(RecipeXmlParser.class);
  /**
   * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
   */
  private static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  private static final String RECIPE_TAG = "recipe";
  private static final String COPY_TAG = "copy";
  private static final String COPY_ATTR_FROM = "from";
  private static final String COPY_ATTR_TO = "to";
  private static final String INSTANTIATE_TAG = "instantiate";
  private static final String DEPENDENCY_TAG = "dependency";
  private static final String DEPENDENCY_ATTR_MAVEN = "mavenUrl";
  private static final String MERGE_TAG = "merge";
  private static final String MERGE_ATTR_FROM = "from";
  private static final String MERGE_ATTR_TO = "to";
  private static final String MKDIR_TAG = "mkdir";
  private static final String MKDIR_ATTR_AT = "at";
  private static final String OPEN_TAG = "open";
  private static final String OPEN_ATTR_FILE = "file";

  @NotNull private Project myProject;
  @NotNull private PrefixTemplateLoader myLoader;
  @NotNull private Configuration myFreemarker;
  @NotNull private Map<String, Object> myParamMap;
  @NotNull private File myTemplateRoot;
  @NotNull private File myOutputRoot;
  @NotNull private File myModuleRoot;

  @NotNull private List<File> myFilesToCopy = Lists.newArrayList();
  @NotNull private List<File> myFilesToMerge = Lists.newArrayList();
  @NotNull private List<File> myFilesToOpen = Lists.newArrayList();

  private boolean myIsDryRun;
  private boolean myNeedsGradleSync;
  private boolean mySyncGradleIfNeeded;

  public RecipeXmlParser(@NotNull Project project,
                         @NotNull PrefixTemplateLoader templateLoader,
                         @NotNull Configuration freemarker,
                         @NotNull Map<String, Object> paramMap,
                         @NotNull File templateRoot,
                         @NotNull File outputRoot,
                         @NotNull File moduleRoot,
                         boolean gradleSyncIfNeeded) {
    myProject = project;
    myLoader = templateLoader;
    myTemplateRoot = templateRoot;
    myOutputRoot = outputRoot;
    myFreemarker = freemarker;
    myParamMap = paramMap;
    myModuleRoot = moduleRoot;
    mySyncGradleIfNeeded = gradleSyncIfNeeded;
  }

  /**
   * If {@code true}, parse this recipe file but do not execute its instructions. Defaults to
   * {@code false}
   */
  public void setDryRun(boolean isDryRun) {
    myIsDryRun = isDryRun;
  }

  /**
   * Return a list of all dependencies which this recipe wants to add.
   */
  public List<String> getDependencies() {
    return (List<String>)myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
  }

  /**
   * Return a list of all target files which this recipe wants to create / overwrite.
   */
  public List<File> getFilesToCopy() {
    return myFilesToCopy;
  }

  /**
   * Return a list of all target files which this recipe wants to create / modify.
   */
  public List<File> getFilesToMerge() {
    return myFilesToMerge;
  }

  /**
   * Returns the list of all files requested by the recipe file for opening in the editor.
   */
  public List<File> getFilesToOpen() {
    return myFilesToOpen;
  }

  @Override
  public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
    try {

      if (COPY_TAG.equals(name) || INSTANTIATE_TAG.equals(name)) {
        boolean instantiate = INSTANTIATE_TAG.equals(name);

        File fromFile = getPath(attributes, COPY_ATTR_FROM);
        File toFile = getPath(attributes, COPY_ATTR_TO);
        if (toFile == null || toFile.getPath().isEmpty()) {
          toFile = getPath(attributes, COPY_ATTR_FROM);
          toFile = TemplateUtils.stripSuffix(toFile, DOT_FTL);
        }

        myFilesToCopy.add(toFile);
        if (!myIsDryRun) {
          if (instantiate) {
            instantiate(myFreemarker, myParamMap, fromFile, toFile);
          }
          else {
            copyTemplateResource(fromFile, toFile);
          }
        }
      }
      else if (MERGE_TAG.equals(name)) {
        File fromFile = getPath(attributes, MERGE_ATTR_FROM);
        File toFile = getPath(attributes, MERGE_ATTR_TO);
        if (toFile == null || toFile.getPath().isEmpty()) {
          toFile = getPath(attributes, MERGE_ATTR_FROM);
          toFile = TemplateUtils.stripSuffix(toFile, DOT_FTL);
        }

        myFilesToMerge.add(toFile);
        if (!myIsDryRun) {
          // Resources in template.xml are located within root/
          merge(myFreemarker, myParamMap, fromFile, toFile);
        }
      }
      else if (name.equals(OPEN_TAG)) {
        // The relative path here is within the output directory:
        File relativePath = getPath(attributes, OPEN_ATTR_FILE);
        if (relativePath != null && !relativePath.getPath().isEmpty()) {
          myFilesToOpen.add(relativePath);
        }
      }
      else if (name.equals(MKDIR_TAG)) {
        // The relative path here is within the output directory:
        File relativePath = getPath(attributes, MKDIR_ATTR_AT);
        if (relativePath != null && !relativePath.getPath().isEmpty()) {
          File targetFile = getTargetFile(relativePath);
          checkedCreateDirectoryIfMissing(targetFile);
        }
      }
      else if (name.equals(DEPENDENCY_TAG)) {
        String url = attributes.getValue(DEPENDENCY_ATTR_MAVEN);
        //noinspection unchecked
        List<String> dependencyList = (List<String>)myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);

        if (url != null) {
          dependencyList.add(url);
        }
      }
      else if (!name.equals(RECIPE_TAG)) {
        LOG.warn("WARNING: Unknown recipe directive " + name);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void endElement(String uri, String localName, String name) throws SAXException {
    // Post-process this recipe once we close the root tag
    if (RECIPE_TAG.equals(name)) {
      if (myIsDryRun) {
        return;
      }

      // Handle dependencies
      if (myParamMap.containsKey(TemplateMetadata.ATTR_DEPENDENCIES_LIST)) {
        Object maybeDependencyList = myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
        if (maybeDependencyList instanceof List) {
          //noinspection unchecked
          List<String> dependencyList = (List<String>)maybeDependencyList;
          if (!dependencyList.isEmpty()) {
            try {
              mergeDependenciesIntoFile(GradleUtil.getGradleBuildFilePath(myModuleRoot));
              myNeedsGradleSync = true;
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
      if (myNeedsGradleSync && mySyncGradleIfNeeded && !myProject.isDefault() && isBuildWithGradle(myProject)) {
        GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
      }
    }
  }

  private void merge(@NotNull final Configuration freemarker,
                     @NotNull final Map<String, Object> paramMap,
                     @NotNull File relativeFrom,
                     @NotNull File to) throws IOException, TemplateException {

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
      boolean instantiate = hasExtension(relativeFrom, DOT_FTL);
      if (instantiate) {
        instantiate(freemarker, paramMap, relativeFrom, to);
      }
      else {
        copyTemplateResource(relativeFrom, to);
      }
      return;
    }

    String sourceText;
    File from = getFullPath(relativeFrom);
    if (hasExtension(relativeFrom, DOT_FTL)) {
      // Perform template substitution of the template prior to merging
      myLoader.setTemplateFile(from);
      sourceText = processFreemarkerTemplate(freemarker, paramMap, from.getName());
    }
    else {
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
      contents = GradleFileMerger.mergeGradleFiles(sourceText, targetText, myProject);
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

  /**
   * Merge the given dependency URLs into the given build.gradle file
   *
   * @param paramMap        the parameters to merge
   * @param gradleBuildFile the build.gradle file which will be written with the merged dependencies
   */
  private void mergeDependenciesIntoFile(@NotNull File gradleBuildFile) throws IOException, TemplateException {
    String templateRoot = TemplateManager.getTemplateRootFolder().getPath();
    File gradleTemplate = new File(templateRoot, FileUtil.join("gradle", "utils", "dependencies.gradle.ftl"));
    myLoader.setTemplateFile(gradleTemplate);
    String contents = processFreemarkerTemplate(myFreemarker, myParamMap, gradleTemplate.getName());
    String destinationContents = null;
    if (gradleBuildFile.exists()) {
      destinationContents = TemplateUtils.readTextFile(gradleBuildFile);
    }
    if (destinationContents == null) {
      destinationContents = "";
    }
    String result = GradleFileMerger.mergeGradleFiles(contents, destinationContents, myProject);
    writeFile(this, result, gradleBuildFile);
  }

  /**
   * Instantiates the given template file into the given output file
   */
  private void instantiate(@NotNull final Configuration freemarker,
                           @NotNull final Map<String, Object> paramMap,
                           @NotNull File relativeFrom,
                           @NotNull File to) throws IOException, TemplateException {
    // For now, treat extension-less files as directories... this isn't quite right
    // so I should refine this! Maybe with a unique attribute in the template file?
    boolean isDirectory = relativeFrom.getName().indexOf('.') == -1;
    if (isDirectory) {
      // It's a directory
      copyTemplateResource(relativeFrom, to);
    }
    else {
      File from = getFullPath(relativeFrom);
      myLoader.setTemplateFile(from);
      String contents = processFreemarkerTemplate(freemarker, paramMap, from.getName());

      contents = format(contents, to);
      File targetFile = getTargetFile(to);
      VfsUtil.createDirectories(targetFile.getParentFile().getAbsolutePath());
      writeFile(this, contents, targetFile);
    }
  }

  private String format(@NotNull String contents, File to) {
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(to.getName());
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(to.getName(), type, StringUtil.convertLineSeparators(contents));
    CodeStyleManager.getInstance(myProject).reformat(file);
    return file.getText();
  }

  private void copyTemplateResource(@NotNull File relativeFrom, @NotNull File output) throws IOException {
    copy(getFullPath(relativeFrom), getTargetFile(output));
  }

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  private void copy(@NotNull File src, @NotNull File dest) throws IOException {
    VirtualFile sourceFile = VfsUtil.findFileByIoFile(src, true);
    assert sourceFile != null : src;
    sourceFile.refresh(false, false);
    File destPath = (src.isDirectory() ? dest : dest.getParentFile());
    VirtualFile destFolder = checkedCreateDirectoryIfMissing(destPath);
    if (src.isDirectory()) {
      copyDirectory(sourceFile, destFolder);
    }
    else {
      com.intellij.openapi.editor.Document document = FileDocumentManager.getInstance().getDocument(sourceFile);
      if (document != null) {
        writeFile(this, document.getText(), dest);
      }
      else {
        VfsUtilCore.copyFile(this, sourceFile, destFolder, dest.getName());
      }
    }
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
      VfsUtilCore.copyFile(this, file, targetDir);
    }
    return true;
  }

  @NotNull
  private File getTargetFile(@NotNull File file) throws IOException {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myOutputRoot, file.getPath());
  }

  @NotNull
  private File getFullPath(@NotNull File fromFile) {
    if (fromFile.isAbsolute()) {
      return fromFile;
    }
    else {
      // If it's a relative file path, get the data from the template data directory
      return new File(myTemplateRoot, fromFile.getPath());
    }
  }
}
