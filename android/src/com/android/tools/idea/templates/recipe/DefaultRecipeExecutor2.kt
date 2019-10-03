/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.GRADLE_ANDROID_TEST_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.GRADLE_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.SUPPORT_LIB_ARTIFACT
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.repository.GradleVersion
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFilePath
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.templates.RenderingContextAdapter
import com.android.tools.idea.templates.TemplateUtils.checkDirectoryIsWriteable
import com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.TemplateUtils.readTextFromDisk
import com.android.tools.idea.templates.TemplateUtils.readTextFromDocument
import com.android.tools.idea.templates.TemplateUtils.writeTextFile
import com.android.tools.idea.templates.findModule
import com.android.tools.idea.templates.mergeGradleSettingsFile
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.SourceSetType
import com.android.utils.XmlUtils.XML_PROLOG
import com.google.common.base.Strings.nullToEmpty
import com.google.common.io.Resources.getResource
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy.IGNORE_WHITESPACES
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.XmlElementFactory
import com.intellij.util.LineSeparator
import freemarker.template.TemplateModelException
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.IOException
import com.android.tools.idea.templates.mergeXml as mergeXmlUtil
import com.android.tools.idea.wizard.template.RecipeExecutor as RecipeExecutor2

/**
 * Executor support for recipe instructions.
 *
 * Note: it tries to use [GradleBuildModel] for merging of Gradle files, but falls back on simple merging if it is unavailable.
 */
