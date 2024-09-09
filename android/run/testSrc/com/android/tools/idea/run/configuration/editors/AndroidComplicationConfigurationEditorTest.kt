/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.editors

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.deployer.model.component.Complication.ComplicationType
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.model.TestMergedManifestSnapshotBuilder
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.utils.PositionXmlParser
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.base.Charsets
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import java.awt.event.ActionEvent
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.w3c.dom.Element

class AndroidComplicationConfigurationEditorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture
    get() = projectRule.fixture

  private val module
    get() = projectRule.projectRule.module

  private lateinit var manifestSnapshot: MergedManifestSnapshot
  private lateinit var runConfiguration: AndroidComplicationConfiguration
  private lateinit var configurationConfigurable:
    SingleConfigurationConfigurable<AndroidComplicationConfiguration>
  private lateinit var settingsEditor: AndroidComplicationConfigurationEditor
  private val editor
    get() = settingsEditor.component as DialogPanel

  // region editor-utils
  private val componentComboBox
    get() = TreeWalker(editor).descendants().filterIsInstance<ComboBox<String>>()[1]

  private val modulesComboBox
    get() = TreeWalker(editor).descendants().filterIsInstance<ModulesComboBox>().first()

  private val slotsPanel
    get() = (editor.components.first { it is SlotsPanel } as SlotsPanel).slotsUiPanel

  private val <T> ComboBox<T>.items
    get() = (0 until itemCount).map { getItemAt(it) }

  private fun getPanelForSlot(slotNum: Int) = slots(slotsPanel)[slotNum] as JPanel

  private fun JPanel.getComboBox() = getComponent(2) as ComboBox<*>

  private fun JPanel.getCheckBox() = getComponent(0) as JCheckBox

  private fun slots(slotsPanel: Box) =
    ((slotsPanel.getComponent(0) as JComponent).getComponent(0) as JComponent).components

  private fun countCheckedSlots(slotsPanel: Box) =
    slots(slotsPanel).count { (it as JPanel).getCheckBox().isSelected }

  private fun countEnabledSlots(slotsPanel: Box) =
    slots(slotsPanel).count { (it as JPanel).getCheckBox().isEnabled }

  // endregion editor-utils

  @Before
  fun setUp() {
    manifestSnapshot = TestMergedManifestSnapshotBuilder.builder(module).build()
    fixture.addComplicationServiceClass()

    // List of FQ Complication names added and their supported types as String in manifest.
    val complicationsInProject =
      mapOf(
        "com.example.MyIconComplication" to "ICON",
        "com.example.MyLongShortTextComplication" to "LONG_TEXT, SHORT_TEXT",
        "com.example.MyNoTypeComplication" to "",
        "com.example.MyAllTypesComplication" to "ICON, LONG_TEXT, SHORT_TEXT, LARGE_IMAGE",
      )

    complicationsInProject.forEach(addComplicationToProjectAndManifest())

    val runConfigurationFactory = AndroidComplicationConfigurationType().configurationFactories[0]
    val runManager = RunManagerImpl.getInstanceImpl(projectRule.project)
    runConfiguration =
      AndroidComplicationConfiguration(projectRule.project, runConfigurationFactory)

    val settings = RunnerAndConfigurationSettingsImpl(runManager, runConfiguration)
    configurationConfigurable = SingleConfigurationConfigurable.editSettings(settings, null)

    settingsEditor =
      (configurationConfigurable.editor as ConfigurationSettingsEditorWrapper)
        .selectTabAndGetEditor(AndroidComplicationConfigurationEditor::class.java)
    mockMergedManifest()

    // Don't delete. Is needed for [BaseRCSettingsConfigurable.isModified] be checked via
    // serialization.
    configurationConfigurable.apply()
    modulesComboBox.isEditable = true // To allow setting fake module names in the tests.

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @After
  fun tearDown() {
    configurationConfigurable.disposeUIResources()
  }

  private fun addComplicationToProjectAndManifest() = { name: String, supportedTypes: String ->
    fixture.addComplication(name)
    val newServiceInManifest = getServiceDomElement(name, supportedTypes)
    manifestSnapshot =
      TestMergedManifestSnapshotBuilder.builder(module)
        .setServices(manifestSnapshot.services + newServiceInManifest)
        .build()
  }

  private fun getServiceDomElement(complicationName: String, supportedTypes: String): Element {
    val serviceString =
      """<?xml version="1.0" encoding="utf-8"?>
    <service xmlns:android="http://schemas.android.com/apk/res/android"
        android:name="$complicationName">
        <meta-data
            android:name="android.support.wearable.complications.SUPPORTED_TYPES"
            android:value="$supportedTypes"/>
    </service>
    """

    val stream = ByteArrayInputStream(serviceString.toByteArray(Charsets.UTF_8))
    val document = PositionXmlParser.parse(stream)
    return document.documentElement
  }

  private fun mockMergedManifest() {
    val supplier =
      object : AsyncSupplier<MergedManifestSnapshot> {
        override val now
          get() = manifestSnapshot

        override fun get(): ListenableFuture<MergedManifestSnapshot> =
          immediateFuture(manifestSnapshot)
      }
    val mockMergedManifestManager = Mockito.mock(MergedManifestManager::class.java)
    whenever(mockMergedManifestManager.mergedManifest).thenReturn(supplier)
    module.replaceService(
      MergedManifestManager::class.java,
      mockMergedManifestManager,
      projectRule.project,
    )
  }

  @Test
  fun testResetFromEmptyConfiguration() {
    assertThat(runConfiguration.module).isNull()
    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testResetWithMissingModule() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(null)
    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testPMFlags() {
    runConfiguration.deployOptions.pmInstallFlags = ""
    val textField = TreeWalker(editor).descendants().filterIsInstance<JBTextField>()[0]
    textField.text = "Some Flags"
    assertThat(configurationConfigurable.isModified).isTrue()
    configurationConfigurable.apply()
    assertThat(runConfiguration.deployOptions.pmInstallFlags).isEqualTo("Some Flags")
  }

  @Test
  @Ignore("b/326598354")
  fun testResetFromConfigurationWithChosenSlots() {
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot("Top", 3, arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON))
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }
    runConfiguration.componentLaunchOptions.chosenSlots =
      listOf(AndroidComplicationConfiguration.ChosenSlot(3, ComplicationType.ICON))
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)

    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    val topSlot = getPanelForSlot(0)
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(1)
    assertThat(topSlot.getCheckBox().isSelected).isTrue()
    assertThat(topSlot.getComboBox().selectedItem).isEqualTo(ComplicationType.ICON)
  }

  @Test
  @Ignore("b/364922561")
  fun testCleanupComplicationNameOnModuleChange() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)
    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    assertThat(componentComboBox.item).isEqualTo("com.example.MyIconComplication")

    modulesComboBox.selectedItem = null
    assertThat(componentComboBox.item).isEqualTo(null)
    configurationConfigurable.apply()
    assertThat(runConfiguration.componentLaunchOptions.componentName).isEqualTo(null)

    modulesComboBox.selectedItem = module
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    configurationConfigurable.apply()

    assertThat(runConfiguration.componentLaunchOptions.componentName)
      .isEqualTo("com.example.MyLongShortTextComplication")
  }

  @Test
  @Ignore("b/326598354")
  fun testFilterComponentTypes() {
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              0,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            ),
            ComplicationSlot(
              "Right",
              2,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON),
            ),
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }
    configurationConfigurable.reset()

    modulesComboBox.item = module
    editor.apply()
    // region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat((getPanelForSlot(0).getComboBox().items)).isEmpty()

    // intersect between (SHORT_TEXT, ICON) and (ICON)
    assertThat((getPanelForSlot(1).getComboBox().items)).containsExactly(ComplicationType.ICON)
    // first available type is chosen.
    assertThat((getPanelForSlot(1).getComboBox().item)).isEqualTo(ComplicationType.ICON)
    // endregion MyIconComplication

    // region MyLongShortTextComplication
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat((getPanelForSlot(0).getComboBox().items))
      .containsExactly(ComplicationType.SHORT_TEXT)

    // intersect between (SHORT_TEXT, ICON) and (SHORT_TEXT, LONG_TEXT)
    assertThat((getPanelForSlot(1).getComboBox().items))
      .containsExactly(ComplicationType.SHORT_TEXT)
    // endregion MyLongShortTextComplication

    // region MyNoTypeComplication
    componentComboBox.item = "com.example.MyNoTypeComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // intersect between (SHORT_TEXT, RANGED_VALUE) and ()
    assertThat((getPanelForSlot(0).getComboBox().items)).isEmpty()
    assertThat((getPanelForSlot(0).getComboBox().isEnabled)).isFalse()

    // intersect between (SHORT_TEXT, ICON) and ()
    assertThat((getPanelForSlot(1).getComboBox().items)).isEmpty()
    // endregion MyIconComplication
  }

  @Test
  fun `test update slot type from invalid to first available`() {
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              0,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            ),
            ComplicationSlot(
              "Right",
              2,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON),
            ),
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }

    configurationConfigurable.reset()
    modulesComboBox.item = module
    editor.apply()
    // Choose complication provider
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(((getPanelForSlot(0).getComponent(2) as ComboBox<*>).items)).isEmpty()

    // first available is ICON
    assertThat(((getPanelForSlot(1).getComponent(2) as ComboBox<*>).item))
      .isEqualTo(ComplicationType.ICON)
    // Select the check box
    getPanelForSlot(1).getCheckBox().isSelected = true
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    // save first available for configuration
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().type)
      .isEqualTo(ComplicationType.ICON)
  }

  @Test
  fun testNullType() {
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              0,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            )
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }

    configurationConfigurable.reset()
    modulesComboBox.item = module
    editor.apply()
    // region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat((getPanelForSlot(0).getComboBox().items)).isEmpty()
    val comboBoxRenderer =
      (getPanelForSlot(0).getComboBox().renderer as ListCellRenderer<ComplicationType>)
        .getListCellRendererComponent(
          JList(),
          getPanelForSlot(0).getComboBox().item as? ComplicationType,
          -1,
          false,
          false,
        ) as SimpleListCellRenderer<*>
    // Select the check box
    getPanelForSlot(0).getCheckBox().isSelected = true
    getPanelForSlot(0).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))

    assertThat(comboBoxRenderer.text).isEqualTo("No type is supported by this slot")

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    // save first available for configuration
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().type).isEqualTo(null)
  }

  @Test
  fun testClearSlotsOnComplicationNameChange() {
    assertThat(modulesComboBox.isEnabled).isTrue()
    modulesComboBox.item = module
    assertThat(modulesComboBox.item).isEqualTo(module)

    componentComboBox.item = "com.example.MyIconComplication"
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(slots(slotsPanel)).hasLength(5)
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // Add slot.
    getPanelForSlot(0).getCheckBox().isSelected = true
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(1)

    // Change name
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    // Assert that previously added slots are removed.
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)
  }

  @Test
  @Ignore("b/327623449")
  fun testResetFromAndApplyTo() {
    runConfiguration.componentLaunchOptions.componentName =
      "com.example.MyLongShortTextComplication"
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              0,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            ),
            ComplicationSlot(
              "Right",
              2,
              arrayOf(
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.ICON,
              ),
            ),
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }

    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(modulesComboBox.item).isEqualTo(module)
    assertThat(componentComboBox.item).isEqualTo("com.example.MyLongShortTextComplication")

    // runConfiguration doesn't have chosen components.
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)

    // Add slot.
    getPanelForSlot(0).getCheckBox().isSelected = true
    getPanelForSlot(0).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(1)

    val slotTypeComboBox1 = (getPanelForSlot(0).getComponent(2) as ComboBox<*>)
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotTypeComboBox1.items).containsExactly(ComplicationType.SHORT_TEXT)

    // Add slot.
    getPanelForSlot(1).getCheckBox().isSelected = true
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
    // runConfiguration.watchFaceInfo has only 2 available slots.
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(2)

    val slotTypeComboBox2 = getPanelForSlot(1).getComboBox()
    // intersect between (LONG_TEXT, SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotTypeComboBox2.items)
      .containsExactly(ComplicationType.LONG_TEXT, ComplicationType.SHORT_TEXT)

    // Choose LONG_TEXT for slot with id 2.
    (slotTypeComboBox2 as ComboBox<ComplicationType>).item = ComplicationType.LONG_TEXT

    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 2 }!!.type)
      .isEqualTo(ComplicationType.LONG_TEXT)

    // Changing type.
    (slotTypeComboBox2 as ComboBox<ComplicationType>).item = ComplicationType.SHORT_TEXT
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 2 }!!.type)
      .isEqualTo(ComplicationType.SHORT_TEXT)

    // Uncheck the Right slot.
    getPanelForSlot(1).getCheckBox().isSelected = false
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(1)
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().id).isEqualTo(0)
  }

  @Test
  @Ignore("b/364922561")
  fun testRestoreComponentName() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)

    configurationConfigurable.reset()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(componentComboBox.selectedItem).isEqualTo("com.example.MyIconComplication")
  }

  @Test
  fun testApkFound() {
    assertThat(
        Files.isRegularFile(Paths.get(runConfiguration.componentLaunchOptions.watchFaceInfo.apk))
      )
      .isTrue()
  }

  @Test
  fun slotsAreDisabledWhenNoComponentIsSelected() {
    // the module and component are null, all the slots should be disabled
    assertThat(modulesComboBox.item).isNull()
    assertThat(componentComboBox.item).isNull()
    assertThat(slots(slotsPanel)).hasLength(5)
    assertThat(countEnabledSlots(slotsPanel)).isEqualTo(0)

    // set the module component, all slots should be enabled
    modulesComboBox.item = module
    componentComboBox.item = "com.example.MyAllTypesComplication"
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(slots(slotsPanel)).hasLength(5)
    assertThat(countEnabledSlots(slotsPanel)).isEqualTo(5)

    // unset the component, the slots panel should be disabled
    componentComboBox.item = null
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(slots(slotsPanel)).hasLength(5)
    assertThat(countEnabledSlots(slotsPanel)).isEqualTo(0)
  }

  @Test
  fun selectedSlotsAreResetWhenNoComponentIsSelected() {
    modulesComboBox.item = module
    componentComboBox.item = "com.example.MyAllTypesComplication"
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    getPanelForSlot(0).getCheckBox().isSelected = true
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(1)

    // unset the component, the selected slot should be deselected
    componentComboBox.item = null
    editor.apply()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(countCheckedSlots(slotsPanel)).isEqualTo(0)
  }

  @Test
  @RunsInEdt
  fun testSlotsAreDisplayedInASingleConfigurableEditor() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              0,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            ),
            ComplicationSlot(
              "Right",
              2,
              arrayOf(
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.ICON,
              ),
            ),
          )

        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }

    val dialog =
      object :
        SingleConfigurableEditor(
          projectRule.project,
          configurationConfigurable,
          null,
          IdeModalityType.IDE,
        ) {}
    createModalDialogAndInteractWithIt({ dialog.show() }) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertThat(slots(slotsPanel)).hasLength(2)
    }
  }
}

private fun CodeInsightTestFixture.addComplicationServiceClass() {
  addFileToProject(
    "src/lib/ComplicationDataSourceService.kt",
    """
      package androidx.wear.watchface.complications.datasource

      open class ComplicationDataSourceService
    """
      .trimIndent(),
  )
}

private fun CodeInsightTestFixture.addComplication(complicationFqName: String) {
  addFileToProject(
    "src/lib/${complicationFqName.replace(".", "/")}.java",
    """
    package ${complicationFqName.substringBeforeLast(".")}

    public class ${
                     complicationFqName.substringAfterLast(".")
                   } extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
  """
      .trimIndent(),
  )
}
