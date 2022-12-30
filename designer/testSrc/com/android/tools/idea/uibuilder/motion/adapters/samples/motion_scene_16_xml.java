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
 * XML MotionScene file as a string for use in test
 */
public final class motion_scene_16_xml {
 static String value = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
   "<MotionScene xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
   "             xmlns:motion=\"http://schemas.android.com/apk/res-auto\">\n" +
   "\n" +
   "\n" +
   "    <Transition\n" +
   "            motion:constraintSetStart=\"@+id/base_state\"\n" +
   "            motion:constraintSetEnd=\"@+id/dial\"\n" +
   "            motion:duration=\"3000\">\n" +
   "        <OnSwipe\n" +
   "                motion:dragDirection=\"dragUp\"\n" +
   "                motion:touchAnchorId=\"@id/dial_pad\"\n" +
   "                motion:touchAnchorSide=\"top\"/>\n" +
   "        <KeyFrameSet>\n" +
   "            <KeyPosition\n" +
   "                    motion:keyPositionType=\"pathRelative\"\n" +
   "                    motion:percentX=\"0.3\"\n" +
   "                    motion:motionTarget=\"@id/dial_pad\"\n" +
   "                    motion:framePosition=\"50\"/>\n" +
   "        </KeyFrameSet>\n" +
   "    </Transition>\n" +
   "\n" +
   "    <Transition\n" +
   "            motion:constraintSetStart=\"@+id/base_state\"\n" +
   "            motion:constraintSetEnd=\"@+id/half_people\"\n" +
   "            motion:duration=\"3000\">\n" +
   "        <OnSwipe\n" +
   "                motion:dragDirection=\"dragRight\"\n" +
   "                motion:touchAnchorId=\"@id/people_pad\"\n" +
   "                motion:touchAnchorSide=\"right\"/>\n" +
   "\n" +
   "        <KeyFrameSet>\n" +
   "            <KeyPosition\n" +
   "                    motion:keyPositionType=\"pathRelative\"\n" +
   "                    motion:percentX=\"0.3\"\n" +
   "                    motion:motionTarget=\"@id/people_pad\"\n" +
   "                    motion:framePosition=\"50\"/>\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"sin\"\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"0\"\n" +
   "                    android:translationX=\"0dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"sin\"\n" +
   "                    motion:wavePeriod=\"1\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"15\"\n" +
   "                    android:translationX=\"0dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"sin\"\n" +
   "                    motion:wavePeriod=\"1\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"75\"\n" +
   "                    android:translationX=\"200dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"sin\"\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"100\"\n" +
   "                    android:translationX=\"0dp\"/>\n" +
   "\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"cos\"\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"0\"\n" +
   "                    android:translationY=\"0dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"cos\"\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"15\"\n" +
   "                    android:translationY=\"0dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"cos\"\n" +
   "                    motion:wavePeriod=\"1\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"75\"\n" +
   "                    android:translationY=\"200dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:waveShape=\"cos\"\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"rotate\"\n" +
   "                    motion:framePosition=\"0100\"\n" +
   "                    android:translationY=\"0dp\"/>\n" +
   "\n" +
   "\n" +
   "        </KeyFrameSet>\n" +
   "    </Transition>\n" +
   "\n" +
   "    <Transition\n" +
   "            motion:constraintSetStart=\"@+id/half_people\"\n" +
   "            motion:constraintSetEnd=\"@+id/people\"\n" +
   "            motion:duration=\"3000\">\n" +
   "\n" +
   "        <OnSwipe\n" +
   "                motion:dragDirection=\"dragRight\"\n" +
   "                motion:touchAnchorId=\"@id/people_pad\"\n" +
   "                motion:touchAnchorSide=\"right\"/>\n" +
   "\n" +
   "        <KeyFrameSet>\n" +
   "            <KeyPosition\n" +
   "                    motion:keyPositionType=\"pathRelative\"\n" +
   "                    motion:percentX=\"0.3\"\n" +
   "                    motion:motionTarget=\"@id/people_pad\"\n" +
   "                    motion:framePosition=\"50\"/>\n" +
   "\n" +
   "            <KeyTimeCycle\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"@+id/people_title\"\n" +
   "                    motion:framePosition=\"10\"\n" +
   "                    android:translationX=\"0dp\"/>\n" +
   "\n" +
   "            <KeyTimeCycle\n" +
   "                    motion:wavePeriod=\"0.1\"\n" +
   "                    motion:motionTarget=\"@+id/people_title\"\n" +
   "                    motion:framePosition=\"20\"\n" +
   "                    android:translationX=\"400dp\"/>\n" +
   "\n" +
   "            <KeyTimeCycle\n" +
   "                    motion:wavePeriod=\"0.1\"\n" +
   "                    motion:motionTarget=\"@+id/people_title\"\n" +
   "                    motion:framePosition=\"80\"\n" +
   "                    android:translationX=\"400dp\"/>\n" +
   "\n" +
   "            <KeyTimeCycle\n" +
   "                    motion:wavePeriod=\"0\"\n" +
   "                    motion:motionTarget=\"@+id/people_title\"\n" +
   "                    motion:framePosition=\"90\"\n" +
   "                    android:translationX=\"0dp\"/>\n" +
   "\n" +
   "            <KeyCycle\n" +
   "                    motion:wavePeriod=\"0.5\"\n" +
   "                    motion:motionTarget=\"@+id/people_pad\"\n" +
   "                    motion:framePosition=\"50\"\n" +
   "                    android:rotation=\"20\"/>\n" +
   "\n" +
   "            <KeyTrigger\n" +
   "                    motion:onPositiveCross=\"callOnClick\"\n" +
   "                    motion:motion_triggerOnCollision=\"@+id/people_pad\"\n" +
   "                    motion:motionTarget=\"@+id/people_title\"\n" +
   "                    motion:motion_postLayoutCollision=\"true\"/>\n" +
   "\n" +
   "            <KeyAttribute\n" +
   "                    motion:motionTarget=\"@id/people_title\"\n" +
   "                    motion:framePosition=\"40\"\n" +
   "                    android:alpha=\"0.2\"\n" +
   "                     />\n" +
   "\n" +
   "        </KeyFrameSet>\n" +
   "\n" +
   "    </Transition>\n" +
   "    <include motion:constraintSet=\"@xml/test\"/>\n" +
   "\n" +
   "    <ConstraintSet android:id=\"@+id/base_state\">\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/dial_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"fill_parent\"\n" +
   "                    android:layout_height=\"300dp\"\n" +
   "\n" +
   "                    motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toBottomOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/people_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"300dp\"\n" +
   "                    android:layout_height=\"500dp\"\n" +
   "                    android:layout_marginStart=\"4dp\"\n" +
   "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
   "                    motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toTopOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "    </ConstraintSet>\n" +
   "\n" +
   "    <ConstraintSet android:id=\"@+id/dial\" motion:deriveConstraintsFrom=\"@id/base_state\">\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/dial_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"fill_parent\"\n" +
   "                    android:layout_height=\"500dp\"\n" +
   "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
   "                    motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/people_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"300dp\"\n" +
   "                    android:layout_height=\"500dp\"\n" +
   "                    android:layout_marginStart=\"4dp\"\n" +
   "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
   "                    motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toTopOf=\"parent\"/>\n" +
   "\n" +
   "                   <CustomAttribute\n" +
   "                motion:attributeName=\"letterSpacing\"\n" +
   "                motion:customFloatValue=\"1\" />" +
   "          </Constraint>\n" +
   "\n" +
   "    </ConstraintSet>\n" +
   "\n" +
   "    <ConstraintSet android:id=\"@+id/people\" motion:deriveConstraintsFrom=\"@id/base_state\">\n" +
   "        <Constraint android:id=\"@+id/number\"\n" +
   "                    android:text=\"xxx-xxx-xxxx\"\n" +
   "                    android:layout_width=\"0dp\"\n" +
   "                    android:layout_height=\"0dp\"\n" +
   "                    motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
   "                    android:layout_marginTop=\"8dp\"\n" +
   "                    android:layout_marginBottom=\"8dp\"\n" +
   "                    android:layout_marginEnd=\"8dp\"\n" +
   "                    android:layout_marginStart=\"8dp\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toTopOf=\"parent\"\n" +
   "                    motion:layout_constraintBottom_toTopOf=\"@+id/people_pad\"\n" +
   "        />\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/dial_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"fill_parent\"\n" +
   "                    android:layout_height=\"500dp\"\n" +
   "                    motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toBottomOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/people_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"360dp\"\n" +
   "                    android:layout_height=\"600dp\"\n" +
   "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toTopOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "    </ConstraintSet>\n" +
   "\n" +
   "    <ConstraintSet android:id=\"@+id/half_people\"\n" +
   "                   motion:deriveConstraintsFrom=\"@+id/people\">\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/dial_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"fill_parent\"\n" +
   "                    android:layout_height=\"500dp\"\n" +
   "                    motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toBottomOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "        <Constraint android:id=\"@+id/people_pad\">\n" +
   "            <Layout\n" +
   "                    android:layout_width=\"360dp\"\n" +
   "                    android:layout_height=\"600dp\"\n" +
   "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
   "                    motion:layout_constraintStart_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
   "                    motion:layout_constraintTop_toTopOf=\"parent\"/>\n" +
   "\n" +
   "        </Constraint>\n" +
   "\n" +
   "    </ConstraintSet>\n" +
   "\n" +
   "\n" +
   "</MotionScene>";