class DefaultRecipeExecutor2(private val context: RenderingContext2) : RecipeExecutor2 {
  private val project: Project get() = context.project
  private val referencesExecutor: FindReferencesRecipeExecutor2 = FindReferencesRecipeExecutor2(context)
  private val io: RecipeIO = if (context.dryRun) DryRunRecipeIO() else RecipeIO()
  private val readonlyStatusHandler: ReadonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)

  private val templateData: ModuleTemplateData get() = context.templateData

  @Suppress("DEPRECATION")
  override fun hasDependency(mavenCoordinate: String, configuration: String?): Boolean {
    val defaultConfigurations = arrayOf(
      GRADLE_COMPILE_CONFIGURATION, GRADLE_IMPLEMENTATION_CONFIGURATION, GRADLE_API_CONFIGURATION)
    val defaultTestConfigurations = arrayOf(
      GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION, GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION, GRADLE_ANDROID_TEST_API_CONFIGURATION)

    // Determine the configuration to check, based on the second argument passed to the function.
    // Defaults to "compile" and "implementation and "api".
    val configurations: Array<String> = if (configuration != null)
      arrayOf(configuration)
    else
      defaultConfigurations

    val isArtifactInDependencies = configurations.any { conf ->
      context.dependencies.get(conf).any { it.contains(mavenCoordinate) }
    }

    if (isArtifactInDependencies) {
      return true
    }

    fun findCorrespondingModule(): Boolean? {
      val modulePath = (context.templateData as? ModuleTemplateData)?.rootDir ?: return null
      // TODO rewrite findModule to accept a file
      val module = findModule(modulePath.toString()) ?: return null
      val facet = AndroidFacet.getInstance(module) ?: return null
      // TODO: b/23032990
      val androidModel = AndroidModuleModel.get(facet) ?: return null
      return when (configurations[0]) {
        in defaultConfigurations ->
          GradleUtil.dependsOn(androidModel, mavenCoordinate) || GradleUtil.dependsOnJavaLibrary(androidModel, mavenCoordinate) // For Kotlin dependencies
        in defaultTestConfigurations -> GradleUtil.dependsOnAndroidTest(androidModel, mavenCoordinate)
        else -> throw TemplateModelException("Unknown dependency configuration " + configurations[0])
      }
    }

    val isCorrespondingDefaultModuleFound = findCorrespondingModule()
    if (isCorrespondingDefaultModuleFound != null) {
      return isCorrespondingDefaultModuleFound
    }

    // Creating a new module, so no existing dependencies: provide some defaults. This is really intended for appcompat-v7,
    // but since it depends on support-v4, we include it here (such that a query to see if support-v4 is installed in a newly
    // created project will return true since it will be by virtue of appcompat also being installed.)
    if (mavenCoordinate.contains(APPCOMPAT_LIB_ARTIFACT) || mavenCoordinate.contains(SUPPORT_LIB_ARTIFACT)) {
      // No dependencies: Base it off of the minApi and buildApi versions:
      // If building with Lollipop, and targeting anything earlier than Lollipop, use appcompat.
      // (Also use it if minApi is less than ICS.)
      val buildApiObject = context.templateData.projectTemplateData.buildApi
      val minApiObject = context.templateData.projectTemplateData.minApiLevel
      return buildApiObject >= 21 && minApiObject < 21
    }

    return false
  }

  /**
   * Merges the given gradle file into the given destination file (or copies it over if the destination file does not exist).
   */
  @Deprecated("Avoid merging Gradle files, add to an existing file programmatically instead")
  override fun mergeGradleFile(source: String, to: File) {
    val targetFile = getTargetFile(to)
    require(hasExtension(targetFile, DOT_GRADLE)) { "Only Gradle files can be merged at this point: $targetFile" }

    val targetText = readTargetText(targetFile) ?: run {
      save(source, to)
      return
    }

    val contents: String = when {
      targetFile.name == GRADLE_PROJECT_SETTINGS_FILE -> mergeGradleSettingsFile(source, targetText)
      targetFile.name == FN_BUILD_GRADLE -> {
        val compileSdkVersion = templateData.projectTemplateData.buildApiString
        io.mergeBuildFiles(source, targetText, project, compileSdkVersion)
      }
      else -> throw RuntimeException("Only $GRADLE_PROJECT_SETTINGS_FILE and $FN_BUILD_GRADLE can be merged: $targetFile")
    }

    writeTargetFile(this, contents, targetFile)
  }

  /**
   * Merges the given XML file into the given destination file (or copies it over if  the destination file does not exist).
   */
  override fun mergeXml(source: String, to: File) {
    val targetFile = getTargetFile(to)
    require(hasExtension(targetFile, DOT_XML)) { "Only XML files can be merged at this point: $targetFile" }

    val targetText = readTargetText(targetFile) ?: run {
      save(source, to)
      return
    }

    val contents = mergeXmlUtil(RenderingContextAdapter(context), source, targetText, targetFile)

    writeTargetFile(this, contents, targetFile)
  }

  override fun open(file: File) {
    context.filesToOpen.add(file)
  }

  override fun applyPlugin(plugin: String) {
    referencesExecutor.applyPlugin(plugin)

    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, project)
    if (buildModel != null) {
      if (buildModel.plugins().none { it.name().forceString() == plugin }) {
        buildModel.applyPlugin(plugin)
        io.applyChanges(buildModel)
      }
      return
    }

    mergeBuildFilesAndWrite(
      dependencies = "apply plugin: '$plugin'",
      content = if (buildFile.exists()) (readTextFile(buildFile) ?: "") else "",
      project = project,
      buildFile = buildFile
    )
  }

  override fun addClasspathDependency(mavenCoordinate: String) {
    referencesExecutor.addClasspathDependency(mavenCoordinate)

    val toBeAddedDependency = ArtifactDependencySpec.create(mavenCoordinate)
    check(toBeAddedDependency != null) { "$mavenCoordinate is not a valid classpath dependency" }

    val rootBuildFile = getGradleBuildFilePath(getBaseDirPath(project))
    val buildModel = getBuildModel(rootBuildFile, project)
    if (buildModel == null) {
      mergeBuildFilesAndWrite(
        dependencies = formatClasspath(mavenCoordinate),
        content = if (rootBuildFile.exists()) nullToEmpty(readTextFile(rootBuildFile)) else "",
        project = project,
        buildFile = rootBuildFile
      )
      return
    }

    val buildscriptDependencies = buildModel.buildscript().dependencies()
    val targetDependencyModel = buildscriptDependencies.artifacts(CLASSPATH_CONFIGURATION_NAME).firstOrNull {
      toBeAddedDependency.equalsIgnoreVersion(ArtifactDependencySpec.create(it))
    }
    if (targetDependencyModel == null) {
      buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, toBeAddedDependency)
    }
    else {
      val toBeAddedVersion = GradleVersion.parse(toBeAddedDependency.version ?: "")
      val existingVersion = GradleVersion.parse(targetDependencyModel.version().toString())
      if (toBeAddedVersion > existingVersion) {
        targetDependencyModel.version().setValue(toBeAddedDependency.version ?: "")
      }
    }
    io.applyChanges(buildModel)
  }

  /**
   * Add a library dependency into the project.
   */
  override fun addDependency(mavenCoordinate: String, configuration: String) {
    // Translate from "compile" to "implementation" based on the parameter map context
    val newConfiguration = convertConfiguration(templateData.projectTemplateData.gradlePluginVersion, configuration)
    referencesExecutor.addDependency(newConfiguration, mavenCoordinate)
    context.dependencies.put(newConfiguration, mavenCoordinate)
  }

  /**
   * Copies the given source file into the given destination file (where the source
   * is allowed to be a directory, in which case the whole directory is copied recursively)
   */
  override fun copy(from: File, to: File) {
    val source = File(getResource(from.path).path)
    val target = getTargetFile(to)

    val sourceFile = findFileByIoFile(source, true) ?: error(from)
    sourceFile.refresh(false, false)
    val destPath = if (source.isDirectory) target else target.parentFile
    when {
      source.isDirectory -> copyDirectory(sourceFile, destPath)
      target.exists() -> if (!sourceFile.contentEquals(target)) {
        addFileAlreadyExistWarning(target)
      }
      else -> {
        val document = FileDocumentManager.getInstance().getDocument(sourceFile)
        if (document != null) {
          io.writeFile(this, document.text, target)
        }
        else {
          io.copyFile(this, sourceFile, destPath, target.name)
        }
        referencesExecutor.addTargetFile(target)
      }
    }
  }

  /**
   * Instantiates the given template file into the given output file (running the Freemarker engine over it)
   */
  override fun save(source: String, to: File) {
    val targetFile = getTargetFile(to)
    val content = extractFullyQualifiedNames(to, source)

    if (targetFile.exists()) {
      if (!targetFile.contentEquals(content)) {
        addFileAlreadyExistWarning(targetFile)
      }
      return
    }
    io.writeFile(this, content, targetFile)
    referencesExecutor.addTargetFile(targetFile)
  }

  override fun createDirectory(at: File) {
    io.mkDir(getTargetFile(at))
  }

  override fun addSourceSet(type: SourceSetType, name: String, dir: File) {
    val buildFile = getBuildFilePath(context)
    // TODO(qumeric) handle it in a better way?
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    val sourceSet = buildModel.android().addSourceSet(name)

    if (type == SourceSetType.MANIFEST) {
      sourceSet.manifest().srcFile().setValue(dir)
      io.applyChanges(buildModel)
      return
    }

    val srcDirsModel = with(sourceSet) {
      when (type) {
        SourceSetType.AIDL -> aidl()
        SourceSetType.ASSETS -> assets()
        SourceSetType.JAVA -> java()
        SourceSetType.JNI -> jni()
        SourceSetType.RENDERSCRIPT -> renderscript()
        SourceSetType.RES -> res()
        SourceSetType.RESOURCES -> resources()
        SourceSetType.MANIFEST -> throw RuntimeException("manifest should have been handled earlier")
      }
    }.srcDirs()

    val dirExists = srcDirsModel.toList().orEmpty().any { it.toString() == dir.path }

    if (dirExists) {
      return
    }

    srcDirsModel.addListValue().setValue(dir)
    io.applyChanges(buildModel)
  }

  override fun setExtVar(name: String, value: Any) {
    val rootBuildFile = getGradleBuildFilePath(getBaseDirPath(context.project))
    // TODO(qumeric) handle it in more reliable way?
    val buildModel = getBuildModel(rootBuildFile, context.project) ?: return
    val property = buildModel.buildscript().ext().findProperty(name)
    if (property.valueType != GradlePropertyModel.ValueType.NONE) {
      return // we do not override property value if it exists. TODO(qumeric): ask user?
    }
    property.setValue(value)
    io.applyChanges(buildModel)
  }

  /**
   * Merge the URLs from our gradle template into the target module's build.gradle file
   */
  // TODO(qumeric): remove it. We want finer-grained merge.
  fun mergeDependenciesIntoGradle() {
    // Note: ATTR_BASE_FEATURE_DIR has a value set for Instant App/Dynamic Feature modules.
    val baseFeatureRoot = "" // TODO
    val featureBuildFile = getBuildFilePath(context)
    if (baseFeatureRoot.isNullOrEmpty()) {
      writeDependencies(featureBuildFile)
      return
    }
    // The new gradle API deprecates the "compile" keyword by two new keywords: "implementation" and "api"
    var configName = convertConfiguration(templateData.projectTemplateData.gradlePluginVersion, "compile")
    if ("implementation" == configName) {
      // For the base module, we want to use "api" instead of "implementation"
      for (apiDependency in context.dependencies.removeAll("implementation")) {
        context.dependencies.put("api", apiDependency)
      }
      configName = "api"
    }

    // If a Library (e.g. Google Maps) Manifest references its own resources, it needs to be added to the Base, otherwise aapt2 will fail
    // during linking. Since we don't know the libraries Manifest references, we declare this libraries in the base as "api" dependencies.
    val baseBuildFile = getGradleBuildFilePath(File(baseFeatureRoot))
    val configuration = configName
    writeDependencies(baseBuildFile) { it == configuration }
    writeDependencies(featureBuildFile) { it != configuration }
  }

  private fun writeDependencies(buildFile: File, configurationFilter: (String) -> Boolean = { true }) {
    fun convertToAndroidX(dep: String): String =
      if (templateData.projectTemplateData.androidXSupport)
        AndroidxNameUtils.getVersionedCoordinateMapping(dep)
      else
        dep

    fun formatDependencies(configurationFilter: (String) -> Boolean): String =
      context.dependencies.entries().asSequence()
        .filter { (configuration, _) -> configurationFilter(configuration) }
        .joinToString("\n", "dependencies {\n", "\n}\n") { (configuration, mavenCoordinate) ->
          val dependencyValue = convertToAndroidX(mavenCoordinate)
          // Interpolated values need to be in double quotes
          val delimiter = if (dependencyValue.contains("$")) '"' else '\''
          "  $configuration $delimiter$dependencyValue$delimiter"
        }

    mergeBuildFilesAndWrite(
      dependencies = formatDependencies(configurationFilter),
      content = if (buildFile.exists()) nullToEmpty(readTextFile(buildFile)) else "",
      project = project,
      buildFile = buildFile,
      supportLibVersionFilter = templateData.projectTemplateData.buildApi.toString())
  }

  /**
   * [VfsUtil.copyDirectory] messes up the undo stack, most likely by trying to create a directory even if it already exists.
   * This is an undo-friendly replacement.
   */
  private fun copyDirectory(src: VirtualFile, dest: File) =
    VfsUtilCore.visitChildrenRecursively(src, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean {
        try {
          return copyFile(file, src, dest)
        }
        catch (e: IOException) {
          throw VisitorException(e)
        }
      }
    }, IOException::class.java)

  private fun copyFile(file: VirtualFile, src: VirtualFile, destinationFile: File): Boolean {
    val relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar)
    check(relativePath != null) { "${file.path} is not a child of $src" }
    if (file.isDirectory) {
      io.mkDir(File(destinationFile, relativePath))
      return true
    }
    val target = File(destinationFile, relativePath)
    if (target.exists()) {
      if (!file.contentEquals(target)) {
        addFileAlreadyExistWarning(target)
      }
    }
    else {
      io.copyFile(this, file, target)
      referencesExecutor.addTargetFile(target)
    }
    return true
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  private fun getTargetFile(file: File): File = if (file.isAbsolute)
    file
  else
    File(context.outputRoot, file.path)

  private fun readTextFile(file: File): String? =
    if (templateData.isNew)
      readTextFromDisk(file)
    else
      readTextFromDocument(project, file)

  /**
   * Shorten all fully qualified Layout names that belong to the same package as the manifest's package attribute value.
   *
   * @See [com.android.manifmerger.ManifestMerger2.extractFqcns]
   */
  private fun extractFullyQualifiedNames(to: File, content: String): String {
    if (ResourceFolderType.getFolderType(to.parentFile.name) != ResourceFolderType.LAYOUT) {
      return content
    }

    val packageName: String? = templateData.projectTemplateData.applicationPackage ?: templateData.packageName

    val factory = XmlElementFactory.getInstance(project)
    val root = factory.createTagFromText(content)

    // Note: At the moment only root "context:tools" atribute needs to be shorten
    val contextAttr = root.getAttribute(ATTR_CONTEXT, TOOLS_URI)
    val context = contextAttr?.value
    if (packageName == null || context == null || !context.startsWith("$packageName.")) {
      return content
    }

    val newContext = context.substring(packageName.length)
    root.setAttribute(ATTR_CONTEXT, TOOLS_URI, newContext)

    return XML_PROLOG + root.text
  }

  private fun readTargetText(targetFile: File): String? {
    if (!targetFile.exists()) {
      return null
    }
    if (project.isInitialized) {
      val toFile = findFileByIoFile(targetFile, true)
      val status = readonlyStatusHandler.ensureFilesWritable(listOf(toFile!!))
      check(!status.hasReadonlyFiles()) { "Attempt to update file that is readonly: ${targetFile.absolutePath}" }
    }
    return readTextFile(targetFile)
  }

  private fun writeTargetFile(requestor: Any, contents: String?, to: File) {
    io.writeFile(requestor, contents, to)
    referencesExecutor.addTargetFile(to)
  }

  private fun VirtualFile.contentEquals(targetFile: File): Boolean =
    findFileByIoFile(targetFile, true)?.let { targetVFile ->
      if (fileType.isBinary)
        this.contentsToByteArray() contentEquals targetVFile.contentsToByteArray()
      else
        ComparisonManager.getInstance().isEquals(readTextFile(toIoFile())!!, readTextFile(targetVFile.toIoFile())!!, IGNORE_WHITESPACES)
    } ?: false

  private infix fun File.contentEquals(content: String): Boolean =
    ComparisonManager.getInstance().isEquals(content, readTextFile(this)!!, IGNORE_WHITESPACES)

  // TODO why argument is called "dependencies"? This is very suspicious. Also maybe it should be removed.
  private fun mergeBuildFilesAndWrite(
    dependencies: String,
    content: String,
    project: Project,
    buildFile: File,
    supportLibVersionFilter: String = ""
  ) = io.writeFile(this, io.mergeBuildFiles(dependencies, content, project, supportLibVersionFilter), buildFile)

  private fun addFileAlreadyExistWarning(targetFile: File) =
    context.warnings.add("The following file could not be created since it already exists: ${targetFile.path}")

  // TODO(qumeric): make private
  open class RecipeIO {
    open fun writeFile(requestor: Any, contents: String?, to: File) {
      checkedCreateDirectoryIfMissing(to.parentFile)
      writeTextFile(this, contents, to)
    }

    open fun copyFile(requestor: Any, file: VirtualFile, toFile: File) {
      val toDir = checkedCreateDirectoryIfMissing(toFile.parentFile)
      VfsUtilCore.copyFile(this, file, toDir)
    }

    open fun copyFile(requestor: Any, file: VirtualFile, toFileDir: File, newName: String) {
      val toDir = checkedCreateDirectoryIfMissing(toFileDir)
      VfsUtilCore.copyFile(requestor, file, toDir, newName)
    }

    open fun mkDir(directory: File) {
      checkedCreateDirectoryIfMissing(directory)
    }

    open fun applyChanges(buildModel: GradleBuildModel) {
      buildModel.applyChanges()
    }

    open fun mergeBuildFiles(
      dependencies: String, destinationContents: String, project: Project, supportLibVersionFilter: String?
    ): String = project.getProjectSystem().mergeBuildFiles(dependencies, destinationContents, supportLibVersionFilter)
  }

  // TODO(qumeric): make private
  class DryRunRecipeIO : RecipeIO() {
    override fun writeFile(requestor: Any, contents: String?, to: File) {
      checkDirectoryIsWriteable(to.parentFile)
    }

    override fun copyFile(requestor: Any, file: VirtualFile, toFile: File) {
      checkDirectoryIsWriteable(toFile.parentFile)
    }

    override fun copyFile(requestor: Any, file: VirtualFile, toFileDir: File, newName: String) {
      checkDirectoryIsWriteable(toFileDir)
    }

    override fun mkDir(directory: File) {
      checkDirectoryIsWriteable(directory)
    }

    override fun applyChanges(buildModel: GradleBuildModel) {}

    override fun mergeBuildFiles(
      dependencies: String, destinationContents: String, project: Project, supportLibVersionFilter: String?
    ): String = destinationContents
  }
}

