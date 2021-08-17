/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.ui.validation.validators.ILLEGAL_CHARACTER
import com.android.tools.idea.ui.validation.validators.ILLEGAL_WINDOWS_FILENAME
import com.android.tools.idea.ui.validation.validators.INVALID_SLASHES
import com.android.tools.idea.ui.validation.validators.IS_EMPTY
import com.android.tools.idea.ui.validation.validators.LOCATION_IS_NOT_A_FILE
import com.android.tools.idea.ui.validation.validators.NON_ASCII_CHARS
import com.android.tools.idea.ui.validation.validators.PARENT_DIRECTORY_NOT_WRITABLE
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.android.tools.idea.ui.validation.validators.WHITESPACE
import com.android.tools.idea.ui.validation.validators.WINDOWS_PATH_TOO_LONG
import com.android.tools.idea.ui.validation.validators.filenameRule
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.DEFAULT_CMAKE_VERSION
import com.android.tools.idea.wizard.template.cMakeListsTxt
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.buttonGroup
import com.intellij.ui.layout.panel
import org.jetbrains.android.facet.AndroidRootUtil.findModuleRootFolderPath
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.swing.JLabel

private const val TITLE = "Add C++ to Module"
private const val DESCRIPTION = "Add C/C++ code built with CMake or ndk-build to this module"

private val creationPathValidator = PathValidator.createDefault("directory")
private val linkPathValidator = PathValidator.Builder().apply {
  withError(IS_EMPTY)
  if (SystemInfo.isWindows) {
    withError(WINDOWS_PATH_TOO_LONG)
  }
  withError(INVALID_SLASHES)
  withError(ILLEGAL_CHARACTER)
  withWarning(WHITESPACE)
  if (SystemInfo.isWindows) {
    withError(ILLEGAL_WINDOWS_FILENAME)
    withError(NON_ASCII_CHARS)
  }
  else {
    withWarning(ILLEGAL_WINDOWS_FILENAME)
    withWarning(NON_ASCII_CHARS)
  }
  withError(PARENT_DIRECTORY_NOT_WRITABLE)
  withError(LOCATION_IS_NOT_A_FILE)
  withError(filenameRule("must be a CMakeLists.txt or *.mk file") {
    it.toLowerCase(Locale.US) == "cmakelists.txt" || it.endsWith(".mk")
  })
}
  .build("build file")

/**
 * Action to add C++ to an existing pure Java/Kotlin Android module. The action pops up a dialog to let user to pick the location of an
 * existing C++ project or create the boilerplate to let user add C++ code from scratch.
 */
class AddCppToModuleAction : AndroidStudioGradleAction(TITLE, DESCRIPTION, null) {

  override fun doUpdate(e: AnActionEvent, project: Project) {
    e.presentation.isEnabledAndVisible = StudioFlags.NPW_NEW_NATIVE_MODULE.get() && e.dataContext.selectedModule?.canAddCppToIt == true
  }

  override fun doPerform(e: AnActionEvent, project: Project) {
    val module = e.dataContext.selectedModule ?: return
    showAddCppToModuleDialog(module)
  }

  private val DataContext.selectedModule: Module?
    get() = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(this)?.singleOrNull()
            ?: LangDataKeys.MODULE.getData(this)

  private val Module.canAddCppToIt: Boolean
    get() {
      val androidModuleModel = AndroidModuleModel.get(this)
      if (androidModuleModel == null || !androidModuleModel.features.isExternalBuildSupported) {
        // Not Android module or it's too old.
        return false
      }
      if (NdkModuleModel.get(this) != null) {
        // Already has C++
        return false
      }
      if (ProjectBuildModel.get(project).getModuleBuildModel(this) == null) {
        // Not synced
        return false
      }
      return true
    }

  companion object {
    fun showAddCppToModuleDialog(module: Module) {
      val moduleRoot = findModuleRootFolderPath(module)
      val defaultCppFolder: File? = moduleRoot?.resolve("src/main/cpp")
      val dialogModel = AddCppToModuleDialogModel(defaultCppFolder)
      lateinit var whitespaceWarningMessage: JLabel
      dialog(
        title = TITLE,
        panel = panel {
          buttonGroup({ dialogModel.addMode.get() }, { dialogModel.addMode.set(it) }) {
            row {
              label("Choose how you want to add C++ to module '${module.name}'")
            }
            row {
              radioButton("Create CMakeLists.txt at the following location", AddMode.CREATE_NEW)
                .component.addActionListener {
                  // Manually add a listener since the automatically bound property by `buttonGroup` is only set upon user clicking "OK".
                  // This is WAI according to https://youtrack.jetbrains.com/issue/IDEA-251062
                  dialogModel.addMode.set(AddMode.CREATE_NEW)
                }
              row {
                textFieldWithBrowseButton(
                  dialogModel.newCppFolder,
                  project = module.project,
                  fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                    withFileFilter { creationPathValidator.validate(it.toIoFile()).severity < Validator.Severity.ERROR }
                    title = "Choose a folder for C++ sources"
                  }
                ).component.apply {
                  setTextFieldPreferredWidth(80)
                }
              }
            }
            row {
              radioButton("Link an existing CMakeLists.txt or Android.mk to this module", AddMode.USE_EXISTING)
                .component.addActionListener {
                  // Manually add a listener since the automatically bound property by `buttonGroup` is only set upon user clicking "OK".
                  // This is WAI according to https://youtrack.jetbrains.com/issue/IDEA-251062
                  dialogModel.addMode.set(AddMode.USE_EXISTING)
                }
              row {
                textFieldWithBrowseButton(
                  dialogModel.existingBuildFile,
                  project = module.project,
                  fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().apply {
                    withFileFilter { linkPathValidator.validate(it.toIoFile()).severity < Validator.Severity.ERROR }
                    title = "Choose a CMakeLists.txt or Android.mk file"
                  }
                ).component.apply {
                  setTextFieldPreferredWidth(80)
                }
              }
            }
            row {
              whitespaceWarningMessage = label("").component.apply {
                val validationResult = dialogModel.isValid.get()
                icon = validationResult.severity.icon
                text = validationResult.message
                isVisible = validationResult.severity > Validator.Severity.OK
              }
            }
          }
        },
        resizable = true,
        okActionEnabled = dialogModel.isValid.get().severity < Validator.Severity.ERROR,
        ok = { onOkClicked(module, dialogModel, moduleRoot) }
      ).apply {
        dialogModel.isValid.afterChange { result ->
          isOKActionEnabled = result.severity < Validator.Severity.ERROR
          whitespaceWarningMessage.icon = result.severity.icon
          whitespaceWarningMessage.text = result.message
          whitespaceWarningMessage.isVisible = result.severity > Validator.Severity.OK
        }
        show()
      }
    }

