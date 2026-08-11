/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.viewmodel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.templateengine.WizardConstants
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.runReadActionBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils

sealed interface ValidationMessage {
  val message: String

  data class Error(override val message: String) : ValidationMessage

  data class Warning(override val message: String) : ValidationMessage
}

class ConfigureProjectViewModel(
  private val defaultParentDir: Path,
  private val versionsInfo: AndroidVersionsInfo = AndroidVersionsInfo(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
  val nameState = TextFieldState(WizardConstants.DEFAULT_APPLICATION_NAME)
  val packageNameState = TextFieldState("${WizardConstants.DEFAULT_PACKAGE_PREFIX}myapplication")
  val locationState = TextFieldState("")

  val name: String
    get() = nameState.text.toString()

  val packageName: String
    get() = packageNameState.text.toString()

  val location: String
    get() = locationState.text.toString()

  var minSdk by mutableStateOf(WizardConstants.DEFAULT_MIN_SDK)

  var formFactor by mutableStateOf(FormFactor.MOBILE)
    private set

  val availableMinSdks: List<AndroidVersionsInfo.VersionItem>
    get() {
      val minSdkLimit = if (formFactor == FormFactor.MOBILE) 23 else formFactor.minOfflineApiLevel
      return versionsInfo.getKnownTargetVersions(formFactor, minSdkLimit).filter { !it.isPreview }
    }

  var selectedMinSdk by mutableStateOf<AndroidVersionsInfo.VersionItem>(initSelectedMinSdk())
    private set

  private fun initSelectedMinSdk(): AndroidVersionsInfo.VersionItem {
    val savedApi = PropertiesComponent.getInstance().getValue(formFactor.name + "minApi")
    if (savedApi != null) {
      val found = availableMinSdks.find { it.minSdk.apiStringWithExtension == savedApi }
      if (found != null) return found
    }
    val defaultApi = formFactor.defaultApi
    val foundDefault = availableMinSdks.find { it.minApiLevel == defaultApi }
    if (foundDefault != null) return foundDefault

    return availableMinSdks.firstOrNull() ?: throw IllegalStateException("No Android SDK targets found for ${formFactor.displayName}")
  }

  fun updateSelectedMinSdk(item: AndroidVersionsInfo.VersionItem) {
    selectedMinSdk = item
    minSdk = item.minApiLevel.toString()
    PropertiesComponent.getInstance().setValue(formFactor.name + "minApi", item.minSdk.apiStringWithExtension)
  }

  var templateName by mutableStateOf("")
    private set

  var templateDescription by mutableStateOf("")
    private set

  fun updateTemplate(name: String, shortName: String, newFormFactor: FormFactor, minSdkDefault: String? = null) {
    templateName = name
    templateDescription = getTemplateDescription(shortName)
    if (formFactor != newFormFactor) {
      formFactor = newFormFactor
    }
    val found = minSdkDefault?.let { sdk ->
      availableMinSdks.find { it.minSdk.apiStringWithExtension == sdk || it.minApiLevel.toString() == sdk }
    }
    if (found != null) {
      selectedMinSdk = found
      minSdk = found.minApiLevel.toString()
    } else {
      selectedMinSdk = initSelectedMinSdk()
      minSdk = selectedMinSdk.minApiLevel.toString()
    }
  }

  private fun getTemplateDescription(shortName: String): String {
    return when (shortName) {
      WizardConstants.TemplateNames.EMPTY_ACTIVITY ->
        AndroidBundle.message("android.wizard.project.new.choose.template.description.empty.activity")
      else -> AndroidBundle.message("android.wizard.action.new.component", shortName)
    }
  }

  var validationMessage by mutableStateOf<ValidationMessage?>(null)
    private set

  val validationError: String?
    get() = (validationMessage as? ValidationMessage.Error)?.message ?: (validationMessage as? ValidationMessage.Warning)?.message

  val isFinishEnabled: Boolean
    get() = validationMessage !is ValidationMessage.Error

  private var userEditedPackageName = false
  private var userEditedLocation = false

  init {
    locationState.setTextAndPlaceCursorAtEnd(getUniqueProjectLocation(name))
    minSdk = selectedMinSdk.minApiLevel.toString()
    validate()

    scope.launch(Dispatchers.Main) {
      snapshotFlow { nameState.text.toString() }
        .collect { newName ->
          updateDerivedPackageName(newName)
          updateDerivedLocation(newName)
          validate()
        }
    }

    scope.launch(Dispatchers.Main) {
      snapshotFlow { packageNameState.text.toString() }
        .collect { newPackage ->
          if (newPackage != "${WizardConstants.DEFAULT_PACKAGE_PREFIX}${NewProjectModel.nameToJavaPackage(nameState.text.toString())}") {
            userEditedPackageName = true
          }
          validate()
        }
    }

    scope.launch(Dispatchers.Main) {
      snapshotFlow { locationState.text.toString() }
        .collect { newLocation ->
          if (newLocation != getUniqueProjectLocation(nameState.text.toString())) {
            userEditedLocation = true
          }
          validate()
        }
    }
  }

  fun updateName(newName: String) {
    if (nameState.text.toString() != newName) {
      nameState.setTextAndPlaceCursorAtEnd(newName)
      Snapshot.sendApplyNotifications()
    }
  }

  private fun updateDerivedPackageName(newName: String) {
    if (!userEditedPackageName) {
      val derivedPackage = "${WizardConstants.DEFAULT_PACKAGE_PREFIX}${NewProjectModel.nameToJavaPackage(newName)}"
      if (packageNameState.text.toString() != derivedPackage) {
        packageNameState.setTextAndPlaceCursorAtEnd(derivedPackage)
        Snapshot.sendApplyNotifications()
      }
    }
  }

  private fun updateDerivedLocation(newName: String) {
    if (!userEditedLocation) {
      val derivedLocation = getUniqueProjectLocation(newName)
      if (locationState.text.toString() != derivedLocation) {
        locationState.setTextAndPlaceCursorAtEnd(derivedLocation)
        Snapshot.sendApplyNotifications()
      }
    }
  }

  fun updatePackage(newPackage: String) {
    if (packageNameState.text.toString() != newPackage) {
      packageNameState.setTextAndPlaceCursorAtEnd(newPackage)
      Snapshot.sendApplyNotifications()
    }
    userEditedPackageName = true
    validate()
  }

  fun updateLocation(newLocation: String) {
    if (locationState.text.toString() != newLocation) {
      locationState.setTextAndPlaceCursorAtEnd(newLocation)
      Snapshot.sendApplyNotifications()
    }
    userEditedLocation = true
    validate()
  }

  private fun validate() {
    val error = validateProjectParameters(name, packageName, location)
    if (error != null) {
      validationMessage = ValidationMessage.Error(error)
      return
    }

    val path = Path.of(location)
    if (path.exists() && path.isDirectory() && isDirectoryNotEmpty(path)) {
      validationMessage =
        ValidationMessage.Warning(AndroidBundle.message("android.wizard.validate.project.location.exists.not.empty", path.name))
    } else {
      validationMessage = null
    }
  }

  private fun getUniqueProjectLocation(baseName: String): String {
    val sanitizedBase = NewProjectModel.sanitizeApplicationName(baseName)
    var candidate = defaultParentDir.resolve(sanitizedBase)
    var counter = 2
    while (candidate.exists()) {
      candidate = defaultParentDir.resolve("$sanitizedBase$counter")
      counter++
    }
    return candidate.toAbsolutePath().toString()
  }

  private fun isDirectoryNotEmpty(dir: Path): Boolean {
    if (!dir.exists() || !dir.isDirectory()) return false
    return Files.newDirectoryStream(dir).use { it.iterator().hasNext() }
  }

  private fun validateProjectParameters(name: String, packageName: String, location: String): String? {
    if (name.isBlank()) {
      return AndroidBundle.message("android.wizard.validate.empty.application.name")
    }

    val packageError = runReadActionBlocking { AndroidUtils.validatePackageName(packageName) }
    if (packageError != null) {
      return packageError
    }

    if (location.isBlank()) {
      return AndroidBundle.message("android.wizard.validate.empty.project.location")
    }
    val path = Path.of(location)
    if (path.exists() && !path.isDirectory()) {
      return AndroidBundle.message("android.wizard.validate.project.location.not.directory")
    }
    return null
  }
}
