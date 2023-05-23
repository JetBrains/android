package com.android.build.attribution.ui.view

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.StatusText

fun StatusText.rowCenterY(row: Int): Int {
  val pointBelow = pointBelow
  val calculatedSize = preferredSize
  val yTop =  pointBelow.y - calculatedSize.height
  val lines = wrappedFragmentsIterable.map { it as SimpleColoredComponent }
  // This constant is private in com.intellij.util.ui.StatusText
  val yGap = 2
  val sumHeightRowsAbove = lines.take(row).sumOf { it.preferredSize.height }
  val gapsSum = yGap * row
  val halfRowHeight = lines[row].preferredSize.height / 2
  return yTop + sumHeightRowsAbove + gapsSum + halfRowHeight
}