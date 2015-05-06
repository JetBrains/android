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
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.*;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.lang.xml.XMLLanguage;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.*;
import com.intellij.util.SystemProperties;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.templates.Template.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateUtils.*;
import static com.android.tools.idea.templates.parse.SaxUtils.getPath;

/**
 * Handles parsing a recipe.xml.ftl file. A recipe file specifies a bunch of file-related actions
 * to take after a template is processed, such as copying files over, merging them, or opening them
 * in the main editor.
 */
// TODO: This class is doing too much. Create classes for each schema element and delegate?
public final class RecipeXmlParser extends DefaultHandler {
  private static final Logger LOG = Logger.getInstance(RecipeXmlParser.class);
  /**
   * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
   */
  private static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";
  /**
   * Finds include ':module_name_1', ':module_name_2',... statements in settings.gradle files
   */
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("(^|\\n)\\s*include +(':[^']+', *)*':[^']+'");

  private static final String RECIPE_TAG = "recipe";
  private static final String COPY_TAG = "copy";
  private static final String COPY_ATTR_FROM = "from";
  private static final String COPY_ATTR_TO = "to";
  private static final String INSTANTIATE_TAG = "instantiate";
  private static final String DEPENDENCY_TAG = "dependency";
  private static final String DEPENDENCY_ATTR_MAVEN = "mavenUrl";
  private static final String MERGE_TAG = "merge";
  private static final String MERGE_ATTR_STRATEGY = "templateMergeStrategy";
  private static final String MERGE_ATTR_STRATEGY_REPLACE = "replace";
  private static final String MERGE_ATTR_STRATEGY_PRESERVE = "preserve";
  private static final String MERGE_ATTR_FROM = "from";
  private static final String MERGE_ATTR_TO = "to";
  private static final String MKDIR_TAG = "mkdir";
  private static final String MKDIR_ATTR_AT = "at";
  private static final String OPEN_TAG = "open";
  private static final String OPEN_ATTR_FILE = "file";

  @NotNull private final Project myProject;
  @NotNull private final PrefixTemplateLoader myLoader;
  @NotNull private final Configuration myFreemarker;
  @NotNull private final File myTemplateRoot;
  @NotNull private final File myOutputRoot;
  @NotNull private final File myModuleRoot;

  @NotNull private final Map<String, Object> myParamMap;
  @NotNull private final List<File> myFilesToOpen = Lists.newArrayList();
  private boolean myNeedsGradleSync;
  private boolean mySyncGradleIfNeeded;

  public RecipeXmlParser(@NotNull Project project,
                         @NotNull PrefixTemplateLoader templateLoader,
                         @NotNull Configuration freemarker,
                         @NotNull Map<String, Object> paramMap,
                         @NotNull File templateRoot,
                         @NotNull File outputRoot,
                         @NotNull File moduleRoot, boolean gradleSyncIfNeeded)
  {
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
   * Merges the given manifest fragment into the given manifest file
   */
  @Nullable
  private static String mergeManifest(@NotNull File targetManifest, @NotNull String mergeText) {
    File tempFile = null;
    try {
      //noinspection SpellCheckingInspection
      tempFile = FileUtil.createTempFile("manifmerge", DOT_XML);
      FileUtil.writeToFile(tempFile, mergeText);
      StdLogger logger = new StdLogger(StdLogger.Level.INFO);
      ManifestMerger2.Invoker merger = ManifestMerger2.newMerger(targetManifest, logger, ManifestMerger2.MergeType.APPLICATION)
        .withFeatures(ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS, ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
        .addLibraryManifest(tempFile);
      MergingReport mergeReport = merger.merge();
      if (mergeReport.getMergedDocument().isPresent()) {
        return XmlPrettyPrinter
          .prettyPrint(mergeReport.getMergedDocument().get().getXml(), createXmlFormatPreferences(), XmlFormatStyle.MANIFEST, "\n",
                       mergeText.endsWith("\n"));
      }
      return null;
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ManifestMerger2.MergeFailureException e) {
      LOG.error(e);
      try {
        FileUtil.appendToFile(tempFile, String.format("<!--%s-->", e.getMessage()));
      }
      catch (IOException e1) {
        LOG.error(e1);
      }
    }
    finally {
      if (tempFile != null) {
        tempFile.delete();
      }
    }
    return null;
  }

  private static String mergeGradleSettingsFile(@NotNull String source, @NotNull String dest) throws IOException, TemplateException {
    // TODO: Right now this is implemented as a dumb text merge. It would be much better to read it into PSI using IJ's Groovy support.
    // If Gradle build files get first-class PSI support in the future, we will pick that up cheaply. At the moment, Our Gradle-Groovy
    // support requires a project, which we don't necessarily have when instantiating a template.

    StringBuilder contents = new StringBuilder(dest);

    for (String line : Splitter.on('\n').omitEmptyStrings().trimResults().split(source)) {
      if (!line.startsWith("include")) {
        throw new RuntimeException("When merging settings.gradle files, only include directives can be merged.");
      }
      line = line.substring("include".length()).trim();

      Matcher matcher = INCLUDE_PATTERN.matcher(contents);
      if (matcher.find()) {
        contents.insert(matcher.end(), ", " + line);
      }
      else {
        contents.insert(0, "include " + line + SystemProperties.getLineSeparator());
      }
    }
    return contents.toString();
  }

  @NotNull
  private static XmlFormatPreferences createXmlFormatPreferences() {
    // TODO: implement
    return XmlFormatPreferences.defaults();
  }

  private static String getResourceId(@NotNull XmlTag tag) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      name = tag.getAttributeValue(ATTR_ID);
    }

    return name;
  }

