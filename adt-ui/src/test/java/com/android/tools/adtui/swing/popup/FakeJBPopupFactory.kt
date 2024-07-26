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
package com.android.tools.adtui.swing.popup

import com.android.testutils.waitForCondition
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.openapi.ui.popup.TreePopupStep
import com.intellij.openapi.util.Condition
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.util.ui.Html
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.event.HyperlinkListener
import kotlin.time.Duration
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

/**
 * A fake implementation of [JBPopupFactory] for testing.
 *
 * This class is implemented ad hoc. All unused methods will throw a [NotImplementedError].
 *
 * This class keeps track of the popups that it creates. Popups can be created directly by this
 * class or indirectly via builders. A test can retrieve the popup it needs using the [getPopup],
 * [getNextPopup], [getBalloon] or [getNextBalloon] method. Type safety is the responsibility of
 * the caller.
 *
 * Note to contributors:
 * As methods are implemented, please move them towards the top of the file.
 */
class FakeJBPopupFactory(val disposable: Disposable) : JBPopupFactory() {
  private val popups = ArrayDeque<JBPopup>()
  private val balloons = ArrayDeque<FakeBalloon>()

  /**
   * Returns the number of popups created using this factory
   */
  val popupCount: Int
    get() = popups.size

  /**
   * Returns the number of balloons created using this factory
   */
  val balloonCount: Int
    get() = balloons.size

  /**
   * Returns a popup that has been created using this factory.
   *
   * Type safety is the responsibility of the caller.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> getPopup(i: Int): FakeJBPopup<T> = popups[i] as FakeJBPopup<T>

  /**
   * Returns the oldest popup that was created using this factory and removes it from the factory.
   *
   * Type safety is the responsibility of the caller.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T, U : FakeJBPopup<T>> getNextPopup(): U = popups.removeFirst() as U

  /**
   * Returns the oldest popup that was created using this factory and removes it from the factory.
   * If no popups have been created yet, waits for one to be created.
   *
   * Type safety is the responsibility of the caller.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T, U : FakeJBPopup<T>> getNextPopup(timeout: Duration): U {
    waitForCondition(timeout) { popups.isNotEmpty() }
    return popups.removeFirst() as U
  }

  /**
   * Returns the oldest popup that was created using this factory and removes it from the factory.
   * If no popups have been created yet, waits for one to be created.
   *
   * Type safety is the responsibility of the caller.
   */
  fun <T, U : FakeJBPopup<T>> getNextPopup(timeout: Long, timeUnit: TimeUnit): U =
      getNextPopup(timeout.toDuration(timeUnit.toDurationUnit()))

  /**
   * Returns a balloon that has been created using this factory.
   */
  fun getBalloon(i: Int): FakeBalloon = balloons[i]

  /**
   * Returns the oldest balloon that was created using this factory and removes it from the factory.
   */
  fun getNextBalloon(): FakeBalloon = balloons.removeFirst()

  internal fun <T> addPopup(popup: FakeJBPopup<T>) {
    popups.add(popup)
  }

  internal fun addBalloon(balloon: FakeBalloon) {
    balloons.add(balloon)
  }

  override fun <T> createPopupChooserBuilder(list: List<T>): IPopupChooserBuilder<T> =
    FakePopupChooserBuilder(this, list)

  override fun createActionGroupPopup(
    title: String?,
    actionGroup: ActionGroup,
    dataContext: DataContext,
    aid: ActionSelectionAid?,
    showDisabledActions: Boolean,
    disposeCallback: Runnable?,
    maxRowCount: Int,
    preselectCondition: Condition<in AnAction>?,
    actionPlace: String?)
    : ListPopup {
    val component: Component? = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val presentationFactory = PresentationFactory()
    val step = ActionPopupStep.createActionsStep(
      title, actionGroup, dataContext,
      actionPlace ?: ActionPlaces.POPUP, presentationFactory,
      getComponentContextSupplier(dataContext, component),
      ActionPopupOptions.forAid(aid, showDisabledActions, maxRowCount, preselectCondition))
    val popup = FakeListPopup(step)
    popups.add(popup)
    return popup
  }

  override fun createActionGroupPopup(title: String?,
                                      actionGroup: ActionGroup,
                                      dataContext: DataContext,
                                      showNumbers: Boolean,
                                      showDisabledActions: Boolean,
                                      honorActionMnemonics: Boolean,
                                      disposeCallback: Runnable?,
                                      maxRowCount: Int,
                                      preselectCondition: Condition<in AnAction>?): ListPopup =
    createActionGroupPopup(
      title,
      actionGroup,
      dataContext,
      /* aid= */ null,
      showDisabledActions,
      disposeCallback,
      maxRowCount,
      preselectCondition,
      /* actionPlace= */ null)

  override fun createListPopup(step: ListPopupStep<*>): ListPopup {
    val popup = FakeListPopup(step)
    popups.add(popup)
    return popup
  }

