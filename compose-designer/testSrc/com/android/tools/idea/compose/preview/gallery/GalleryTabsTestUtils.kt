package com.android.tools.idea.compose.preview.gallery

import com.android.tools.adtui.TreeWalker
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import java.awt.Component
import java.util.stream.Collectors

fun findTabs(parent: Component) = findToolbar(parent, "Gallery Tabs")

private fun findToolbar(parent: Component, place: String): ActionToolbarImpl =
  TreeWalker(parent)
    .descendantStream()
    .filter { it is ActionToolbarImpl }
    .collect(Collectors.toList())
    .map { it as ActionToolbarImpl }
    .first { it.place == place }
