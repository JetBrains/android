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
  fun getCoordinatorLayout(androidPrefix: String,
                           autoPrefix: String,
                           namespaceDeclarations: String,
                           fitsSystemWindows: String,
                           scrollFlags: String,
                           scrollInterpolator: String,
                           backgroundImage: String,
                           behaviourOverlapTop: String,
                           scrollYPosition: String,
                           pageContentAsXml: String,
                           fab: String) = """
    <android.support.design.widget.CoordinatorLayout
      $namespaceDeclarations
      $fitsSystemWindows
      $androidPrefix:layout_width="match_parent"
      $androidPrefix:layout_height="match_parent">
      <android.support.design.widget.AppBarLayout
          $androidPrefix:id="@+id/appbar"
          $fitsSystemWindows

          $androidPrefix:layout_height="192dp"
          $androidPrefix:layout_width="match_parent">
        <android.support.design.widget.CollapsingToolbarLayout
            $androidPrefix:layout_width="match_parent"
            $androidPrefix:layout_height="match_parent"
            $autoPrefix:toolbarId="@+id/toolbar"
            $autoPrefix:layout_scrollFlags="$scrollFlags"
            $scrollInterpolator
            $autoPrefix:contentScrim="?attr/colorPrimary">
          $backgroundImage

          <android.support.v7.widget.Toolbar
              $androidPrefix:id="@+id/toolbar"
              $androidPrefix:layout_height="?attr/actionBarSize"
              $androidPrefix:layout_width="match_parent">
          </android.support.v7.widget.Toolbar>
        </android.support.design.widget.CollapsingToolbarLayout>
      </android.support.design.widget.AppBarLayout>
      <android.support.v4.widget.NestedScrollView
          $androidPrefix:layout_width="match_parent"
          $androidPrefix:layout_height="match_parent"
          $behaviourOverlapTop
          $scrollYPosition
          $autoPrefix:layout_behavior="android.support.design.widget.AppBarLayout${"$"}ScrollingViewBehavior">
          $pageContentAsXml

      </android.support.v4.widget.NestedScrollView>
      $fab

    </android.support.design.widget.CoordinatorLayout>
  """.trimIndent()

  @JvmStatic
  fun getCoordinatorLayoutWithTabs(androidPrefix: String,
                                   autoPrefix: String,
                                   namespaceDeclarations: String,
                                   scrollFlags: String,
                                   tabItems: String,
                                   scrollYPosition: String,
                                   pageContentAsXml: String,
                                   fab: String) = """
    <android.support.design.widget.CoordinatorLayout
      $namespaceDeclarations

      $androidPrefix:layout_width="match_parent"
      $androidPrefix:layout_height="match_parent">
      <android.support.design.widget.AppBarLayout
          $androidPrefix:id="@+id/appbar"
          $androidPrefix:layout_height="wrap_content"
          $androidPrefix:layout_width="match_parent">
        <android.support.v7.widget.Toolbar
            $androidPrefix:layout_height="?attr/actionBarSize"
            $androidPrefix:layout_width="match_parent"
            $autoPrefix:layout_scrollFlags="scroll|enterAlways">
        </android.support.v7.widget.Toolbar>
        <android.support.design.widget.TabLayout
            $androidPrefix:id="@+id/tabs"
            $androidPrefix:layout_width="match_parent"
            $androidPrefix:layout_height="wrap_content"
            $scrollFlags
            $autoPrefix:tabMode="scrollable">
        $tabItems
        </android.support.design.widget.TabLayout>
      </android.support.design.widget.AppBarLayout>
      <android.support.v4.widget.NestedScrollView
          $androidPrefix:layout_width="match_parent"
          $androidPrefix:layout_height="match_parent"
          $scrollYPosition
          $autoPrefix:layout_behavior="android.support.design.widget.AppBarLayout\${'$'}ScrollingViewBehavior">
        $pageContentAsXml
      </android.support.v4.widget.NestedScrollView>
      $fab
    </android.support.design.widget.CoordinatorLayout>
  """.trimIndent()

  @JvmStatic
  fun getTagFloatingActionButton(androidPrefix: String, imageSrc: String) = """
    <android.support.design.widget.FloatingActionButton
        $androidPrefix:layout_height="wrap_content"
        $androidPrefix:layout_width="wrap_content"
        $androidPrefix:src="$imageSrc"
        $androidPrefix:layout_gravity="bottom|end"
        $androidPrefix:layout_margin="16dp"
        $androidPrefix:clickable="true"/>"
    """.trimIndent()


  @JvmStatic
  fun getImageView(androidPrefix: String, collapseMode: String, imageSrc: String) = """
    <ImageView
        $androidPrefix:id="@+id/app_bar_image"
        $androidPrefix:layout_width="match_parent"
        $androidPrefix:layout_height="match_parent"
        $collapseMode
        $androidPrefix:src="$imageSrc"
        $androidPrefix:scaleType="centerCrop"/>
  """.trimIndent()

  @JvmStatic
  fun getTabItem(androidPrefix: String, textAttribute: String) = """
    <android.support.design.widget.TabItem
      $androidPrefix:layout_height="wrap_content"
      $androidPrefix:layout_width="wrap_content"
      $androidPrefix:text="$textAttribute"/>
  """.trimIndent()

  @JvmStatic
  fun getTextView(androidPrefix: String, text: String) = """
    <TextView
      $androidPrefix:layout_height="match_parent"
      $androidPrefix:layout_width="wrap_content"
      $androidPrefix:text="$text"
      $androidPrefix:padding="16dp"/>
    """.trimIndent()
}