  override fun createListPopup(step: ListPopupStep<*>, maxRowCount: Int): ListPopup {
    return createListPopup(step)
  }

  override fun createListPopup(project: Project,
                               step: ListPopupStep<*>,
                               cellRendererProducer: Function<in ListCellRenderer<Any>, out ListCellRenderer<Any>>): ListPopup {
    return createListPopup(step)
  }

  override fun createComponentPopupBuilder(content: JComponent, preferableFocusComponent: JComponent?): ComponentPopupBuilder =
    FakeComponentPopupBuilder(this, content, preferableFocusComponent)

  override fun getChildPopups(parent: Component): MutableList<JBPopup> = popups

  override fun <T> createPopupComponentAdapter(
    builder: PopupChooserBuilder<T>,
    list: JList<T>
  ): PopupChooserBuilder.PopupComponentAdapter<T> = FakePopupListAdapter(builder, list)

  @Suppress("UnstableApiUsage")
  private fun getComponentContextSupplier(parentDataContext: DataContext,
                                          component: Component?): Supplier<DataContext> {
    if (component == null) return Supplier { parentDataContext }
    val dataContext = Utils.createAsyncDataContext(DataManager.getInstance().getDataContext(component))
    return Supplier { dataContext }
  }


  // PLEASE KEEP UNIMPLEMENTED METHODS ONLY BELLOW THIS COMMENT

  override fun createConfirmation(title: String?, onYes: Runnable?, defaultOptionIndex: Int): ListPopup {
    TODO("Not yet implemented")
  }

  override fun createConfirmation(title: String?, yesText: String?, noText: String?, onYes: Runnable?, defaultOptionIndex: Int): ListPopup {
    TODO("Not yet implemented")
  }

  override fun createConfirmation(title: String?,
                                  yesText: String?,
                                  noText: String?,
                                  onYes: Runnable?,
                                  onNo: Runnable?,
                                  defaultOptionIndex: Int): ListPopup {
    TODO("Not yet implemented")
  }

  override fun createActionsStep(actionGroup: ActionGroup,
                                 dataContext: DataContext,
                                 actionPlace: String?,
                                 showNumbers: Boolean,
                                 showDisabledActions: Boolean,
                                 title: String?,
                                 component: Component?,
                                 honorActionMnemonics: Boolean,
                                 defaultOptionIndex: Int,
                                 autoSelectionEnabled: Boolean): ListPopupStep<*> {
    TODO("Not yet implemented")
  }

  override fun guessBestPopupLocation(component: JComponent): RelativePoint =
    RelativePoint(component, Point(0, 0))

  override fun guessBestPopupLocation(dataContext: DataContext): RelativePoint =
    guessBestPopupLocation(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) as JComponent)

  override fun guessBestPopupLocation(editor: Editor): RelativePoint {
    TODO("Not yet implemented")
  }

  override fun createTree(parent: JBPopup?, step: TreePopupStep<*>, parentValue: Any?): TreePopup {
    TODO("Not yet implemented")
  }

  override fun createTree(step: TreePopupStep<*>): TreePopup {
    TODO("Not yet implemented")
  }

  override fun isBestPopupLocationVisible(editor: Editor): Boolean {
    TODO("Not yet implemented")
  }

  override fun getCenterOf(container: JComponent?, content: JComponent?): Point {
    TODO("Not yet implemented")
  }

  override fun isPopupActive(): Boolean {
    TODO("Not yet implemented")
  }

  override fun createBalloonBuilder(content: JComponent): BalloonBuilder =
    FakeBalloonBuilder(this, content)

  override fun createDialogBalloonBuilder(content: JComponent, title: String?): BalloonBuilder {
    TODO("Not yet implemented")
  }

  override fun createHtmlTextBalloonBuilder(htmlContent: String,
                                            icon: Icon?,
                                            textColor: Color?,
                                            fillColor: Color?,
                                            listener: HyperlinkListener?): BalloonBuilder =
    FakeBalloonBuilder(this, htmlContent = htmlContent)

  override fun createHtmlTextBalloonBuilder(html: Html,
                                            icon: Icon?,
                                            textColor: Color?,
                                            fillColor: Color?,
                                            listener: HyperlinkListener?): BalloonBuilder {
    TODO("Not yet implemented")
  }

  override fun createHtmlTextBalloonBuilder(htmlContent: String, messageType: MessageType?, listener: HyperlinkListener?): BalloonBuilder {
    TODO("Not yet implemented")
  }

  override fun createMessage(text: String?): JBPopup {
    TODO("Not yet implemented")
  }

  override fun getParentBalloonFor(c: Component?): Balloon? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> createPopupComponentAdapter(builder: PopupChooserBuilder<T>,
                                                      tree: JTree): PopupChooserBuilder.PopupComponentAdapter<T> {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> createPopupComponentAdapter(builder: PopupChooserBuilder<T>,
                                                      table: JTable): PopupChooserBuilder.PopupComponentAdapter<T> {
    TODO("Not yet implemented")
  }
}