  static String includeString = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<ConstraintSet " +
                        //"    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        //"    xmlns:motion=\"http://schemas.android.com/apk/res-auto\"" +
                        "    android:id=\"@+id/include_state\" >"+
                        "\n" +
                        "    <Constraint android:id=\"@+id/dial_pad\">\n" +
                        "         <Layout\n" +
                        "            android:layout_width=\"fill_parent\"\n" +
                        "            android:layout_height=\"300dp\"\n" +
                        "\n" +
                        "            motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                        "            motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                        "            motion:layout_constraintTop_toBottomOf=\"parent\"/>\n" +
                        "\n" +
                        "        </Constraint>\n" +
                        "\n" +
                        "        <Constraint android:id=\"@+id/people_pad\">\n" +
                        "            <Layout\n" +
                        "                    android:layout_width=\"300dp\"\n" +
                        "                    android:layout_height=\"500dp\"\n" +
                        "                    android:layout_marginStart=\"4dp\"\n" +
                        "                    motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                        "                    motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                        "                    motion:layout_constraintTop_toTopOf=\"parent\"/>\n" +
                        "\n" +
                        "        </Constraint>\n" +
                        "\n" +
                        "    </ConstraintSet>\n";
  public static InputStream asStream() {
     return new ByteArrayInputStream(value.getBytes(Charset.forName( "UTF-8" )));
   }
  public static InputStream asIncludeStream() {
    return new ByteArrayInputStream(includeString.getBytes(Charset.forName( "UTF-8" )));
  }
}