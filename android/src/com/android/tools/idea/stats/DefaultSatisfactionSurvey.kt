package com.android.tools.idea.stats

import com.android.tools.idea.serverflags.protos.Option
import com.android.tools.idea.serverflags.protos.Survey

val DEFAULT_SATISFACTION_SURVEY: Survey = Survey.newBuilder().apply {
  title = "Android Studio Feedback"
  question = "Overall, how satisfied are you with this product?"
  intervalDays = 365
  answerCount = 1
  addOptions(Option.newBuilder().apply {
    iconPath = "/studio/icons/shell/telemetry/sentiment-very-satisfied.svg"
    label = "Very satisfied"
  })
  addOptions(Option.newBuilder().apply {
    iconPath = "/studio/icons/shell/telemetry/sentiment-satisfied.svg"
    label = "Somewhat satisfied"
  })
  addOptions(Option.newBuilder().apply {
    iconPath = "/studio/icons/shell/telemetry/sentiment-neutral.svg"
    label = "Neither satisfied or dissatisfied"
  })
  addOptions(Option.newBuilder().apply {
    iconPath = "/studio/icons/shell/telemetry/sentiment-dissatisfied.svg"
    label = "Somewhat dissatisfied"
  })
  addOptions(Option.newBuilder().apply {
    iconPath = "/studio/icons/shell/telemetry/sentiment-very-dissatisfied.svg"
    label = "Very dissatisfied"
  })
  name = "Default Satisfaction Survey"
}.build()