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

public final class simple_scene_xml {
  static String value="<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<MotionScene \n" +
                      "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:motion=\"http://schemas.android.com/apk/res-auto\">\n" +
                      "\n" +
                      "    <Transition\n" +
                      "        motion:constraintSetEnd=\"@+id/end\"\n" +
                      "        motion:constraintSetStart=\"@id/start\"\n" +
                      "        motion:duration=\"1000\">\n" +
                      "       <KeyFrameSet>\n" +
                      "           <KeyPosition\n" +
                      "               motion:motionTarget=\"@+id/button29\"\n" +
                      "               motion:framePosition=\"50\"\n" +
                      "               motion:percentX=\"1.0\" />\n" +
                      "       </KeyFrameSet>\n" +
                      "    </Transition>\n" +
                      "\n" +
                      "    <ConstraintSet android:id=\"@+id/start\">\n" +
                      "        <Constraint\n" +
                      "            motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:layout_marginEnd=\"8dp\"\n" +
                      "            android:layout_marginRight=\"8dp\"\n" +
                      "            android:id=\"@+id/button29\"\n" +
                      "            motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                      "            android:layout_marginStart=\"110dp\"\n" +
                      "            android:layout_marginLeft=\"110dp\"\n" +
                      "            motion:layout_constraintTop_toTopOf=\"parent\" />\n" +
                      "    </ConstraintSet>\n" +
                      "\n" +
                      "    <ConstraintSet android:id=\"@+id/end\">\n" +
                      "        <Constraint\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:id=\"@+id/button29\"\n" +
                      "            motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                      "            android:layout_marginStart=\"8dp\"\n" +
                      "            android:layout_marginLeft=\"8dp\"\n" +
                      "            motion:layout_constraintBottom_toBottomOf=\"parent\" />\n" +
                      "        <Constraint\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:layout_marginTop=\"76dp\"\n" +
                      "            motion:layout_constraintTop_toTopOf=\"parent\"\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                      "            android:layout_marginLeft=\"216dp\"\n" +
                      "            android:layout_marginStart=\"216dp\"\n" +
                      "            android:id=\"@+id/move\" />\n" +
                      "    </ConstraintSet>\n" +
                      "</MotionScene>";
  public static InputStream asStream() {
    return new ByteArrayInputStream(value.getBytes(Charset.forName("UTF-8" )));
  }

}
