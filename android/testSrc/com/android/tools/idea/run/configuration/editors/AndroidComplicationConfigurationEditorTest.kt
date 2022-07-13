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
import com.android.tools.deployer.model.component.Complication.ComplicationType
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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer


class AndroidComplicationConfigurationEditorTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val module get() = projectRule.module

  private lateinit var manifestSnapshot: MergedManifestSnapshot
  private lateinit var runConfiguration: AndroidComplicationConfiguration
  private lateinit var configurationConfigurable: SingleConfigurationConfigurable<AndroidComplicationConfiguration>
  private lateinit var settingsEditor: AndroidComplicationConfigurationEditor
  private val editor get() = settingsEditor.component as DialogPanel

  //region editor-utils
  private val componentComboBox get() = TreeWalker(editor).descendants().filterIsInstance<ComboBox<String>>()[1]
  private val modulesComboBox get() = TreeWalker(editor).descendants().filterIsInstance<ModulesComboBox>().first()
  private val addButton get() = TreeWalker(editor).descendants().filterIsInstance<ActionLink>().first()
  private val slotsPanel get() = editor.components.firstIsInstance<JPanel>()

  private val <T> ComboBox<T>.items get() = (0 until itemCount).map { getItemAt(it) }
  private fun JPanel.getIdComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(1) as ComboBox<*>
  private fun JPanel.getTypeComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(3) as ComboBox<*>
  private fun JPanel.getDeleteButtonForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(4) as JButton
  //endregion editor-utils

  @Before
  fun setUp() {
    manifestSnapshot = TestMergedManifestSnapshotBuilder.builder(projectRule.module).build()
    fixture.addComplicationServiceClass()

    //List of FQ Complication names added and their supported types as String in manifest.
    val complicationsInProject = mapOf(
      "com.example.MyIconComplication" to "ICON",
      "com.example.MyLongShortTextComplication" to "LONG_TEXT, SHORT_TEXT",
      "com.example.MyNoTypeComplication" to "",
    )

    complicationsInProject.forEach(addComplicationToProjectAndManifest())

    val runConfigurationFactory = AndroidComplicationConfigurationType().configurationFactories[0]
    val runManager = RunManagerImpl.getInstanceImpl(projectRule.project)
    runConfiguration = AndroidComplicationConfiguration(projectRule.project, runConfigurationFactory)

    val settings = RunnerAndConfigurationSettingsImpl(runManager, runConfiguration)
    configurationConfigurable = SingleConfigurationConfigurable.editSettings(settings, null)

    settingsEditor = (configurationConfigurable.editor as ConfigurationSettingsEditorWrapper)
      .selectTabAndGetEditor(AndroidComplicationConfigurationEditor::class.java)
    mockMergedManifest()

    // Don't delete. Is needed for [BaseRCSettingsConfigurable.isModified] be checked via serialization.
    configurationConfigurable.apply()
  }

  @After
  fun tearDown() {
    configurationConfigurable.disposeUIResources()
  }

  private fun addComplicationToProjectAndManifest() = { name: String, supportedTypes: String ->
    fixture.addComplication(name)
    val newServiceInManifest = getServiceDomElement(name, supportedTypes)
    manifestSnapshot = TestMergedManifestSnapshotBuilder
      .builder(module)
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
    val supplier = object : AsyncSupplier<MergedManifestSnapshot> {
      override val now get() = manifestSnapshot
      override fun get(): ListenableFuture<MergedManifestSnapshot> = immediateFuture(manifestSnapshot)
    }
    val mockMergedManifestManager = Mockito.mock(MergedManifestManager::class.java)
    whenever(mockMergedManifestManager.mergedManifest).thenReturn(supplier)
    module.replaceService(MergedManifestManager::class.java, mockMergedManifestManager, projectRule.project)
  }

  @Test
  fun testResetFromEmptyConfiguration() {
    assertThat(runConfiguration.module).isNull()
    configurationConfigurable.reset()
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
  fun testResetFromConfigurationWithChosenSlots() {
    runConfiguration.componentLaunchOptions.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          3,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON)
        )
      )
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }
    runConfiguration.componentLaunchOptions.chosenSlots = listOf(AndroidComplicationConfiguration.ChosenSlot(3, ComplicationType.ICON))
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)

    configurationConfigurable.reset()
    assertThat(slotsPanel.components).hasLength(1)
    assertThat(slotsPanel.getIdComboBoxForSlot(0).item).isEqualTo(3)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).item).isEqualTo(ComplicationType.ICON)
  }

  @Test
  fun testCleanupComplicationNameOnModuleChange() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)
    configurationConfigurable.reset()

    assertThat(componentComboBox.item).isEqualTo("com.example.MyIconComplication")

    modulesComboBox.selectedItem = null
    assertThat(componentComboBox.item).isEqualTo(null)
    configurationConfigurable.apply()
    assertThat(runConfiguration.componentLaunchOptions.componentName).isEqualTo(null)
    assertThat(addButton.isEnabled).isFalse()

    modulesComboBox.selectedItem = module
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    configurationConfigurable.apply()

    assertThat(runConfiguration.componentLaunchOptions.componentName).isEqualTo("com.example.MyLongShortTextComplication")
    assertThat(addButton.isEnabled).isTrue()
  }

  @Test
  fun testFilterComponentTypes() {
    runConfiguration.componentLaunchOptions.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          0,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)
        ),
        ComplicationSlot(
          "Right",
          2,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON)
        ))
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }
    configurationConfigurable.reset()
    modulesComboBox.item = module
    editor.apply()
    //region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    assertThat(slotsPanel.components).isEmpty()
    addButton.doClick()

    slotsPanel.getIdComboBoxForSlot(0).item = 0
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).isEmpty()

    slotsPanel.getIdComboBoxForSlot(0).item = 2
    // intersect between (SHORT_TEXT, ICON) and (ICON)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).containsExactly(ComplicationType.ICON)
    // first available type is chosen.
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).item).isEqualTo(ComplicationType.ICON)
    //endregion MyIconComplication

    //region MyLongShortTextComplication
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    editor.apply()
    assertThat(slotsPanel.components).isEmpty()
    addButton.doClick()

    slotsPanel.getIdComboBoxForSlot(0).item = 0
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).containsExactly(ComplicationType.SHORT_TEXT)

    slotsPanel.getIdComboBoxForSlot(0).item = 2
    // intersect between (SHORT_TEXT, ICON) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).containsExactly(ComplicationType.SHORT_TEXT)
    //endregion MyLongShortTextComplication

    //region MyNoTypeComplication
    componentComboBox.item = "com.example.MyNoTypeComplication"
    editor.apply()
    assertThat(slotsPanel.components).isEmpty()
    addButton.doClick()

    slotsPanel.getIdComboBoxForSlot(0).item = 0
    // intersect between (SHORT_TEXT, RANGED_VALUE) and ()
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).isEmpty()
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).isEnabled).isFalse()

    slotsPanel.getIdComboBoxForSlot(0).item = 2
    // intersect between (SHORT_TEXT, ICON) and ()
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).isEmpty()
    //endregion MyIconComplication
  }

  @Test
  fun `test update slot type from invalid to first available`() {
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          0,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)
        ),
        ComplicationSlot(
          "Right",
          2,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.ICON)
        ))
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }

    configurationConfigurable.reset()
    modulesComboBox.item = module
    editor.apply()
    //region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    assertThat(slotsPanel.components).isEmpty()
    addButton.doClick()

    slotsPanel.getIdComboBoxForSlot(0).item = 0
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).isEmpty()

    slotsPanel.getIdComboBoxForSlot(0).item = 2
    // first available is ICON
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).item).isEqualTo(ComplicationType.ICON)
    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    // save first available for configuration
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().type).isEqualTo(ComplicationType.ICON)
  }

  @Test
  fun testNullType() {
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          0,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)
        ))
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }

    configurationConfigurable.reset()
    modulesComboBox.item = module
    editor.apply()
    //region MyIconComplication
    componentComboBox.item = "com.example.MyIconComplication"
    editor.apply()
    assertThat(slotsPanel.components).isEmpty()
    addButton.doClick()

    slotsPanel.getIdComboBoxForSlot(0).item = 0
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (ICON)
    assertThat(slotsPanel.getTypeComboBoxForSlot(0).items).isEmpty()
    val comboBoxRenderer = (slotsPanel.getTypeComboBoxForSlot(0).renderer as ListCellRenderer<ComplicationType>)
      .getListCellRendererComponent(JList(), slotsPanel.getTypeComboBoxForSlot(0).item as? ComplicationType, -1, false, false)
      as SimpleListCellRenderer<*>

    assertThat(comboBoxRenderer.text).isEqualTo("Source doesn't provide types supported by slot")

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    // save first available for configuration
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().type).isEqualTo(null)
  }

  @Test
  fun testClearSlotsOnComplicationNameChange() {
    modulesComboBox.selectedItem = module

    componentComboBox.item = "com.example.MyIconComplication"
    assertThat(slotsPanel.components).isEmpty()

    // Add slot.
    addButton.doClick()
    assertThat(slotsPanel.components).hasLength(1)

    //Change name
    componentComboBox.item = "com.example.MyLongShortTextComplication"
    editor.apply()
    //Assert that previously added slots are removed.
    assertThat(slotsPanel.components).isEmpty()
  }

  @Test
  fun testResetFromAndApplyTo() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyLongShortTextComplication"
    runConfiguration.setModule(module)
    runConfiguration.componentLaunchOptions.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          0,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)
        ),
        ComplicationSlot(
          "Right",
          2,
          arrayOf(ComplicationType.LONG_TEXT, ComplicationType.SHORT_TEXT, ComplicationType.ICON)
        ))
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }

    configurationConfigurable.reset()
    assertThat(modulesComboBox.item).isEqualTo(module)

    // runConfiguration has available slots, add button should become enabled.
    assertThat(addButton.isEnabled).isTrue()
    // runConfiguration doesn't have chosen components.
    assertThat(slotsPanel.components).isEmpty()

    // Add slot.
    addButton.doClick()
    assertThat(addButton.isEnabled).isTrue()
    assertThat(slotsPanel.components).hasLength(1)

    var slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    assertThat(slotIdComboBox1.items).containsExactly(0, 2)
    assertThat(slotIdComboBox1.item).isEqualTo(0)

    // Selecting between items should not change the list of available items
    slotIdComboBox1.item = 2
    slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    assertThat(slotIdComboBox1.items).containsExactly(0, 2)

    slotIdComboBox1.item = 0
    slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    assertThat(slotIdComboBox1.items).containsExactly(0, 2)

    val slotTypeComboBox1 = slotsPanel.getTypeComboBoxForSlot(0)
    // intersect between (SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotTypeComboBox1.items).containsExactly(ComplicationType.SHORT_TEXT)

    // Add slot.
    addButton.doClick()
    // runConfiguration.watchFaceInfo has only 2 available slots.
    assertThat(addButton.isEnabled).isFalse()
    assertThat(slotsPanel.components).hasLength(2)

    slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    // After we added second slot, only one option is available in the first slot.
    assertThat(slotIdComboBox1.items).containsExactly(0)

    val slotIdComboBox2 = slotsPanel.getIdComboBoxForSlot(1)
    assertThat(slotIdComboBox2.items).containsExactly(2)
    assertThat(slotIdComboBox2.item).isEqualTo(2)

    val slotTypeComboBox2 = slotsPanel.getTypeComboBoxForSlot(1)
    // intersect between (LONG_TEXT, SHORT_TEXT, RANGED_VALUE) and (SHORT_TEXT, LONG_TEXT)
    assertThat(slotTypeComboBox2.items).containsExactly(ComplicationType.LONG_TEXT, ComplicationType.SHORT_TEXT)
    // Choose LONG_TEXT for slot with id 2.
    slotTypeComboBox2.item = ComplicationType.LONG_TEXT
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 2 }!!.type).isEqualTo(ComplicationType.LONG_TEXT)

    //Changing type.
    slotsPanel.getTypeComboBoxForSlot(1).item = ComplicationType.SHORT_TEXT
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(2)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.find { it.id == 2 }!!.type).isEqualTo(ComplicationType.SHORT_TEXT)

    //Delete slot with id 2.
    slotsPanel.getDeleteButtonForSlot(1).doClick()
    assertThat(slotsPanel.components).hasLength(1)
    assertThat(configurationConfigurable.isModified).isTrue()

    // Saving configuration.
    configurationConfigurable.apply()
    assertThat(configurationConfigurable.isModified).isFalse()

    assertThat(runConfiguration.componentLaunchOptions.chosenSlots).hasSize(1)
    assertThat(runConfiguration.componentLaunchOptions.chosenSlots.single().id).isEqualTo(0)
  }

  @Test
  fun testRestoreComponentName() {
    runConfiguration.componentLaunchOptions.componentName = "com.example.MyIconComplication"
    runConfiguration.setModule(module)

    configurationConfigurable.reset()
    assertThat(componentComboBox.selectedItem).isEqualTo("com.example.MyIconComplication")
  }

  fun testApkFound() {
    assertThat(Files.isRegularFile(Paths.get(runConfiguration.componentLaunchOptions.watchFaceInfo.apk))).isTrue()
  }
}

private fun JavaCodeInsightTestFixture.addComplicationServiceClass() {
  addFileToProject(
    "src/lib/ComplicationDataSourceService.kt",
    """
      package androidx.wear.watchface.complications.datasource

      open class ComplicationDataSourceService
    """.trimIndent())
}

private fun JavaCodeInsightTestFixture.addComplication(complicationFqName: String) {
  addClass(
    """
    package ${complicationFqName.substringBeforeLast(".")}

    public class ${
      complicationFqName.substringAfterLast(".")
    } extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
  """.trimIndent())
}