    private fun onOkClicked(module: Module, dialogModel: AddCppToModuleDialogModel, moduleRoot: File?): List<ValidationInfo> {
      val buildModel = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throw IllegalStateException(
        "Cannot find gradle model for module ${module.name}")

      val filesToOpen = mutableListOf<File>()
      WriteCommandAction.writeCommandAction(module.project).withName("Adding C++ to module ${module.name}").run<Throwable> {
        val externalNativeBuildFile: File = when (dialogModel.addMode.get()) {
          AddMode.CREATE_NEW -> {
            val cppFolder = File(dialogModel.newCppFolder.get())
            cppFolder.mkdirs()
            val libraryName = buildModel.android().defaultConfig().applicationId().valueAsString()?.split('.')?.last()
                              ?: module.name.substringAfterLast('.').replace(' ', '_')
            val sourceName = "$libraryName.cpp"
            filesToOpen += cppFolder.resolve(sourceName).apply {
              writeText("""
              // Write C++ code here.
              //
              // Do not forget to dynamically load the C++ library into your application.
              //
              // For instance,
              //
              // In MainActivity.java:
              //    static {
              //       System.loadLibrary("$libraryName");
              //    }
              //
              // Or, in MainActivity.kt:
              //    companion object {
              //      init {
              //         System.loadLibrary("$libraryName")
              //      }
              //    }
            """.trimIndent())
            }
            cppFolder.resolve("CMakeLists.txt").apply {
              writeText(cMakeListsTxt(sourceName, libraryName), StandardCharsets.UTF_8)
              filesToOpen += this
            }
          }
          AddMode.USE_EXISTING -> File(dialogModel.existingBuildFile.get()).also { filesToOpen += it }
        }.absoluteFile
        val relativizedPath = if (moduleRoot != null) {
          try {
            externalNativeBuildFile.relativeTo(moduleRoot)
          }
          catch (e: IllegalArgumentException) {
            // Handle the case if the externalNativeBuildFile is under a different root than the module root.
            externalNativeBuildFile
          }
        }
        else {
          externalNativeBuildFile
        }
        buildModel.android().apply {
          when (relativizedPath.name.toLowerCase(Locale.US)) {
            "cmakelists.txt" -> {
              externalNativeBuild().cmake().apply {
                path().setValue(FileUtils.toSystemIndependentPath(relativizedPath.path))
                version().setValue(DEFAULT_CMAKE_VERSION)
              }
              defaultConfig().externalNativeBuild().cmake().cppFlags().setValue("")
            }
            else -> {
              externalNativeBuild().ndkBuild().path().setValue(FileUtils.toSystemIndependentPath(relativizedPath.path))
              defaultConfig().externalNativeBuild().ndkBuild().cppFlags().setValue("")
            }
          }
        }
        buildModel.applyChanges()
        filesToOpen.addIfNotNull(GradleUtil.getGradleBuildFile(module)?.toIoFile())
      }

      filesToOpen.mapNotNull { VfsUtil.findFileByIoFile(it, true) }
        .reversed()
        .forEach { file -> OpenFileDescriptor(module.project, file).navigate(true) }

      GradleSyncInvoker.getInstance().requestProjectSync(module.project, GradleSyncStats.Trigger.TRIGGER_CPP_EXTERNAL_PROJECT_LINKED)
      return emptyList()
    }
  }
}

private class AddCppToModuleDialogModel(defaultCppFolder: File?) {
  private val propertyGraph = PropertyGraph()
  val addMode = propertyGraph.graphProperty { AddMode.CREATE_NEW }
  val existingBuildFile = propertyGraph.graphProperty { "" }
  val newCppFolder = propertyGraph.graphProperty { defaultCppFolder?.path ?: "" }
  val isValid = propertyGraph.graphProperty { creationPathValidator.validate(defaultCppFolder ?: File("")) }

  init {
    isValid.dependsOn(addMode, ::checkIsValid)
    isValid.dependsOn(existingBuildFile, ::checkIsValid)
    isValid.dependsOn(newCppFolder, ::checkIsValid)
  }

  private fun checkIsValid(): Validator.Result = when (addMode.get()) {
    AddMode.CREATE_NEW -> creationPathValidator.validate(File(newCppFolder.get()))
    AddMode.USE_EXISTING -> linkPathValidator.validate(File(existingBuildFile.get()))
  }
}

private enum class AddMode {
  USE_EXISTING, CREATE_NEW
}