  /**
   * Wraps the given strings in the standard conflict syntax
   */
  private static String wrapWithMergeConflict(String original, String added) {
    String sep = "\n";
    return "<<<<<<< Original" + sep + original + sep + "=======" + sep + added + ">>>>>>> Added" + sep;
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

        if (instantiate) {
          instantiate(myFreemarker, myParamMap, fromFile, toFile);
        }
        else {
          copyTemplateResource(fromFile, toFile);
        }
      }
      else if (MERGE_TAG.equals(name)) {
        File fromFile = getPath(attributes, MERGE_ATTR_FROM);
        File toFile = getPath(attributes, MERGE_ATTR_TO);
        if (toFile == null || toFile.getPath().isEmpty()) {
          toFile = getPath(attributes, MERGE_ATTR_FROM);
          toFile = TemplateUtils.stripSuffix(toFile, DOT_FTL);
        }
        // Resources in template.xml are located within root/
        merge(myFreemarker, myParamMap, fromFile, toFile);
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
      contents = mergeGradleSettingsFile(sourceText, targetText);
      myNeedsGradleSync = true;
    }
    else if (to.getName().equals(SdkConstants.FN_BUILD_GRADLE)) {
      contents = GradleFileMerger.mergeGradleFiles(sourceText, targetText, myProject);
      myNeedsGradleSync = true;
    }
    else if (hasExtension(to, DOT_XML)) {
      contents = mergeXml(sourceText, targetText, to);
    }
    else {
      throw new RuntimeException("Only XML or Gradle settings files can be merged at this point: " + to);
    }

