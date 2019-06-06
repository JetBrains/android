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

import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.DOT_FTL;
import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFilePath;
import static com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_APPLICATION_PACKAGE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BASE_FEATURE_DIR;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateUtils.checkDirectoryIsWriteable;
import static com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing;
import static com.android.tools.idea.templates.TemplateUtils.hasExtension;
import static com.android.tools.idea.templates.TemplateUtils.readTextFromDisk;
import static com.android.tools.idea.templates.TemplateUtils.readTextFromDocument;
import static com.android.tools.idea.templates.TemplateUtils.writeTextFile;
import static com.android.utils.XmlUtils.XML_PROLOG;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.ide.common.repository.GradleVersion;
import com.android.manifmerger.XmlElement;
import com.android.resources.ResourceFolderType;
import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.templates.FmGetConfigurationNameMethod;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateUserVisibleException;
import com.android.tools.idea.templates.RecipeMergeUtils;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.SetMultimap;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LineSeparator;
import freemarker.template.Configuration;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executor support for recipe instructions.
 */
public final class DefaultRecipeExecutor implements RecipeExecutor {

  /**
   * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
   */
  private static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  /**
   * 'classpath' is the configuration name used to specify buildscript dependencies.
   */
  private static final String CLASSPATH_CONFIGURATION_NAME = "classpath";

  private static final String LINE_SEPARATOR = LineSeparator.getSystemLineSeparator().getSeparatorString();

  private final FindReferencesRecipeExecutor myReferences;
  private final RenderingContext myContext;
  private final RecipeIO myIO;
  private final ReadonlyStatusHandler myReadonlyStatusHandler;

  public DefaultRecipeExecutor(@NotNull RenderingContext context, boolean dryRun) {
    myReferences = new FindReferencesRecipeExecutor(context);
    myContext = context;
    myIO = dryRun ? new DryRunRecipeIO() : new RecipeIO();
    myReadonlyStatusHandler = ReadonlyStatusHandler.getInstance(context.getProject());
  }

  @Nullable
  private static GradleBuildModel getBuildModel(@NotNull File buildFile, @NotNull Project project) {
    VirtualFile virtualFile = findFileByIoFile(buildFile, true);
    if (virtualFile == null) {
      throw new RuntimeException("Failed to find " + buildFile.getPath());
    }
    ProjectBuildModel projectBuildModel = ProjectBuildModel.getOrLog(project);
    if (projectBuildModel == null) {
      return null;
    }

    return projectBuildModel.getModuleBuildModel(virtualFile);
  }

