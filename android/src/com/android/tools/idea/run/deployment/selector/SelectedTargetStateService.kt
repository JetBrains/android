/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshot
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.run.util.KotlinInstantConverter
import com.google.common.base.Strings
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlinx.datetime.Instant

/**
 * The PersistentStateComponent responsible for persisting the selected targets in
 * ${PROJECT}/.idea/deploymentTargetSelector.xml.
 *
 * Each run configuration has its own separate state. Within each run configuration, we have state
 * for the dropdown (single-target mode) and state for the dialog box (multiple-target mode).
 *
 * This consists mostly a bunch of duplicative data classes that satisfy the requirements of the XML
 * persistence framework: public no-arg constructor, mutable fields, tags as necessary.
 *
 * Note that this class is required to be thread-safe, yet the XML framework requires mutable
 * fields. If we exposed these mutable fields to the rest of the application, then any consumer
 * would need to participate in the synchronization scheme of this class, which is somewhat
 * impractical.
 *
 * Thus, we have immutable data classes that are used to communicate with the external world, which
 * are translated here into the mutable form required by the XML framework. However, the getState()
 * method required by the PersistentStateComponent interface still returns the mutable
 * SelectionStateList: this should only be used by the persistence framework.
 */
@State(
  name = "deploymentTargetSelector",
  // Roaming disabled because AVD identifiers contain local filesystem paths
  storages = [Storage("deploymentTargetSelector.xml", roamingType = RoamingType.DISABLED)]
)
@Service(Service.Level.PROJECT)
internal class SelectedTargetStateService(val project: Project) :
  PersistentStateComponent<SelectionStateList> {
  @GuardedBy("Lock") private var stateList = SelectionStateList()
  private val runManager: RunManager
    get() = RunManager.getInstance(project)

  private object Lock

  fun getState(runConfiguration: RunConfiguration?): SelectionState {
    if (runConfiguration == null || Strings.isNullOrEmpty(runConfiguration.name)) {
      return SelectionState()
    }

    if (!DeployableToDevice.deploysToLocalDevice(runConfiguration)) {
      // We do not want to keep track of states for configurations that don't deploy to local
      // devices
      return SelectionState()
    }

    return getOrCreateState(runConfiguration.name).fromXml()
  }

  private fun getOrCreateState(runConfigName: String) =
    synchronized(Lock) {
      stateList.selectionStates.find { it.runConfigName == runConfigName }
        ?: SelectionStateXml(runConfigName = runConfigName).also {
          stateList.selectionStates.add(it)
        }
    }

  /**
   * Any updates create a new SelectionStateList, because otherwise the value returned by getState
   * could change.
   */
  fun updateState(newState: SelectionState) =
    synchronized(Lock) {
      val newStateList = SelectionStateList()
      for (selectionState in stateList.selectionStates) {
        newStateList.selectionStates.add(
          if (selectionState.runConfigName == newState.runConfigName) newState.toXml()
          else selectionState
        )
      }
      stateList = newStateList
    }

  override fun getState(): SelectionStateList =
    synchronized(Lock) {
      removeInvalidStates()
      return stateList
    }

  override fun loadState(mapState: SelectionStateList) =
    synchronized(Lock) {
      stateList = mapState
      removeInvalidStates()
    }

  private fun removeInvalidStates() {
    stateList.selectionStates.removeIf {
      // The state can be null if the XML is corrupted
      it == null || !validRunConfigurationExists(it.runConfigName)
    }
  }

  private fun validRunConfigurationExists(runConfigurationName: String): Boolean {
    return runManager.allConfigurationsList.any {
      DeployableToDevice.deploysToLocalDevice(it) && it.name == runConfigurationName
    }
  }
}

internal data class SelectionStateList(
  @XCollection(style = XCollection.Style.v2)
  var selectionStates: MutableList<SelectionStateXml> = mutableListOf()
)

internal data class SelectionState(
  val runConfigName: String = "",
  val selectionMode: SelectionMode = SelectionMode.DROPDOWN,
  val dropdownSelection: DropdownSelection? = null,
  val dialogSelection: DialogSelection = DialogSelection(emptyList()),
) {
  fun toXml() =
    SelectionStateXml(
      runConfigName,
      selectionMode,
      dropdownSelection?.toXml(),
      dialogSelection.toXml()
    )
}

/** The selection state for a specific run config. */
@Tag("SelectionState")
internal data class SelectionStateXml(
  @Attribute var runConfigName: String = "",
  @Property(alwaysWrite = true) var selectionMode: SelectionMode = SelectionMode.DROPDOWN,
  @Property(surroundWithTag = false) var dropdownSelection: DropdownSelectionXml? = null,
  @Property(surroundWithTag = false) var dialogSelection: DialogSelectionXml? = null,
) {
  fun fromXml(): SelectionState =
    SelectionState(
      runConfigName,
      selectionMode,
      dropdownSelection?.fromXml(),
      dialogSelection.fromXml(),
    )
}

