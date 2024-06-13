/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.ui

import com.android.AndroidXConstants.CLASS_APP_BAR_LAYOUT
import com.android.AndroidXConstants.CLASS_COLLAPSING_TOOLBAR_LAYOUT
import com.android.AndroidXConstants.CLASS_COORDINATOR_LAYOUT
import com.android.AndroidXConstants.CLASS_FLOATING_ACTION_BUTTON
import com.android.AndroidXConstants.CLASS_NESTED_SCROLL_VIEW
import com.android.AndroidXConstants.CLASS_TAB_ITEM
import com.android.AndroidXConstants.CLASS_TAB_LAYOUT
import com.android.AndroidXConstants.CLASS_TOOLBAR_V7
import com.intellij.openapi.project.Project
import org.jetbrains.android.refactoring.getNameInProject

fun formatNamespaces(namespaces: Map<String, String>): String {
  val result = StringBuilder()
  for (ns in namespaces.keys) {
    val prefix = namespaces[ns]
    result.append("    xmlns:$prefix=\"$ns\"\n")
  }
  return result.toString()
}

object Templates {
  // TODO: Remove the hardcoded AppBar height (192dp) and ID (appbar).
  @JvmStatic
  fun getCoordinatorLayout(
    project: Project,
    androidPrefix: String,
    autoPrefix: String,
    namespaceDeclarations: String,
    fitsSystemWindows: String,
    scrollFlags: String,
    scrollInterpolator: String,
    backgroundImage: String,
    behaviourOverlapTop: String,
    scrollYPosition: String,
    pageContentAsXml: String,
    fab: String,
  ) =
    """
    <${CLASS_COORDINATOR_LAYOUT.getNameInProject(project)}
      $namespaceDeclarations
      $fitsSystemWindows
      $androidPrefix:layout_width="match_parent"
      $androidPrefix:layout_height="match_parent">
      <${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}
          $androidPrefix:id="@+id/appbar"
          $fitsSystemWindows
          $androidPrefix:layout_height="192dp"
          $androidPrefix:layout_width="match_parent">
        <${CLASS_COLLAPSING_TOOLBAR_LAYOUT.getNameInProject(project)}
            $androidPrefix:layout_width="match_parent"
            $androidPrefix:layout_height="match_parent"
            $autoPrefix:toolbarId="@+id/toolbar"
            $autoPrefix:layout_scrollFlags="$scrollFlags"
            $scrollInterpolator
            $autoPrefix:contentScrim="?attr/colorPrimary">
          $backgroundImage

          <${CLASS_TOOLBAR_V7.getNameInProject(project)}
              $androidPrefix:id="@+id/toolbar"
              $androidPrefix:layout_height="?attr/actionBarSize"
              $androidPrefix:layout_width="match_parent">
          </${CLASS_TOOLBAR_V7.getNameInProject(project)}>
        </${CLASS_COLLAPSING_TOOLBAR_LAYOUT.getNameInProject(project)}>
      </${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}>
      <${CLASS_NESTED_SCROLL_VIEW.getNameInProject(project)}
          $androidPrefix:layout_width="match_parent"
          $androidPrefix:layout_height="match_parent"
          $behaviourOverlapTop
          $scrollYPosition
          $autoPrefix:layout_behavior="${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}${"$"}ScrollingViewBehavior">
          $pageContentAsXml

      </${CLASS_NESTED_SCROLL_VIEW.getNameInProject(project)}>
      $fab

    </${CLASS_COORDINATOR_LAYOUT.getNameInProject(project)}>
  """
      .trimIndent()

  @JvmStatic
  fun getCoordinatorLayoutWithTabs(
    project: Project,
    androidPrefix: String,
    autoPrefix: String,
    namespaceDeclarations: String,
    scrollFlags: String,
    tabItems: String,
    scrollYPosition: String,
    pageContentAsXml: String,
    fab: String,
  ) =
    """
    <${CLASS_COORDINATOR_LAYOUT.getNameInProject(project)}
      $namespaceDeclarations
      $androidPrefix:layout_width="match_parent"
      $androidPrefix:layout_height="match_parent">
      <${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}
          $androidPrefix:id="@+id/appbar"
          $androidPrefix:layout_height="wrap_content"
          $androidPrefix:layout_width="match_parent">
        <${CLASS_TOOLBAR_V7.getNameInProject(project)}
            $androidPrefix:layout_height="?attr/actionBarSize"
            $androidPrefix:layout_width="match_parent"
            $autoPrefix:layout_scrollFlags="scroll|enterAlways">
        </${CLASS_TOOLBAR_V7.getNameInProject(project)}>
        <${CLASS_TAB_LAYOUT.getNameInProject(project)}
            $androidPrefix:id="@+id/tabs"
            $androidPrefix:layout_width="match_parent"
            $androidPrefix:layout_height="wrap_content"
            $scrollFlags
            $autoPrefix:tabMode="scrollable">
        $tabItems
        </${CLASS_TAB_LAYOUT.getNameInProject(project)}>
      </${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}>
      <${CLASS_NESTED_SCROLL_VIEW.getNameInProject(project)}
          $androidPrefix:layout_width="match_parent"
          $androidPrefix:layout_height="match_parent"
          $scrollYPosition
          $autoPrefix:layout_behavior="${CLASS_APP_BAR_LAYOUT.getNameInProject(project)}${'$'}ScrollingViewBehavior">
        $pageContentAsXml
      </${CLASS_NESTED_SCROLL_VIEW.getNameInProject(project)}>
      $fab
    </${CLASS_COORDINATOR_LAYOUT.getNameInProject(project)}>
  """
      .trimIndent()

  @JvmStatic
  fun getTagFloatingActionButton(project: Project, androidPrefix: String, imageSrc: String) =
    """
    <${CLASS_FLOATING_ACTION_BUTTON.getNameInProject(project)}
        $androidPrefix:layout_height="wrap_content"
        $androidPrefix:layout_width="wrap_content"
        $androidPrefix:src="$imageSrc"
        $androidPrefix:layout_gravity="bottom|end"
        $androidPrefix:layout_margin="16dp"/>
    """
      .trimIndent()

  @JvmStatic
  fun getImageView(androidPrefix: String, collapseMode: String, imageSrc: String) =
    """
    <ImageView
        $androidPrefix:id="@+id/app_bar_image"
        $androidPrefix:layout_width="match_parent"
        $androidPrefix:layout_height="match_parent"
        $collapseMode
        $androidPrefix:src="$imageSrc"
        $androidPrefix:scaleType="centerCrop"/>
  """
      .trimIndent()

  @JvmStatic
  fun getTabItem(project: Project, androidPrefix: String, textAttribute: String) =
    """
    <${CLASS_TAB_ITEM.getNameInProject(project)}
      $androidPrefix:layout_height="wrap_content"
      $androidPrefix:layout_width="wrap_content"
      $androidPrefix:text="$textAttribute"/>
  """
      .trimIndent()

  @JvmStatic
  fun getTextView(androidPrefix: String, text: String) =
    """
    <TextView
      $androidPrefix:layout_height="match_parent"
      $androidPrefix:layout_width="wrap_content"
      $androidPrefix:text="$text"
      $androidPrefix:padding="16dp"/>
    """
      .trimIndent()
}
