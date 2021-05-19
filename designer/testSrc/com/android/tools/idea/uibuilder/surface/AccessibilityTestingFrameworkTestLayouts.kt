/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import org.intellij.lang.annotations.Language

@Language("XML")
const val DUP_BOUNDS_LAYOUT = """
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:id="@+id/clickable_wrapper_for_button"
        android:orientation="horizontal"
        android:clickable="true">

        <Button
            android:text="Button in clickable parent"
            android:id="@+id/button_in_clickable_parent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minHeight="48dp"/>
    </LinearLayout>

    <Button
        android:text="Button on its own"
        android:id="@+id/button_on_its_own"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:minHeight="48dp"/>

</LinearLayout>
"""

@Language("XML")
const val TEXT_COLOR_CONTRAST_SIMPLE = """
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical">

    <TextView
      android:id="@+id/low_contrast_button1"
      android:layout_width="wrap_content"
      android:layout_height="48dp"
      android:layout_marginStart="196dp"
      android:layout_marginTop="248dp"
      android:background="@android:color/holo_green_dark"
      android:text="low contrast"
      android:textColor="#0098cb"
      tools:ignore="HardcodedText"/>

</LinearLayout>
"""

@Language("XML")
const val TEXT_COLOR_CONTRAST_COMPLEX = """
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:gravity="top|center_horizontal"
              android:orientation="vertical">
    <!-- Low contrast, with slightly transparent text -->
    <Button
        android:id="@+id/low_contrast_button1"
        android:text="Bad Button"
        android:layout_width="wrap_content"
        android:layout_height="48dp"
        android:layout_margin="8dp"
        android:layout_marginLeft="0dp"
        android:background="@android:color/holo_green_dark"
        android:textColor="#fe0099cc" />
    <!-- ATF bypasses transparent views / colors unless image is available. -->
    <Button
        android:id="@+id/low_contrast_button2"
        android:layout_width="wrap_content"
        android:layout_height="48dp"
        android:background="@android:color/holo_green_dark"
        android:text="Button B"
        android:textColor="@android:color/holo_blue_dark"/>
    <EditText
        android:id="@+id/low_contrast_edit_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ems="10"
        android:inputType="textPersonName"
        android:minHeight="48dp"
        android:text="Edit B"
        android:background="@android:color/holo_green_dark"
        android:textColor="@android:color/holo_blue_dark"
    />
    <CheckBox
        android:id="@+id/low_contrast_check_box"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@android:color/holo_green_dark"
        android:minHeight="48dp"
        android:text="CheckBox B"
        android:textColor="@android:color/holo_blue_dark"/>
</LinearLayout>
  """

