package com.android.build.attribution.ui.view

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.StatusText
import java.awt.Rectangle

fun StatusText.toStringState(): String {
  val pointBelow = pointBelow
  val calculatedSize = preferredSize
  val bounds = Rectangle(pointBelow.x, pointBelow.y - calculatedSize.height, calculatedSize.width, calculatedSize.height)
  val rows = wrappedFragmentsIterable
    .map { it as SimpleColoredComponent }
    .distinct() // There is a bug in 'wrappedFragmentsIterable' that it twice lists the content of first columns.
    .map {
      val size = it.preferredSize
      "${it.getCharSequence(true)}| width=${size.width} height=${size.height}"
    }
  return buildString {
    appendLine(bounds)
    rows.forEach { appendLine(it) }
  }.trimEnd()
}