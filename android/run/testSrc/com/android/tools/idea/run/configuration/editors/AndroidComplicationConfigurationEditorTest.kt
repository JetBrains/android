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

import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.delayUntilCondition
import com.android.testutils.ignore.IgnoreTestRule
import com.android.testutils.ignore.IgnoreWithCondition
import com.android.testutils.ignore.OnWindows
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.deployer.model.component.Complication.ComplicationType
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.model.TestMergedManifestSnapshotBuilder
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.android.tools.idea.testing.AndroidProjectRule
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
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.w3c.dom.Element
import java.awt.event.ActionEvent
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class AndroidComplicationConfigurationEditorTest {
  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val fakeAdb = FakeAdbRule()

  private val fixture
    get() = projectRule.fixture

  private val module
    get() = projectRule.module

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
    get() = (editor.components.first { it is SlotsPanel } as SlotsPanel)

  private val <T> ComboBox<T>.items
    get() = (0 until itemCount).map { getItemAt(it) }

  private fun getPanelForSlot(slotNum: Int) = slotsPanel.slots()[slotNum]

  private fun JPanel.getComboBox() = getComponent(2) as ComboBox<*>

  private fun JPanel.getCheckBox() = getComponent(0) as JCheckBox

  private fun SlotsPanel.slots(): Array<JPanel> {
    val components =
      ((this.slotsUiPanel.getComponent(0) as JComponent).getComponent(0) as JComponent).components
    return if (components.isEmpty()) {
      emptyArray()
    } else {
      components.filterIsInstance<JPanel>().toTypedArray()
    }
  }

  private fun Array<out JPanel>.countChecked() = this.count { it.getCheckBox().isSelected }

  private fun Array<out JPanel>.countEnabled() = this.count { it.getCheckBox().isEnabled }

  private suspend fun waitAndAssertSlotConfiguration(
    all: Int,
    enabled: Int,
    checked: Int,
    supportedTypes: Collection<ComplicationType>,
  ) {
    try {
      delayUntilCondition(100) {
        val currentSlotsPanel = slotsPanel
        val currentSlots = currentSlotsPanel.slots()
        val currentModel = currentSlotsPanel.getModel()
        currentSlots.size == all &&
          currentSlots.countEnabled() == enabled &&
          currentSlots.countChecked() == checked &&
          currentModel.allAvailableSlots.size == all &&
          currentModel.currentChosenSlots.size == checked &&
          currentModel.supportedTypes.containsAll(supportedTypes)
      }
    } catch (e: TimeoutCancellationException) {
      println("ERROR: didn't get expected slot configuration")
    }
    val currentSlotsPanel = slotsPanel
    val currentSlots = currentSlotsPanel.slots()
    val currentModel = currentSlotsPanel.getModel()
    assertThat(currentSlots.size).isEqualTo(all)
    assertThat(currentSlots.countEnabled()).isEqualTo(enabled)
    assertThat(currentSlots.countChecked()).isEqualTo(checked)
    assertThat(currentModel.allAvailableSlots.size).isEqualTo(all)
    assertThat(currentModel.currentChosenSlots.size).isEqualTo(checked)
    assertThat(currentModel.supportedTypes).containsExactlyElementsIn(supportedTypes)
  }

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
        "com.example.MyAllTypesComplication" to ComplicationType.entries.joinToString { it.name },
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
    Disposer.register(projectRule.testRootDisposable, settingsEditor)
    mockMergedManifest()

    // Don't delete. Is needed for [BaseRCSettingsConfigurable.isModified] be checked via
    // serialization.
    configurationConfigurable.apply()
    modulesComboBox.isEditable = true // To allow setting fake module names in the tests.
    runBlocking { configurationConfigurable.resetAndWait() }
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
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testResetFromEmptyConfiguration() = runBlocking {
    setModuleAndChooseAllTypesComplications()
    assertThat(runConfiguration.module).isNull()
    configurationConfigurable.resetAndWait()
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testResetWithMissingModule() = runBlocking {
    setModuleAndChooseAllTypesComplications()
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(null)
    configurationConfigurable.resetAndWait()
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testPMFlags() {
    runConfiguration.deployOptions.pmInstallFlags = ""
    val textField = TreeWalker(editor).descendants().filterIsInstance<JBTextField>()[0]
    textField.text = "Some Flags"
    assertThat(configurationConfigurable.isModified).isTrue()
    configurationConfigurable.apply()
    assertThat(runConfiguration.deployOptions.pmInstallFlags).isEqualTo("Some Flags")
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testResetFromConfigurationWithChosenSlots() = runBlocking {
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

    configurationConfigurable.resetAndWait()

    waitAndAssertSlotConfiguration(
      all = 1,
      enabled = 1,
      checked = 1,
      supportedTypes = listOf(ComplicationType.ICON),
    )
    val topSlot = getPanelForSlot(0)
    assertThat(topSlot.getCheckBox().isSelected).isTrue()
    assertThat(topSlot.getComboBox().selectedItem).isEqualTo(ComplicationType.ICON)
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testCleanupComplicationNameOnModuleChange() = runBlocking {
    setModuleAndChooseAllTypesComplications()
    componentComboBox.item = "com.example.MyLongShortTextComplication"

    configurationConfigurable.apply()
    assertThat(runConfiguration.componentLaunchOptions.componentName)
      .isEqualTo("com.example.MyLongShortTextComplication")

    modulesComboBox.selectedItem = null
    delayUntilCondition(100) { componentComboBox.item == null }
    configurationConfigurable.apply()
    assertThat(runConfiguration.componentLaunchOptions.componentName).isEqualTo(null)
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testFilterComponentTypes() = runBlocking {
    val watchInfo =
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
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo = watchInfo
    configurationConfigurable.resetAndWait()

    // region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    waitAndAssertSlotConfiguration(
      all = 2,
      enabled = 1,
      checked = 0,
      supportedTypes = listOf(ComplicationType.ICON),
    )

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat((getPanelForSlot(0).getComboBox().items)).isEmpty()

    // intersect between (SHORT_TEXT, ICON) and (ICON)
    assertThat((getPanelForSlot(1).getComboBox().items)).containsExactly(ComplicationType.ICON)
    // first available type is chosen.
    assertThat((getPanelForSlot(1).getComboBox().item)).isEqualTo(ComplicationType.ICON)
    // endregion MyIconComplication

    // region MyLongShortTextComplication
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    waitAndAssertSlotConfiguration(
      all = 2,
      enabled = 2,
      checked = 0,
      supportedTypes = listOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT),
    )

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat((getPanelForSlot(0).getComboBox().items))
      .containsExactly(ComplicationType.SHORT_TEXT)

    // intersect between (SHORT_TEXT, ICON) and (SHORT_TEXT, LONG_TEXT)
    assertThat((getPanelForSlot(1).getComboBox().items))
      .containsExactly(ComplicationType.SHORT_TEXT)
    // endregion MyLongShortTextComplication

    // region MyNoTypeComplication
    componentComboBox.item = "com.example.MyNoTypeComplication"
    waitAndAssertSlotConfiguration(all = 2, enabled = 0, checked = 0, supportedTypes = emptyList())

    // intersect between (SHORT_TEXT, RANGED_VALUE) and ()
    assertThat((getPanelForSlot(0).getComboBox().items)).isEmpty()
    assertThat((getPanelForSlot(0).getComboBox().isEnabled)).isFalse()

    // intersect between (SHORT_TEXT, ICON) and ()
    assertThat((getPanelForSlot(1).getComboBox().items)).isEmpty()
    // endregion MyNoTypeComplication
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun `test update slot type from invalid to first available`() = runBlocking {
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
    configurationConfigurable.resetAndWait()

    setModuleAndChooseAllTypesComplications()
    // Choose complication provider
    componentComboBox.item = "com.example.MyIconComplication"

    waitAndAssertSlotConfiguration(
      all = 2,
      enabled = 1,
      checked = 0,
      supportedTypes = listOf(ComplicationType.ICON),
    )

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(getPanelForSlot(0).getComboBox().items).isEmpty()

    // first available is ICON
    assertThat(getPanelForSlot(1).getComboBox().item).isEqualTo(ComplicationType.ICON)
    // Select the checkbox
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
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testNullType() = runBlocking {
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
    configurationConfigurable.resetAndWait()
    setModuleAndChooseAllTypesComplications()
    // Choose complication provider
    componentComboBox.item = "com.example.MyIconComplication"
    waitAndAssertSlotConfiguration(
      all = 1,
      enabled = 0,
      checked = 0,
      supportedTypes = listOf(ComplicationType.ICON),
    )

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(getPanelForSlot(0).getComboBox().items).isEmpty()
    val comboBoxRenderer =
      (getPanelForSlot(0).getComboBox().renderer as ListCellRenderer<ComplicationType>)
        .getListCellRendererComponent(
          JList(),
          getPanelForSlot(0).getComboBox().item as? ComplicationType,
          -1,
          false,
          false,
        ) as SimpleListCellRenderer<*>
    // Select the checkbox

    assertThat(getPanelForSlot(0).getCheckBox().isEnabled).isFalse()
    assertThat(comboBoxRenderer.text).isEqualTo("No type is supported by this slot")

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(0)
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testClearSlotsOnComplicationNameChange() = runBlocking {
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
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    configurationConfigurable.resetAndWait()

    // Add slot.
    getPanelForSlot(1).getCheckBox().isSelected = true
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))

    // Change name
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    // Assert that previously added slots are removed.
    waitAndAssertSlotConfiguration(
      all = 2,
      enabled = 2,
      checked = 0,
      supportedTypes = arrayListOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT),
    )
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testResetFromAndApplyTo() = runBlocking {
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.componentName =
      "com.example.MyLongShortTextComplication"
    runConfiguration.componentLaunchOptions.watchFaceInfo =
      object : ComplicationWatchFaceInfo {
        override val complicationSlots =
          listOf(
            ComplicationSlot(
              "Top",
              15,
              arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE),
            ),
            ComplicationSlot(
              "Right",
              17,
              arrayOf(
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.ICON,
                ComplicationType.LARGE_IMAGE,
              ),
            ),
          )
        override val apk = ""
        override val appId = ""
        override val watchFaceFQName = ""
      }
    runConfiguration.componentLaunchOptions.chosenSlots = emptyList()

    // reset
    configurationConfigurable.resetAndWait()
    waitAndAssertSlotConfiguration(
      all = 2,
      enabled = 2,
      checked = 0,
      supportedTypes = listOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT),
    )
    // assert that runConfiguration settings applied
    assertThat(modulesComboBox.item).isEqualTo(module)
    assertThat(componentComboBox.item).isEqualTo("com.example.MyLongShortTextComplication")

    // Add slot.
    getPanelForSlot(0).getCheckBox().isSelected = true
    getPanelForSlot(0).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))

    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(getPanelForSlot(0).getComboBox().items).containsExactly(ComplicationType.SHORT_TEXT)
    // intersect between (LONG_TEXT, SHORT_TEXT, ICON, LARGE_IMAGE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(getPanelForSlot(1).getComboBox().items)
      .containsExactly(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT)

    // Add slot.
    getPanelForSlot(1).getCheckBox().isSelected = true
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
    // runConfiguration.watchFaceInfo has only 2 available slots.
    val slotTypeComboBox2 = getPanelForSlot(1).getComboBox()
    // intersect between (LONG_TEXT, SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotTypeComboBox2.items)
      .containsExactly(ComplicationType.LONG_TEXT, ComplicationType.SHORT_TEXT)

    // Choose LONG_TEXT for slot with id 17.
    (slotTypeComboBox2 as ComboBox<ComplicationType>).item = ComplicationType.LONG_TEXT

    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 17 }!!.type)
      .isEqualTo(ComplicationType.LONG_TEXT)

    // Changing type.
    (slotTypeComboBox2 as ComboBox<ComplicationType>).item = ComplicationType.SHORT_TEXT
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 17 }!!.type)
      .isEqualTo(ComplicationType.SHORT_TEXT)

    // Uncheck the Right slot.
    getPanelForSlot(1).getCheckBox().isSelected = false
    getPanelForSlot(1).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().id).isEqualTo(15)
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testRestoreComponentName() = runBlocking {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)
    configurationConfigurable.resetAndWait()
    assertThat(componentComboBox.selectedItem).isEqualTo("com.example.MyIconComplication")
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testApkFound() = runBlocking {
    assertThat(
        Files.isRegularFile(Paths.get(runConfiguration.componentLaunchOptions.watchFaceInfo.apk))
      )
      .isTrue()
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun slotsAreDisabledWhenNoComponentIsSelected() = runBlocking {
    // the module and component are null, all the slots should be disabled
    assertThat(componentComboBox.item).isNull()
    waitAndAssertSlotConfiguration(all = 5, enabled = 0, checked = 0, supportedTypes = emptyList())

    // set the module component, all slots should be enabled
    setModuleAndChooseAllTypesComplications()

    // unset the component, the slots panel should be disabled
    modulesComboBox.item = null
    waitAndAssertSlotConfiguration(all = 5, enabled = 0, checked = 0, supportedTypes = emptyList())
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun selectedSlotsAreResetWhenNoComponentIsSelected() = runBlocking {
    setModuleAndChooseAllTypesComplications()
    val checkBox = getPanelForSlot(0).getCheckBox()
    checkBox.isSelected = true
    checkBox.actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))

    // unset the component, the selected slot should be deselected
    modulesComboBox.item = null
    waitAndAssertSlotConfiguration(all = 5, enabled = 0, checked = 0, supportedTypes = emptyList())
  }

  private suspend fun setModuleAndChooseAllTypesComplications() {
    if (componentComboBox.item == "com.example.MyAllTypesComplication") {
      return
    }
    modulesComboBox.item = module
    delayUntilCondition(200) { componentComboBox.item != null }
    if (componentComboBox.item != "com.example.MyAllTypesComplication") {
      componentComboBox.item = "com.example.MyAllTypesComplication"
    }
    val slotsTotal = runConfiguration.componentLaunchOptions.watchFaceInfo.complicationSlots.size
    waitAndAssertSlotConfiguration(
      all = slotsTotal,
      enabled = slotsTotal,
      checked = 0,
      supportedTypes = ComplicationType.entries.toSet(),
    )
  }

  @Test
  @IgnoreWithCondition(reason = "b/368132759", condition = OnWindows::class)
  fun testSlotsAreDisplayedInASingleConfigurableEditor() = runBlocking {
    enableHeadlessDialogs(fixture.testRootDisposable)
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

    fun getAvailableTypes(dialog: SingleConfigurableEditor): List<String> {
      val slotPanelDialog =
        FakeUi(dialog.contentPanel).findAllComponents(SlotsPanel::class.java).single().slotsUiPanel
      return FakeUi(slotPanelDialog).findAllComponents<JLabel>().map { it.text }
    }

    withContext(uiThread) {
      val dialog =
        object :
          SingleConfigurableEditor(
            projectRule.project,
            configurationConfigurable,
            null,
            IdeModalityType.IDE,
          ) {}

      delayUntilCondition(200) { withContext(uiThread) { getAvailableTypes(dialog) }.size == 2 }
      createModalDialogAndInteractWithIt({ dialog.show() }) {
        assertThat(getAvailableTypes(dialog)).containsExactly("Top", "Right")
      }
    }
  }

  private suspend fun SingleConfigurationConfigurable<*>.resetAndWait() {
    this.reset()
    delayUntilCondition(200) { componentComboBox.actionListeners.isNotEmpty() }
  }
}

private fun CodeInsightTestFixture.addComplicationServiceClass() = runBlocking {
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