  @Override
  public void applyPlugin(@NotNull String plugin) {
    String name = plugin.trim();
    myReferences.applyPlugin(name);

    Project project = myContext.getProject();
    File buildFile = getBuildFilePath(myContext);
    if (project.isInitialized()) {
      GradleBuildModel buildModel = getBuildModel(buildFile, project);
      if (buildModel != null) {
        if (buildModel.plugins().stream().noneMatch(x -> x.name().forceString().equals(name))) {
          buildModel.applyPlugin(name);
          myIO.applyChanges(buildModel);
        }
        return;
      }
    }

    // The attempt above to add the plugin using the GradleBuildModel failed, now attempt to add the plugin by appending the string.
    String destinationContents = buildFile.exists() ? nullToEmpty(readTextFile(buildFile)) : "";
    String applyPluginStatement = "apply plugin: '" + name + "'";
    String result = destinationContents.isEmpty() ? applyPluginStatement : destinationContents + LINE_SEPARATOR + applyPluginStatement;
    try {
      myIO.writeFile(this, result, buildFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void addClasspath(@NotNull String mavenUrl) {
    mavenUrl = mavenUrl.trim();

    myReferences.addClasspath(mavenUrl);

    ArtifactDependencySpec toBeAddedDependency = ArtifactDependencySpec.create(mavenUrl);
    if (toBeAddedDependency == null) {
      throw new RuntimeException(mavenUrl + " is not a valid classpath dependency");
    }

    Project project = myContext.getProject();
    File rootBuildFile = getGradleBuildFilePath(getBaseDirPath(project));
    if (project.isInitialized()) {
      GradleBuildModel buildModel = getBuildModel(rootBuildFile, project);
      if (buildModel != null) {
        DependenciesModel buildscriptDependencies = buildModel.buildscript().dependencies();
        ArtifactDependencyModel targetDependencyModel = null;
        for (ArtifactDependencyModel dependencyModel : buildscriptDependencies.artifacts(CLASSPATH_CONFIGURATION_NAME)) {
          if (toBeAddedDependency.equalsIgnoreVersion(ArtifactDependencySpec.create(dependencyModel))) {
            targetDependencyModel = dependencyModel;
          }
        }
        if (targetDependencyModel == null) {
          buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, toBeAddedDependency);
        }
        else {
          GradleVersion toBeAddedDependencyVersion = GradleVersion.parse(nullToEmpty(toBeAddedDependency.getVersion()));
          GradleVersion existingDependencyVersion = GradleVersion.parse(nullToEmpty(targetDependencyModel.version().toString()));
          if (toBeAddedDependencyVersion.compareTo(existingDependencyVersion) > 0) {
            targetDependencyModel.version().setValue(nullToEmpty(toBeAddedDependency.getVersion()));
          }
        }
        myIO.applyChanges(buildModel);
        return;
      }
    }

    // The attempt above to merge the classpath using the GradleBuildModel failed, now attempt to merge the classpaths by merging the files.
    String destinationContents = rootBuildFile.exists() ? nullToEmpty(readTextFile(rootBuildFile)) : "";
    String result = myIO.mergeBuildFiles(formatClasspath(mavenUrl), destinationContents, project, "");
    try {
      myIO.writeFile(this, result, rootBuildFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static String formatClasspath(@NotNull String dependency) {
    return "buildscript {" + LINE_SEPARATOR +
           "  dependencies {" + LINE_SEPARATOR +
           "    classpath '" + dependency + "'" + LINE_SEPARATOR +
           "  }" + LINE_SEPARATOR +
           "}" + LINE_SEPARATOR;
  }

  @NotNull
  private static File getBuildFilePath(@NotNull RenderingContext context) {
    Module module = context.getModule();
    VirtualFile moduleBuildFile = module == null ? null : getGradleBuildFile(module);
    return moduleBuildFile == null ? getGradleBuildFilePath(context.getModuleRoot()) : virtualToIoFile(moduleBuildFile);
  }

  /**
   * Add a library dependency into the project.
   */
  @Override
  public void addDependency(@NotNull String configuration, @NotNull String mavenUrl) {
    // Translate from "configuration" to "implementation" based on the parameter map context
    configuration = FmGetConfigurationNameMethod.convertConfiguration(getParamMap(), configuration);

    myReferences.addDependency(configuration, mavenUrl);
    //noinspection unchecked
    SetMultimap<String, String> dependencyList =
      (SetMultimap<String, String>)getParamMap().get(TemplateMetadata.ATTR_DEPENDENCIES_MULTIMAP);
    dependencyList.put(configuration, mavenUrl);
  }

  @Override
  public void addFilesToOpen(@NotNull File file) {
    myReferences.addFilesToOpen(file);
  }

  private void addWarning(@NotNull String warning) {
    myContext.getWarnings().add(warning);
  }

  @NotNull
  private Map<String, Object> getParamMap() {
    return myContext.getParamMap();
  }

  @NotNull
  Configuration getFreemarker() {
    return myContext.getFreemarkerConfiguration();
  }

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  @Override
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
  @Override
  public void instantiate(@NotNull File from, @NotNull File to) throws TemplateProcessingException {
    try {
      // For now, treat extension-less files as directories... this isn't quite right
      // so I should refine this! Maybe with a unique attribute in the template file?
      boolean isDirectory = from.getName().indexOf('.') == -1;
      if (isDirectory) {
        // It's a directory
        copyTemplateResource(from, to);
      }
      else {
        File sourceFile = myContext.getLoader().getSourceFile(from);
        File targetFile = getTargetFile(to);
        String content = processFreemarkerTemplate(myContext, sourceFile, null);
        content = extractFullyQualifiedNames(to, content);

        if (targetFile.exists()) {
          if (!compareTextFile(targetFile, content)) {
            addFileAlreadyExistWarning(targetFile);
          }
        }
        else {
          myIO.writeFile(this, content, targetFile);
          myReferences.addSourceFile(sourceFile);
          myReferences.addTargetFile(targetFile);
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   * <p/>
   * Only XML and Gradle files are currently supported.
   */
  @Override
  public void merge(@NotNull File from, @NotNull File to) throws TemplateProcessingException {
    try {
      String targetText = null;

      File sourceFile = myContext.getLoader().getSourceFile(from);
      File targetFile = getTargetFile(to);
      if (!(hasExtension(targetFile, DOT_XML) || hasExtension(targetFile, DOT_GRADLE))) {
        throw new RuntimeException("Only XML or Gradle files can be merged at this point: " + targetFile);
      }

      if (targetFile.exists()) {
        if (myContext.getProject().isInitialized()) {
          VirtualFile toFile = findFileByIoFile(targetFile, true);
          final ReadonlyStatusHandler.OperationStatus status = myReadonlyStatusHandler.ensureFilesWritable(toFile);
          if (status.hasReadonlyFiles()) {
            throw new TemplateUserVisibleException(
              String.format("Attempt to update file that is readonly: %1$s", targetFile.getAbsolutePath()));
          }
        }
        targetText = readTextFile(targetFile);
      }

      if (targetText == null) {
        // The target file doesn't exist: don't merge, just copy
        boolean instantiate = hasExtension(from, DOT_FTL);
        if (instantiate) {
          instantiate(from, targetFile);
        }
        else {
          copyTemplateResource(from, targetFile);
        }
        return;
      }

      String sourceText;
      if (hasExtension(from, DOT_FTL)) {
        // Perform template substitution of the template prior to merging
        sourceText = processFreemarkerTemplate(myContext, from, null);
      }
      else {
        sourceText = readTextFromDisk(sourceFile);
        if (sourceText == null) {
          return;
        }
      }

      String contents;
      if (targetFile.getName().equals(GRADLE_PROJECT_SETTINGS_FILE)) {
        contents = RecipeMergeUtils.mergeGradleSettingsFile(sourceText, targetText);
      }
      else if (targetFile.getName().equals(FN_BUILD_GRADLE)) {
        String compileSdkVersion = (String)getParamMap().get(TemplateMetadata.ATTR_BUILD_API_STRING);
        contents = myIO.mergeBuildFiles(sourceText, targetText, myContext.getProject(), compileSdkVersion);
      }
      else if (hasExtension(targetFile, DOT_XML)) {
        contents = RecipeMergeUtils.mergeXml(myContext, sourceText, targetText, targetFile);
      }
      else {
        throw new RuntimeException("Only XML or Gradle settings files can be merged at this point: " + targetFile);
      }

      myIO.writeFile(this, contents, targetFile);
      myReferences.addSourceFile(sourceFile);
      myReferences.addTargetFile(targetFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void append(@NotNull File from, @NotNull File to) {
    try {
      File sourceFile = myContext.getLoader().getSourceFile(from);
      File targetFile = getTargetFile(to);

      final String sourceText = readTextFromDisk(sourceFile);
      if (sourceText == null) {
        return;
      }

      if (targetFile.exists()) {
        final String targetContents = readTextFromDisk(targetFile);
        final String resultContents = (targetContents == null ? "" : targetContents + LINE_SEPARATOR) + sourceText;

        myIO.writeFile(this, resultContents, targetFile);
      }
      else {
        myIO.writeFile(this, sourceText, targetFile);
      }
      myReferences.addSourceFile(sourceFile);
      myReferences.addTargetFile(targetFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void addGlobalVariable(@NotNull String id, @NotNull Object value) {
    myContext.getParamMap().put(id, value);
  }

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  @Override
  public void mkDir(@NotNull File at) {
    try {
      myIO.mkDir(getTargetFile(at));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  @Override
  public void updateAndSync() {
    // Handle dependencies
    if (!myContext.getDependencies().isEmpty()) {
      try {
        mergeDependenciesIntoGradle();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void pushFolder(@NotNull String folder) {
    try {
      myContext.getLoader().pushTemplateFolder(folder);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void popFolder() {
    myContext.getLoader().popTemplateFolder();
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  @NotNull
  private File getTargetFile(@NotNull File file) {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myContext.getOutputRoot(), file.getPath());
  }

  /**
   * Merge the URLs from our gradle template into the target module's build.gradle file
   */
  private void mergeDependenciesIntoGradle() throws Exception {
    // Note: ATTR_BASE_FEATURE_DIR has a value set for Instant App/Dynamic Feature modules.
    String baseFeatureRoot = (String)getParamMap().getOrDefault(ATTR_BASE_FEATURE_DIR, "");
    File featureBuildFile = getBuildFilePath(myContext);
    if (isNullOrEmpty(baseFeatureRoot)) {
      writeDependencies(featureBuildFile, x -> true);
    }
    else {
      // The new gradle API deprecates the "compile" keyword by two new keywords: "implementation" and "api"
      String configName = FmGetConfigurationNameMethod.convertConfiguration(getParamMap(), "compile");
      if ("implementation".equals(configName)) {
        // For the base module, we want to use "api" instead of "implementation"
        for (String apiDependency : myContext.getDependencies().removeAll("implementation")) {
          myContext.getDependencies().put("api", apiDependency);
        }
        configName = "api";
      }

      // If a Library (e.g. Google Maps) Manifest references its own resources, it needs to be added to the Base, otherwise aapt2 will fail
      // during linking. Since we don't know the libraries Manifest references, we declare this libraries in the base as "api" dependencies.
      File baseBuildFile = getGradleBuildFilePath(new File(baseFeatureRoot));
      String configuration = configName;
      writeDependencies(baseBuildFile, x -> x.equals(configuration));
      writeDependencies(featureBuildFile, x -> !x.equals(configuration));
    }
  }

  private void writeDependencies(File buildFile, Predicate<String> configurationFilter) throws IOException {
    String destinationContents = buildFile.exists() ? nullToEmpty(readTextFile(buildFile)) : "";
    Object buildApi = getParamMap().get(ATTR_BUILD_API);
    String supportLibVersionFilter = buildApi != null ? buildApi.toString() : "";
    String result =
      myIO.mergeBuildFiles(formatDependencies(configurationFilter), destinationContents, myContext.getProject(), supportLibVersionFilter);
    myIO.writeFile(this, result, buildFile);
  }

  private String formatDependencies(Predicate<String> configurationFilter) {
    StringBuilder dependencies = new StringBuilder();
    dependencies.append("dependencies {\n");
    for (Map.Entry<String, String> dependency : myContext.getDependencies().entries()) {
      if (configurationFilter.test(dependency.getKey())) {
        dependencies.append("  ")
          .append(dependency.getKey())
          .append(" ");
        final String dependencyValue = convertToAndroidX(dependency.getValue());
        // Interpolated values need to be in double quotes
        boolean isInterpolated = dependencyValue.contains("$");
        dependencies.append(isInterpolated ? '"' : '\'')
          .append(dependencyValue)
          .append(isInterpolated ? '"' : '\'')
          .append("\n");
      }
    }
    dependencies.append("}\n");
    return dependencies.toString();
  }

  private String convertToAndroidX(String dep) {
    Boolean useAndroidX = (Boolean)getParamMap().get(ATTR_ANDROIDX_SUPPORT);
    if (Boolean.TRUE.equals(useAndroidX)) {
      return AndroidxNameUtils.getVersionedCoordinateMapping(dep);
    }
    return dep;
  }

  /**
   * VfsUtil#copyDirectory messes up the undo stack, most likely by trying to
   * create a directory even if it already exists. This is an undo-friendly
   * replacement.
   */
  private void copyDirectory(@NotNull final VirtualFile src, @NotNull final File dest) throws IOException {
    VfsUtilCore.visitChildrenRecursively(src, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        try {
          return copyFile(file, src, dest);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }
    }, IOException.class);
  }

  private void copyTemplateResource(@NotNull File from, @NotNull File to) throws IOException {
    File source = myContext.getLoader().getSourceFile(from);
    File target = getTargetFile(to);

    VirtualFile sourceFile = findFileByIoFile(source, true);
    assert sourceFile != null : source;
    sourceFile.refresh(false, false);
    File destPath = (source.isDirectory() ? target : target.getParentFile());
    if (source.isDirectory()) {
      copyDirectory(sourceFile, destPath);
    }
    else if (target.exists()) {
      if (!compareFile(sourceFile, target)) {
        addFileAlreadyExistWarning(target);
      }
    }
    else {
      Document document = FileDocumentManager.getInstance().getDocument(sourceFile);
      if (document != null) {
        myIO.writeFile(this, document.getText(), target);
      }
      else {
        myIO.copyFile(this, sourceFile, destPath, target.getName());
      }
      myReferences.addSourceFile(source);
      myReferences.addTargetFile(target);
    }
  }

  private boolean copyFile(VirtualFile file, VirtualFile src, File destinationFile) throws IOException {
    String relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar);
    if (relativePath == null) {
      throw new RuntimeException(String.format("%1$s is not a child of %2$s", file.getPath(), src));
    }
    if (file.isDirectory()) {
      myIO.mkDir(new File(destinationFile, relativePath));
    }
    else {
      File target = new File(destinationFile, relativePath);
      if (target.exists()) {
        if (!compareFile(file, target)) {
          addFileAlreadyExistWarning(target);
        }
      }
      else {
        myIO.copyFile(this, file, target);
        myReferences.addSourceFile(virtualToIoFile(file));
        myReferences.addTargetFile(target);
      }
    }
    return true;
  }

  private String readTextFile(@NotNull File file) {
    // TODO: Rename ATTR_IS_NEW_PROJECT to ATTR_IS_NEW_MODULE since that is what it means...
    if (Boolean.TRUE.equals(myContext.getParamMap().get(TemplateMetadata.ATTR_IS_NEW_PROJECT))) {
      return readTextFromDisk(file);
    }
    else {
      return readTextFromDocument(myContext.getProject(), file);
    }
  }

  private String readTextFile(@NotNull VirtualFile file) {
    // TODO: Rename ATTR_IS_NEW_PROJECT to ATTR_IS_NEW_MODULE since that is what it means...
    if (Boolean.TRUE.equals(myContext.getParamMap().get(TemplateMetadata.ATTR_IS_NEW_PROJECT))) {
      return readTextFromDisk(virtualToIoFile(file));
    }
    else {
      return readTextFromDocument(myContext.getProject(), file);
    }
  }

  /**
   * Shorten all fully qualified Layout names that belong to the same package as the manifest's
   * package attribute value.
   *
   * @See {@link com.android.manifmerger.ManifestMerger2#extractFqcns(String, XmlElement)}
   */
  private String extractFullyQualifiedNames(@NotNull File to, @NotNull String content) {
    if (ResourceFolderType.getFolderType(to.getParentFile().getName()) != ResourceFolderType.LAYOUT) {
      return content;
    }

    String packageName = (String) getParamMap().get(ATTR_APPLICATION_PACKAGE);
    if (packageName == null) {
      packageName = (String) getParamMap().get(ATTR_PACKAGE_NAME);
    }

    XmlElementFactory factory = XmlElementFactory.getInstance(myContext.getProject());
    XmlTag root = factory.createTagFromText(content);

    // Note: At the moment only root "context:tools" atribute needs to be shorten
    XmlAttribute contextAttr = root.getAttribute(ATTR_CONTEXT, TOOLS_URI);
    if (packageName == null || contextAttr == null) {
      return content;
    }

    String context = contextAttr.getValue();
    if (context == null || !context.startsWith(packageName + '.')) {
      return content;
    }

    String newContext = context.substring(packageName.length());
    root.setAttribute(ATTR_CONTEXT, TOOLS_URI, newContext);

    return XML_PROLOG + root.getText();
  }

  /**
   * Return true if the content of {@code targetFile} is the same as the content of {@code sourceVFile}.
   */
  public boolean compareFile(@NotNull VirtualFile sourceVFile, @NotNull File targetFile)
    throws IOException {
    VirtualFile targetVFile = findFileByIoFile(targetFile, true);
    if (targetVFile == null) {
      return false;
    }
    if (sourceVFile.getFileType().isBinary()) {
      byte[] source = sourceVFile.contentsToByteArray();
      byte[] target = targetVFile.contentsToByteArray();
      return Arrays.equals(source, target);
    }
    else {
      String source = readTextFile(sourceVFile);
      String target = readTextFile(targetVFile);
      ComparisonManager comparisonManager = ComparisonManager.getInstance();
      return comparisonManager.isEquals(source, target, ComparisonPolicy.IGNORE_WHITESPACES);
    }
  }

  /**
   * Return true if the content of {@code targetFile} is the same as {@code content}.
   */
  public boolean compareTextFile(@NotNull File targetFile, @NotNull String content) {
    String target = readTextFile(targetFile);
    ComparisonManager comparisonManager = ComparisonManager.getInstance();
    return comparisonManager.isEquals(content, target, ComparisonPolicy.IGNORE_WHITESPACES);
  }

  private void addFileAlreadyExistWarning(@NotNull File targetFile) {
    addWarning(String.format("The following file could not be created since it already exists: %1$s", targetFile.getPath()));
  }


  private static class RecipeIO {
    public void writeFile(@NotNull Object requestor, @Nullable String contents, @NotNull File to) throws IOException {
      checkedCreateDirectoryIfMissing(to.getParentFile());
      writeTextFile(this, contents, to);
    }

    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFile) throws IOException {
      VirtualFile toDir = checkedCreateDirectoryIfMissing(toFile.getParentFile());
      VfsUtilCore.copyFile(this, file, toDir);
    }

    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFileDir, @NotNull String newName)
      throws IOException {
      VirtualFile toDir = checkedCreateDirectoryIfMissing(toFileDir);
      VfsUtilCore.copyFile(requestor, file, toDir, newName);
    }

    public void mkDir(@NotNull File directory) throws IOException {
      checkedCreateDirectoryIfMissing(directory);
    }

    public void applyChanges(@NotNull GradleBuildModel buildModel) {
      buildModel.applyChanges();
    }

    public String mergeBuildFiles(@NotNull String dependencies,
                                  @NotNull String destinationContents,
                                  @NotNull Project project,
                                  @Nullable String supportLibVersionFilter) {
      return ProjectSystemUtil.getProjectSystem(project).mergeBuildFiles(dependencies, destinationContents, supportLibVersionFilter);
    }
  }

  private static class DryRunRecipeIO extends RecipeIO {
    @Override
    public void writeFile(@NotNull Object requestor, @Nullable String contents, @NotNull File to) throws IOException {
      checkDirectoryIsWriteable(to.getParentFile());
    }

    @Override
    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFile) throws IOException {
      checkDirectoryIsWriteable(toFile.getParentFile());
    }

    @Override
    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFileDir, @Nullable String newName)
      throws IOException {
      checkDirectoryIsWriteable(toFileDir);
    }

    @Override
    public void mkDir(@NotNull File directory) throws IOException {
      checkDirectoryIsWriteable(directory);
    }

    @Override
    public void applyChanges(@NotNull GradleBuildModel buildModel) {
    }

    @Override
    public String mergeBuildFiles(@NotNull String dependencies,
                                  @NotNull String destinationContents,
                                  @NotNull Project project,
                                  String compileSdkVersion) {
      return destinationContents;
    }
  }
}
