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
package com.android.tools.idea.uibuilder.motion.adapters.samples;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * XML layout file as a string for use in test
 */
public final class layout_16_xml {
 static String value = "<?xml version=\"1.0\" encoding=\"utf-8\"?><!--<android.support.constraint.motion.MotionLayout   ConstraintLayout-->\n" +
  "<android.support.constraint.motion.MotionLayout\n" +
  "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
  "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
  "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
  "    android:id=\"@+id/base\"\n" +
  "    android:layout_width=\"match_parent\"\n" +
  "    android:layout_height=\"match_parent\"\n" +
  "    app:layoutDescription=\"@xml/motion_testscene_16_scene\">\n" +
  "    <!--app:motionDebug=\"SHOW_ALL\"-->\n" +
  "<!--android.support.constraint.motion.MotionLayout-->\n" +
  "    <!--android:background=\"@drawable/israel_small\"-->\n" +
  "   <TextView android:id=\"@+id/number\"\n" +
  "       android:text=\"xxx-xxx-xxxx\"\n" +
  "       android:layout_width=\"0dp\"\n" +
  "       android:layout_height=\"wrap_content\"\n" +
  "       app:layout_constraintEnd_toEndOf=\"parent\"\n" +
  "       android:layout_marginTop=\"8dp\"\n" +
  "       android:layout_marginEnd=\"8dp\"\n" +
  "       android:layout_marginStart=\"8dp\"\n" +
  "       android:textAlignment=\"center\"\n" +
  "       app:layout_constraintStart_toStartOf=\"parent\"\n" +
  "       app:layout_constraintTop_toTopOf=\"parent\"\n" +
  "       android:background=\"#E5E5E5\"\n" +
  "       />\n" +
  "\n" +
  "\n" +
  "\n" +
  "    <View\n" +
  "        android:id=\"@+id/dial_pad\"\n" +
  "        android:layout_width=\"match_parent\"\n" +
  "        android:layout_height=\"500dp\"\n" +
  "        android:background=\"#137AE2\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
  "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"parent\" />\n" +
  "\n" +
  " <TextView android:id=\"@+id/dialtitle\"\n" +
  "     android:text=\"Dial\"\n" +
  "     android:layout_width=\"0dp\"\n" +
  "     android:layout_height=\"wrap_content\"\n" +
  "     app:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
  "     android:layout_marginTop=\"8dp\"\n" +
  "     android:layout_marginEnd=\"8dp\"\n" +
  "     android:layout_marginStart=\"8dp\"\n" +
  "     android:textAlignment=\"center\"\n" +
  "     app:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
  "     app:layout_constraintTop_toTopOf=\"@+id/dial_pad\"\n" +
  "     android:background=\"#E5E5E5\"\n" +
  "     />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button1\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"1\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button2\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button2\" />\n" +
  "\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button2\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "\n" +
  "        android:text=\"2\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button3\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button1\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button3\" />\n" +
  "\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button3\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "\n" +
  "\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"3\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/button6\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button2\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/dial_pad\" />\n" +
  "\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button4\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"4\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button5\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button5\" />\n" +
  "\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button5\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"5\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button6\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button4\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button6\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button6\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"6\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/button9\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button5\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/button3\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button7\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "\n" +
  "\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"7\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button8\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button8\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button8\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "\n" +
  "        android:text=\"8\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button9\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button7\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button9\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button9\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"9\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/button12\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button8\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/button6\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button10\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"*\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button11\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button11\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button11\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"0\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/button12\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button10\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/button12\" />\n" +
  "\n" +
  "    <Button\n" +
  "        android:id=\"@+id/button12\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:background=\"#49A9DF\"\n" +
  "        android:text=\"#\"\n" +
  "        app:layout_constraintBottom_toBottomOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/button11\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/button9\" />\n" +
  "\n" +
  "    <View\n" +
  "        android:id=\"@+id/people_pad\"\n" +
  "        android:layout_width=\"300dp\"\n" +
  "        android:layout_height=\"500dp\"\n" +
  "        android:layout_marginStart=\"4dp\"\n" +
  "        android:background=\"#643A07\"\n" +
  "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
  "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
  "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
  "\n" +
  " <TextView android:id=\"@+id/people_title\"\n" +
  "     android:text=\"Contacts\"\n" +
  "     android:layout_width=\"0dp\"\n" +
  "     android:layout_height=\"20dp\"\n" +
  "     app:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
  "     android:layout_marginTop=\"8dp\"\n" +
  "     android:layout_marginEnd=\"8dp\"\n" +
  "     android:layout_marginStart=\"8dp\"\n" +
  "     android:textAlignment=\"center\"\n" +
  "     app:layout_constraintStart_toStartOf=\"parent\"\n" +
  "     app:layout_constraintTop_toTopOf=\"@+id/people_pad\"\n" +
  "     android:background=\"#E5E5E5\"\n" +
  "     />\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people1\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginStart=\"8dp\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        android:src=\"@drawable/avatar_1_raster\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people3\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/people2\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/people_pad\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people2\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginEnd=\"8dp\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        android:src=\"@drawable/avatar_2_raster\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people4\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/people1\"\n" +
  "        app:layout_constraintTop_toTopOf=\"@+id/people_pad\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people3\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginStart=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_3_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people5\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/people4\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people1\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people4\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginEnd=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_4_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people6\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/people3\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people2\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people5\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginStart=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_5_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people7\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/people6\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people3\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people6\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginEnd=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_6_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toTopOf=\"@+id/people8\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/people5\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people4\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people7\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginStart=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_7_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toBottomOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintEnd_toStartOf=\"@+id/people8\"\n" +
  "        app:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people5\" />\n" +
  "\n" +
  "    <android.support.constraint.utils.ImageFilterButton\n" +
  "        android:id=\"@+id/people8\"\n" +
  "        android:layout_width=\"64dp\"\n" +
  "        android:layout_height=\"64dp\"\n" +
  "        android:layout_marginEnd=\"8dp\"\n" +
  "        android:src=\"@drawable/avatar_8_raster\"\n" +
  "        android:scaleType=\"fitCenter\"\n" +
  "        app:layout_constraintBottom_toBottomOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
  "        app:layout_constraintStart_toEndOf=\"@+id/people7\"\n" +
  "        app:layout_constraintTop_toBottomOf=\"@+id/people6\" />\n" +
  "\n" +
  "</android.support.constraint.motion.MotionLayout>\n";
   public static InputStream asStream() {
     return new ByteArrayInputStream(value.getBytes(Charset.forName( "UTF-8" )));
   }
}