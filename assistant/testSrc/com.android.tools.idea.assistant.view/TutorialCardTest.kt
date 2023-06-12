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
package com.android.tools.idea.assistant.view

import com.android.tools.idea.assistant.TutorialCardRefreshNotifier
import com.android.tools.idea.assistant.datamodel.AnalyticsProvider
import com.android.tools.idea.assistant.datamodel.FeatureData
import com.android.tools.idea.assistant.datamodel.StepData
import com.android.tools.idea.assistant.datamodel.StepElementData
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.android.tools.idea.assistant.datamodel.TutorialData
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.components.JBScrollPane
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border

internal class TutorialCardTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `null scroll pane is not expected`() {
    val card = createTestTutorialCard()

    assertThat(card.isViewportExpectedLayout(null)).isFalse()
  }

  @Test
  fun `empty viewport is not expected`() {
    val card = createTestTutorialCard()
    var scrollPane = JBScrollPane()

    assertThat(card.isViewportExpectedLayout(scrollPane)).isFalse()
  }

  @Test
  fun `first component in viewport should be a JPanel`() {
    val card = createTestTutorialCard()
    var scrollPane = JBScrollPane()
    scrollPane.viewport.add(JLabel())

    assertThat(card.isViewportExpectedLayout(scrollPane)).isFalse()
  }

  @Test
  fun `expected viewport is not null and first component is a JPanel`() {
    val card = createTestTutorialCard()
    var scrollPane = JBScrollPane()
    scrollPane.viewport.add(JPanel())
    card.add(scrollPane)

    assertThat(card.isViewportExpectedLayout(scrollPane)).isTrue()
  }

  @Test
  fun `only tutorial cards return correct scroll position`() {
    val card = createTestTutorialCard()

    // Given a tutorialCard with an expected layout
    var scrollPane = JBScrollPane()
    val innerJPanel = JPanel()
    val tutorialCardCount = 2
    addTutorialSteps(innerJPanel, tutorialCardCount)
    scrollPane.viewport.add(innerJPanel, tutorialCardCount)

    // When getting the last tutorialCard scroll position
    val scrollbarPositon = card.getVerticalScrollbarPosition(tutorialCardCount, scrollPane)

    // Then the position is set to the last tutorialCards scroll position
    assertThat(scrollbarPositon).isEqualTo(2)
  }


  @Test
  fun `mix of components return appropriate tutorial card scroll position`() {
    // Given tutorialCard UI setup with a JPanel and tutorial cards
    val card = createTestTutorialCard()
    var scrollPane = JBScrollPane()
    val innerJPanel = JPanel()
    val tutorialCardCount = 2
    innerJPanel.add(JPanel())
    addTutorialSteps(innerJPanel, tutorialCardCount)
    scrollPane.viewport.add(innerJPanel, tutorialCardCount)
    innerJPanel.add(JPanel())

    // When getting the last tutorialCard scroll position
    val scrollbarPosition = card.getVerticalScrollbarPosition(tutorialCardCount, scrollPane)

    // Then the position is set to the last tutorialCards scroll position
    assertThat(scrollbarPosition).isEqualTo(2)
  }

  @Test
  fun `tutorial step that doesn't exist returns zero for scroll position`() {
    val card = createTestTutorialCard()

    // Given tutorialCard UI setup
    var scrollPane = JBScrollPane()
    val innerJPanel = JPanel()
    val tutorialCardCount = 2
    innerJPanel.add(JPanel())
    addTutorialSteps(innerJPanel, tutorialCardCount)
    scrollPane.viewport.add(innerJPanel, tutorialCardCount)

    // When getting the last tutorialCard scroll position
    val scrollbarPosition = card.getVerticalScrollbarPosition(tutorialCardCount + 1, scrollPane)

    // Then the position is set to the last tutorialCards scroll position
    assertThat(scrollbarPosition).isEqualTo(0)
  }


  private fun addTutorialSteps(innerJPanel: JPanel, tutorialCardCount: Int) {
    for (i in 0..tutorialCardCount) {
      val step = addTutorialStep(i)
      innerJPanel.add(step)
    }
  }

  // using height here and setting the bounds to it to have an easier way to do validation without rendering the whole UI
  private fun addTutorialStep(yIndex: Int): TutorialStep {
    val listener = TestActionListener()
    val step = TutorialStep(TestStepData(), yIndex, listener, projectRule.project, true, false, TutorialStep::class.java)
    // only y bounds matter for scroll wheel setting
    step.setBounds(0, yIndex, 0, 0)
    return step
  }


  @Test
  fun `tutorial card grabs tutorial steps from its bundle`() {
    // Given the bundle to make a tutorial card
    var testTutorialData = TestTutorialData()
    var testTutorialBundleData = TestTutorialBundleData()
    var myFeaturesPanel = FeaturesPanel(testTutorialBundleData, projectRule.project, TestAnalyticsProvider(), null)

    // When creating a tutorial card
    TutorialCard(myFeaturesPanel, testTutorialData, TestFeatureData(), true, testTutorialBundleData)
    // Then
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(1)
  }

  @Test
  fun `tutorial card redraws when receiving a message on refresh topic`() {
    // Given a tutorial card
    var testTutorialData = TestTutorialData()
    var testTutorialBundleData = TestTutorialBundleData()
    var myFeaturesPanel = FeaturesPanel(testTutorialBundleData, projectRule.project, TestAnalyticsProvider(), null)
    val card = TutorialCard(myFeaturesPanel, testTutorialData, TestFeatureData(), true, testTutorialBundleData)
    // normally called by swing internals during setup
    card.addNotify()
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(1)

    // When a message is sent on the refresh tutorial card topic
    sendMessage()

    // Then internal counter is 2
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(2)
  }

  @Test
  fun `tutorial card that is not subscribed does not receive messages`() {
    // Given a tutorial card that is not notified of messages
    var testTutorialData = TestTutorialData()
    var testTutorialBundleData = TestTutorialBundleData()
    var myFeaturesPanel = FeaturesPanel(testTutorialBundleData, projectRule.project, TestAnalyticsProvider(), null)
    val card = TutorialCard(myFeaturesPanel, testTutorialData, TestFeatureData(), true, testTutorialBundleData)
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(1)
    // called on close by swing internals
    card.removeNotify()

    // when a message is sent
    sendMessage()

    // Then the internal counter is still 1
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(1)
  }

  @Test
  fun `tutorial card can resubscribe and receive events`() {
    // Given a tutorial card that is not notified of messages
    var testTutorialData = TestTutorialData()
    var testTutorialBundleData = TestTutorialBundleData()
    var myFeaturesPanel = FeaturesPanel(testTutorialBundleData, projectRule.project, TestAnalyticsProvider(), null)
    val card = TutorialCard(myFeaturesPanel, testTutorialData, TestFeatureData(), true, testTutorialBundleData)

    card.addNotify()
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(1)

    sendMessage()
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(2)

    card.removeNotify()
    sendMessage()
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(2)

    card.addNotify()
    sendMessage()
    assertThat(testTutorialData.myGetStepCounter).isEqualTo(3)
  }

  private fun sendMessage(): Unit = projectRule.project.messageBus.syncPublisher(
    TutorialCardRefreshNotifier.TUTORIAL_CARD_TOPIC).stateUpdated(1)

  private fun createTestTutorialCard(): TutorialCard {
    var testTutorialData = TestTutorialData()
    var testTutorialBundleData = TestTutorialBundleData()
    var myFeaturesPanel = FeaturesPanel(testTutorialBundleData, projectRule.project, TestAnalyticsProvider(), null)

    return TutorialCard(myFeaturesPanel, testTutorialData, TestFeatureData(), true, testTutorialBundleData)
  }

  class TestActionListener : ActionListener {
    override fun actionPerformed(e: ActionEvent) {
    }
  }

  // Classes required for running tests
  class TestAnalyticsProvider : AnalyticsProvider

  class TestTutorialBundleData : TutorialBundleData {
    override fun setResourceClass(clazz: Class<*>) {}
    override fun getName(): String = "myTutorialBundleDataName"
    override fun getIcon(): Icon? = null
    override fun getLogo(): Icon? = null
    override fun getFeatures(): List<FeatureData> {
      return listOf()
    }

    override fun getWelcome(): String = "TutorialBundleDataWelcome"
    override fun getBundleCreatorId(): String = "myBundleCreatorId"
    override fun setBundleCreatorId(bundleCreatorId: String) {
      this.bundleCreatorId = bundleCreatorId
    }

    override fun isStepByStep(): Boolean = false
    override fun hideStepIndex(): Boolean = false
  }


  class TestTutorialData : TutorialData {
    // using a stepCounter to keep track of calls to getSteps() easily
    var myGetStepCounter = 0
    override fun getLabel(): String = "MyTutorialDataLabel"
    override fun getDescription(): String = "MyTutorialData"
    override fun getKey(): String {
      return "myTutorialDataKey"
    }

    override fun getSteps(): List<StepData?> {
      myGetStepCounter++
      return listOf(TestStepData(), TestStepData())
    }

    override fun hasLocalHTMLPaths() = false
    override fun shouldLoadLazily() = false
    override fun getResourceClass(): Class<*> {
      return TutorialData::class.java
    }

    override fun getRemoteLink(): String? = null
    override fun getRemoteLinkLabel(): String? = null
    override fun getIcon(): Icon? = null
  }

  class TestFeatureData : FeatureData {
    override fun getName(): String = "myFeatureDataName"
    override fun getIcon(): Icon? = null
    override fun getDescription(): String = "myFeatureDataDescription"
    override fun getTutorials(): List<TutorialData> = listOf()
    override fun setResourceClass(clazz: Class<*>) {
      this.setResourceClass(clazz)
    }
  }

  class TestStepData : StepData {
    override fun getStepElements(): MutableList<StepElementData> {
      return mutableListOf()
    }

    override fun getLabel(): String = "StepDataLabel"

    override fun getBorder(): Border = BorderFactory.createEmptyBorder()

  }

}