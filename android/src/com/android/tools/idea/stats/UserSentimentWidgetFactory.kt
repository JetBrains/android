package com.android.tools.idea.stats

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

enum class SentimentType(val description: String) {
  POSITIVE("Positive"),
  NEGATIVE("Negative")
}

abstract class UserSentimentWidgetFactory(private val sentimentType: SentimentType)
  : StatusBarWidgetFactory {

  override fun getId() = "UserSentimentPanel${sentimentType.description}"
  override fun getDisplayName() = "User Sentiment ($sentimentType.description)"
  override fun isAvailable(project: Project) = true
  override fun createWidget(project: Project): StatusBarWidget = UserSentimentPanel(project, sentimentType == SentimentType.POSITIVE)
  override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
  override fun canBeEnabledOn(statusBar: StatusBar) = true
}

class PositiveUserSentimentWidgetFactory : UserSentimentWidgetFactory(SentimentType.POSITIVE)
class NegativeUserSentimentWidgetFactory : UserSentimentWidgetFactory(SentimentType.NEGATIVE)