/**
 * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
 */
// TODO(qumeric): make private
const val GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle"

/**
 * 'classpath' is the configuration name used to specify buildscript dependencies.
 */
// TODO(qumeric): make private
const val CLASSPATH_CONFIGURATION_NAME = "classpath"

private val LINE_SEPARATOR = LineSeparator.getSystemLineSeparator().separatorString

// TODO(qumeric): make private
fun getBuildModel(buildFile: File, project: Project): GradleBuildModel? {
  if (project.isDisposed || !buildFile.exists()) {
    return null
  }
  val virtualFile = findFileByIoFile(buildFile, true) ?: throw RuntimeException("Failed to find " + buildFile.path)

  // TemplateUtils.writeTextFile saves Documents but doesn't commit them, since there might not be a Project to speak of yet.
  // ProjectBuildModel uses PSI, so let's make sure the Document is committed, since it's illegal to modify PSI for a file with
  // and uncommitted Document.
  FileDocumentManager.getInstance()
    .getCachedDocument(virtualFile)
    ?.let(PsiDocumentManager.getInstance(project)::commitDocument)

  return ProjectBuildModel.getOrLog(project)?.getModuleBuildModel(virtualFile)
}

private fun getBuildFilePath(context: RenderingContext2): File {
  val module = context.module
  val moduleBuildFile = if (module == null) null else getGradleBuildFile(module)
  return moduleBuildFile?.let { virtualToIoFile(it) } ?: getGradleBuildFilePath(context.moduleRoot)
}

// TODO(qumeric): make private
fun formatClasspath(dependency: String): String =
  "buildscript {" + LINE_SEPARATOR +
  "  dependencies {" + LINE_SEPARATOR +
  "    classpath '" + dependency + "'" + LINE_SEPARATOR +
  "  }" + LINE_SEPARATOR +
  "}" + LINE_SEPARATOR


fun convertConfiguration(agpVersion: String?, configuration: String): String =
  GradleUtil.mapConfigurationName(configuration, agpVersion, false)
