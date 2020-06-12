// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet

import com.intellij.facet.ui.FacetEditorTab
import com.intellij.ui.layout.*
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * This class is to show single line of text to a user, saying that the project is imported, therefore facet editing is not allowed.
 * Otherwise users are confused by empty configuration tab for Android facet (i.e. [AndroidFacet])
 */
class NotEditableAndroidFacetEditorTab : FacetEditorTab() {
  override fun isModified() = false

  @Nls
  override fun getDisplayName() = AndroidBundle.message("configurable.AndroidFacetEditorTab.display.name")

  override fun createComponent(): JComponent = panel {
    commentRow(AndroidBundle.message("android.facet.not.editable.text"))
  }
}