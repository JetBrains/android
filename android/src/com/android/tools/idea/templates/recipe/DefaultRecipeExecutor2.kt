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

import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.GRADLE_ANDROID_TEST_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.GRADLE_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.repository.GradleVersion
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.templates.RenderingContextAdapter
import com.android.tools.idea.templates.RepositoryUrlManager
import com.android.tools.idea.templates.TemplateUtils.checkDirectoryIsWriteable
import com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.TemplateUtils.readTextFromDisk
import com.android.tools.idea.templates.TemplateUtils.readTextFromDocument
import com.android.tools.idea.templates.TemplateUtils.writeTextFile
import com.android.tools.idea.templates.findModule
import com.android.tools.idea.templates.resolveDependency
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.SourceSetType
import com.android.tools.idea.wizard.template.findResource
import com.android.utils.XmlUtils.XML_PROLOG
import com.android.utils.findGradleBuildFile
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings.nullToEmpty
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy.IGNORE_WHITESPACES
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VfsUtil.findFileByURL
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.pom.java.LanguageLevel
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

  private val projectTemplateData: ProjectTemplateData get() = context.projectTemplateData
  private val moduleTemplateData: ModuleTemplateData? get() = context.templateData as? ModuleTemplateData

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

    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, project)!!

    val isArtifactInDependencies = configurations.any { c ->
      buildModel.dependencies().containsArtifact(c, ArtifactDependencySpec.create(mavenCoordinate)!!)
    }

    if (isArtifactInDependencies) {
      return true
    }

    // TODO(qumeric): Do we need it at all?
    fun findCorrespondingModule(): Boolean? {
      val modulePath = (context.templateData as? ModuleTemplateData)?.rootDir ?: return null
      val module = findModule(modulePath.toString()) ?: return null
      val facet = AndroidFacet.getInstance(module) ?: return null
      // TODO(b/23032990)
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

    return false
  }

  /**
   * Merges the given XML file into the given destination file (or copies it over if  the destination file does not exist).
   */
  override fun mergeXml(source: String, to: File) {
    val targetFile = getTargetFile(to)
    require(hasExtension(targetFile, DOT_XML)) { "Only XML files can be merged at this point: $targetFile" }

    val targetText = readTargetText(targetFile) ?: run {
      save(source, to, true, true)
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
    val resolvedCoordinate = resolveDependency(RepositoryUrlManager.get(), mavenCoordinate.trim())

    referencesExecutor.addClasspathDependency(resolvedCoordinate)

    val toBeAddedDependency = ArtifactDependencySpec.create(resolvedCoordinate)
    check(toBeAddedDependency != null) { "$resolvedCoordinate is not a valid classpath dependency" }

    val rootBuildFile = findGradleBuildFile(getBaseDirPath(project))
    val buildModel = getBuildModel(rootBuildFile, project)
    if (buildModel == null) {
      mergeBuildFilesAndWrite(
        dependencies = formatClasspath(resolvedCoordinate),
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
    val newConfiguration = convertConfiguration(projectTemplateData.gradlePluginVersion, configuration)
    referencesExecutor.addDependency(newConfiguration, mavenCoordinate)

    val buildFile = getBuildFilePath(context)

    val configuration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)
    val mavenCoordinate = resolveDependency(RepositoryUrlManager.get(), convertToAndroidX(mavenCoordinate), null)

    if (hasDependency(mavenCoordinate)) {
      return
    }

    val buildModel = getBuildModel(buildFile, project) ?: return
    buildModel.dependencies().addArtifact(configuration, mavenCoordinate)
    io.applyChanges(buildModel)
  }

  override fun addModuleDependency(configuration: String, moduleName: String, toModule: String) {
    require(moduleName.isNotEmpty() && moduleName.first() != ':') {
      "incorrect module name (it should not be empty or include first ':')"
    }
    val configuration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)
    val buildFile = findGradleBuildFile(File(toModule))

    // TODO(qumeric) handle it in a better way?
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    buildModel.dependencies().addModule(configuration, ":$moduleName")
    io.applyChanges(buildModel)
  }

  /**
   * Copies the given source file into the given destination file (where the source
   * is allowed to be a directory, in which case the whole directory is copied recursively)
   */
  override fun copy(from: File, to: File) {
    val sourceUrl = findResource(context.templateData.javaClass, from)
    val target = getTargetFile(to)

    val sourceFile = findFileByURL(sourceUrl) ?: error("$from ($sourceUrl)")
    sourceFile.refresh(false, false)
    val destPath = if (sourceFile.isDirectory) target else target.parentFile
    when {
      sourceFile.isDirectory -> copyDirectory(sourceFile, destPath)
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
  override fun save(source: String, to: File, trimVertical: Boolean, squishEmptyLines: Boolean) {
    val targetFile = getTargetFile(to)
    val untrimmedContent = extractFullyQualifiedNames(to, source).trim()
    val trimmedUnsquishedContent = if (trimVertical) untrimmedContent.trim() else untrimmedContent
    val content = if (squishEmptyLines) trimmedUnsquishedContent.squishEmptyLines() else trimmedUnsquishedContent

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
    val rootBuildFile = findGradleBuildFile(getBaseDirPath(context.project))
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
   * Adds a module dependency to global settings.gradle[.kts] file.
   */
  override fun addIncludeToSettings(moduleName: String) {
    ProjectBuildModel.get(context.project).projectSettingsModel?.apply {
      addModulePath(moduleName)
      io.applyChanges(this)
    }
  }

  /**
   * Adds a new build feature to android block. For example, may enable compose.
   */
  override fun setBuildFeature(name: String, value: Boolean) {
    val buildFile = getBuildFilePath(context)
    // TODO(qumeric) handle it in a better way?
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    val feature = when(name) {
      "compose" -> buildModel.android().buildFeatures().compose()
      else -> throw IllegalArgumentException("currently only compose build feature is supported")
    }
    if (feature.valueType != GradlePropertyModel.ValueType.NONE) {
      return // we do not override value if it exists. TODO(qumeric): ask user?
    }
    feature.setValue(value)
    io.applyChanges(buildModel)
  }

  /**
   * Sets sourceCompatibility and targetCompatibility in compileOptions and (if needed) jvmTarget in kotlinOptions.
   */
  override fun requireJavaVersion(version: String, kotlinSupport: Boolean) {
    val languageLevel = LanguageLevel.parse(version)!!
    val buildFile = getBuildFilePath(context)
    // TODO(qumeric) handle it in a better way?
    val buildModel = getBuildModel(buildFile, context.project) ?: return

    fun updateCompatibility(current: LanguageLevelPropertyModel) {
      if (current.valueType == GradlePropertyModel.ValueType.NONE ||
          current.toLanguageLevel()!!.isLessThan(languageLevel)) {
        current.setLanguageLevel(languageLevel)
      }
    }

    buildModel.android().compileOptions().run {
      updateCompatibility(sourceCompatibility())
      updateCompatibility(targetCompatibility())
    }
    if (kotlinSupport && (context.templateData as? ModuleTemplateData)?.isDynamic != true) {
      updateCompatibility(buildModel.android().kotlinOptions().jvmTarget())
    }
    io.applyChanges(buildModel)
  }

  private fun convertToAndroidX(mavenCoordinate: String): String =
    if (projectTemplateData.androidXSupport)
      AndroidxNameUtils.getVersionedCoordinateMapping(mavenCoordinate)
    else
      mavenCoordinate

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
    if (moduleTemplateData?.isNew != false)
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

    val packageName: String? = projectTemplateData.applicationPackage ?: moduleTemplateData?.packageName

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

    open fun applyChanges(settingsModel: GradleSettingsModel) {
      settingsModel.applyChanges()
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

    override fun applyChanges(settingsModel: GradleSettingsModel) {}

    override fun mergeBuildFiles(
      dependencies: String, destinationContents: String, project: Project, supportLibVersionFilter: String?
    ): String = destinationContents
  }
}

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
  return moduleBuildFile?.let { virtualToIoFile(it) } ?: findGradleBuildFile(context.moduleRoot!!)
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


@VisibleForTesting
fun CharSequence.squishEmptyLines(): String {
  var isLastBlank = false
  return this.split("\n").mapNotNull { line ->
    when {
      !line.isBlank() -> line
      !isLastBlank -> "" // replace blank with empty
      else -> null
    }.also {
      isLastBlank = line.isBlank()
    }
  }.joinToString("\n")
}