    writeFile(this, contents, to);
  }

  /**
   * Merges sourceXml into targetXml/targetFile (targetXml is the contents of targetFile).
   * Returns the resulting xml if it still needs to be written to targetFile,
   * or null if the file has already been/doesn't need to be updated.
   */
  @Nullable
  private String mergeXml(String sourceXml, String targetXml, File targetFile) {
    boolean ok;
    String fileName = targetFile.getName();
    String contents;
    if (fileName.equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
      Document currentDocument = XmlUtils.parseDocumentSilently(targetXml, true);
      assert currentDocument != null : targetXml + " failed to parse";
      Document fragment = XmlUtils.parseDocumentSilently(sourceXml, true);
      assert fragment != null : sourceXml + " failed to parse";
      contents = mergeManifest(targetFile, sourceXml);
      ok = contents != null;
    }
    else {
      // Merge plain XML files
      String parentFolderName = targetFile.getParentFile().getName();
      ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFolderName);
      // mergeResourceFile handles the file updates itself, so no content is returned in this case.
      contents = mergeResourceFile(targetXml, sourceXml, folderType);
      ok = contents != null;
    }

    // Finally write out the merged file
    if (!ok) {
      // Just insert into file along with comment, using the "standard" conflict
      // syntax that many tools and editors recognize.

      contents = wrapWithMergeConflict(targetXml, sourceXml);
    }
    return contents;
  }

  /**
   * Merges the given resource file contents into the given resource file
   */
  private String mergeResourceFile(@NotNull String targetXml, @NotNull String sourceXml, @Nullable ResourceFolderType folderType) {
    XmlFile targetPsiFile = (XmlFile)PsiFileFactory.getInstance(myProject)
      .createFileFromText("targetFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(targetXml));
    XmlFile sourcePsiFile = (XmlFile)PsiFileFactory.getInstance(myProject)
      .createFileFromText("sourceFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(sourceXml));
    XmlTag root = targetPsiFile.getDocument().getRootTag();
    assert root != null : "Cannot find XML root in target: " + targetXml;

    XmlAttribute[] attributes = sourcePsiFile.getRootTag().getAttributes();
    for (XmlAttribute attr : attributes) {
      if (attr.getNamespacePrefix().equals(XMLNS_PREFIX)) {
        root.setAttribute(attr.getName(), attr.getValue());
      }
    }

    List<XmlTagChild> prependElements = Lists.newArrayList();
    XmlText indent = null;
    if (folderType == ResourceFolderType.VALUES) {
      // Try to merge items of the same name
      Map<String, XmlTag> old = Maps.newHashMap();
      for (XmlTag newSibling : root.getSubTags()) {
        old.put(getResourceId(newSibling), newSibling);
      }
      for (PsiElement child : sourcePsiFile.getRootTag().getChildren()) {
        if (child instanceof XmlComment) {
          if (indent != null) {
            prependElements.add(indent);
          }
          prependElements.add((XmlTagChild)child);
        }
        else if (child instanceof XmlText) {
          indent = (XmlText)child;
        }
        else if (child instanceof XmlTag) {
          XmlTag subTag = (XmlTag)child;
          String mergeStrategy = subTag.getAttributeValue(MERGE_ATTR_STRATEGY);
          subTag.setAttribute(MERGE_ATTR_STRATEGY, null);
          // remove the space left by the deleted attribute
          CodeStyleManager.getInstance(myProject).reformat(subTag);
          String name = getResourceId(subTag);
          XmlTag replace = name != null ? old.get(name) : null;
          if (replace != null) {
            // There is an existing item with the same id. Either replace it
            // or preserve it depending on the "templateMergeStrategy" attribute.
            // If that attribute does not exist, default to preserving it.

            // Let's say you've used the activity wizard once, and it
            // emits some configuration parameter as a resource that
            // it depends on, say "padding". Then the user goes and
            // tweaks the padding to some other number.
            // Now running the wizard a *second* time for some new activity,
            // we should NOT go and set the value back to the template's
            // default!
            if (MERGE_ATTR_STRATEGY_REPLACE.equals(mergeStrategy)) {
              child = replace.replace(child);
              // When we're replacing, the line is probably already indented. Skip the initial indent
              if (child.getPrevSibling() instanceof XmlText && prependElements.get(0) instanceof XmlText) {
                prependElements.remove(0);
                // If we're adding something we'll need a newline/indent after it
                if (!prependElements.isEmpty()) {
                  prependElements.add(indent);
                }
              }
              for (XmlTagChild element : prependElements) {
                root.addBefore(element, child);
              }
            }
            else if (MERGE_ATTR_STRATEGY_PRESERVE.equals(mergeStrategy)) {
              // Preserve the existing value.
            }
            else {
              // No explicit directive given, preserve the original value by default.
              LOG.warn("Warning: Ignoring name conflict in resource file for name " + name);
            }
          }
          else {
            if (indent != null) {
              prependElements.add(indent);
            }
            subTag = root.addSubTag(subTag, false);
            for (XmlTagChild element : prependElements) {
              root.addBefore(element, subTag);
            }
          }
          prependElements.clear();
        }
      }
    }
    else {
      // In other file types, such as layouts, just append all the new content
      // at the end.
      for (PsiElement child : sourcePsiFile.getRootTag().getChildren()) {
        if (child instanceof XmlTag) {
          root.addSubTag((XmlTag)child, false);
        }
      }
    }
    return targetPsiFile.getText();
  }

  /**
   * Merge the given dependency URLs into the given build.gradle file
   *
   * @param paramMap        the parameters to merge
   * @param gradleBuildFile the build.gradle file which will be written with the merged dependencies
   */
  private void mergeDependenciesIntoFile(@NotNull File gradleBuildFile) throws IOException, TemplateException {
    File gradleTemplate =
      new File(TemplateManager.getTemplateRootFolder().getPath(), FileUtil.join("gradle", "utils", "dependencies.gradle.ftl"));
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
    File parentPath = (src.isDirectory() ? dest : dest.getParentFile());
    VirtualFile destFolder = checkedCreateDirectoryIfMissing(parentPath);
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