internal enum class SelectionMode {
  DROPDOWN,
  DIALOG
}

internal data class DropdownSelection(val target: TargetId, val timestamp: Instant?) {
  fun toXml() = DropdownSelectionXml(target.toXml(), timestamp)
}

@Tag("DropdownSelection")
internal data class DropdownSelectionXml(
  @Property(surroundWithTag = false) var target: TargetIdXml? = null,
  @Attribute(converter = KotlinInstantConverter::class) var timestamp: Instant? = null,
)

internal fun DropdownSelectionXml.fromXml() =
  target?.let { DropdownSelection(it.fromXml(), timestamp) }

internal data class DialogSelection(val targets: List<TargetId>) {
  fun toXml() = DialogSelectionXml(targets.map { it.toXml() })
}

@Tag("DialogSelection")
internal data class DialogSelectionXml(
  @XCollection(style = XCollection.Style.v2) var targets: List<TargetIdXml> = emptyList(),
)

internal fun DialogSelectionXml?.fromXml() =
  if (this == null) DialogSelection(emptyList()) else DialogSelection(targets.map { it.fromXml() })

@Tag("Target")
internal data class TargetIdXml(
  @Tag("handle") var id: DeviceIdXml? = null,
  @Tag("template") var templateId: DeviceIdXml? = null,
  @Attribute("type") var type: TargetType? = null,
  @Property(surroundWithTag = false) var snapshot: SnapshotXml? = null
)

internal enum class TargetType {
  COLD_BOOT,
  DEFAULT_BOOT,
  BOOT_WITH_SNAPSHOT,
}

internal fun TargetId.toXml(): TargetIdXml =
  TargetIdXml(
    type = type,
    id = deviceId.takeIf { it != templateId }?.toXml(),
    templateId = templateId?.toXml(),
    snapshot = (bootOption as? BootSnapshot)?.snapshot?.toXml(),
  )

internal fun TargetIdXml.fromXml(): TargetId {
  val type = checkNotNull(type) { "type is missing" }
  val templateId = templateId?.fromXml()
  val id = checkNotNull(id?.fromXml() ?: templateId) { "handle or template ID is missing" }

  val bootOption =
    when (type) {
      TargetType.COLD_BOOT -> ColdBoot
      TargetType.DEFAULT_BOOT -> DefaultBoot
      TargetType.BOOT_WITH_SNAPSHOT -> BootSnapshot(checkNotNull(snapshot).fromXml())
    }
  return TargetId(id, templateId, bootOption)
}

internal fun Collection<TargetId>.toXml(): List<TargetIdXml> = map { it.toXml() }

internal val TargetId.type: TargetType
  get() =
    when (this.bootOption) {
      is ColdBoot -> TargetType.COLD_BOOT
      is DefaultBoot -> TargetType.DEFAULT_BOOT
      is BootSnapshot -> TargetType.BOOT_WITH_SNAPSHOT
    }

@Tag("DeviceId")
internal data class DeviceIdXml(
  @Attribute var pluginId: String = "",
  // boolean doesn't get serialized properly, so use an enum for type
  @Attribute var type: DeviceIdType = DeviceIdType.HANDLE,
  @Attribute var identifier: String = "",
)

internal enum class DeviceIdType {
  HANDLE,
  TEMPLATE
}

internal fun DeviceId.toXml() =
  DeviceIdXml(pluginId, if (isTemplate) DeviceIdType.TEMPLATE else DeviceIdType.HANDLE, identifier)

internal fun DeviceIdXml.fromXml(): DeviceId? {
  return DeviceId(
    pluginId.takeIf { it.isNotBlank() } ?: return null,
    type == DeviceIdType.TEMPLATE,
    identifier.takeIf { it.isNotBlank() } ?: return null
  )
}

@Tag("Snapshot")
@Property(style = Property.Style.ATTRIBUTE)
internal data class SnapshotXml(
  var type: SnapshotType = SnapshotType.LOCAL_EMULATOR,
  var name: String = "",
  @Attribute(converter = PathConverter::class) var path: Path = Path.of("")
)

internal enum class SnapshotType {
  LOCAL_EMULATOR
}

internal fun Snapshot.toXml(): SnapshotXml =
  when (this) {
    is LocalEmulatorSnapshot -> SnapshotXml(SnapshotType.LOCAL_EMULATOR, name, path)
    else -> throw IllegalStateException()
  }

internal fun SnapshotXml.fromXml() =
  when (type) {
    SnapshotType.LOCAL_EMULATOR -> LocalEmulatorSnapshot(name, path)
  }

private class PathConverter : Converter<Path>() {
  private val fileSystem = FileSystems.getDefault()

  override fun fromString(string: String): Path {
    return fileSystem.getPath(string)
  }

  override fun toString(path: Path): String {
    return path.toString()
  }
}
