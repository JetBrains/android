/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.uibuilder.MOST_RECENT_API_LEVEL
import com.google.common.collect.ImmutableList
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet

const val APPCOMPAT_IMAGE_VIEW = "android.support.v7.widget.AppCompatImageView"
const val APPCOMPAT_TEXT_VIEW = "android.support.v7.widget.AppCompatTextView"

private const val APPCOMPAT_ACTIVITY =
  """
package android.support.v7.app;
public class AppCompatActivity {}
"""

private const val APPCOMPAT_IMAGE_VIEW_SOURCE =
  """
package android.support.v7.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class AppCompatImageView extends ImageView {

 public AppCompatImageView(Context context) {
        this(context, null);
    }

    public AppCompatImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppCompatImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
"""

private const val APPCOMPAT_TEXT_VIEW_SOURCE =
  """
package android.support.v7.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class AppCompatTextView extends TextView {

 public AppCompatTextView(Context context) {
        this(context, null);
    }

    public AppCompatTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppCompatTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }}
"""

private const val APPCOMPAT_ATTRS =
  """
<resources>
    <declare-styleable name="AppCompatTextView">
        <!-- Present the text in ALL CAPS. This may use a small-caps form when available. -->
        <attr name="textAllCaps" format="reference|boolean" />
        <attr name="android:textAppearance" />
        <!-- Specify the type of auto-size. Note that this feature is not supported by EditText,
        works only for TextView. -->
        <attr name="autoSizeTextType" format="enum">
            <!-- No auto-sizing (default). -->
            <enum name="none" value="0" />
            <!-- Uniform horizontal and vertical text size scaling to fit within the
            container. -->
            <enum name="uniform" value="1" />
        </attr>
        <!-- Specify the auto-size step size if <code>autoSizeTextType</code> is set to
        <code>uniform</code>. The default is 1px. Overwrites
        <code>autoSizePresetSizes</code> if set. -->
        <attr name="autoSizeStepGranularity" format="dimension" />
        <!-- Resource array of dimensions to be used in conjunction with
        <code>autoSizeTextType</code> set to <code>uniform</code>. Overrides
        <code>autoSizeStepGranularity</code> if set. -->
        <attr name="autoSizePresetSizes" format="reference"/>
        <!-- The minimum text size constraint to be used when auto-sizing text. -->
        <attr name="autoSizeMinTextSize" format="dimension" />
        <!-- The maximum text size constraint to be used when auto-sizing text. -->
        <attr name="autoSizeMaxTextSize" format="dimension" />
        <!-- The attribute for the font family. -->
        <attr name="fontFamily" format="string" />
    </declare-styleable>

  <declare-styleable name="AppCompatImageView">
        <attr name="android:src"/>
        <!-- Sets a drawable as the content of this ImageView. Allows the use of vector drawable
             when running on older versions of the platform. -->
        <attr name="srcCompat" format="reference" />

        <!-- Tint to apply to the image source. -->
        <attr name="tint" format="color" />

        <!-- Blending mode used to apply the image source tint. -->
        <attr name="tintMode">
            <!-- The tint is drawn on top of the drawable.
                 [Sa + (1 - Sa)*Da, Rc = Sc + (1 - Sa)*Dc] -->
            <enum name="src_over" value="3" />
            <!-- The tint is masked by the alpha channel of the drawable. The drawable’s
                 color channels are thrown out. [Sa * Da, Sc * Da] -->
            <enum name="src_in" value="5" />
            <!-- The tint is drawn above the drawable, but with the drawable’s alpha
                 channel masking the result. [Da, Sc * Da + (1 - Sa) * Dc] -->
            <enum name="src_atop" value="9" />
            <!-- Multiplies the color and alpha channels of the drawable with those of
                 the tint. [Sa * Da, Sc * Dc] -->
            <enum name="multiply" value="14" />
            <!-- [Sa + Da - Sa * Da, Sc + Dc - Sc * Dc] -->
            <enum name="screen" value="15" />
            <!-- Combines the tint and icon color and alpha channels, clamping the
                 result to valid color values. Saturate(S + D) -->
            <enum name="add" value="16" />
        </attr>
    </declare-styleable>
</resources>
"""

private const val MY_ACTIVITY =
  """
package com.example;

import android.support.v7.app.AppCompatActivity;

public class MyActivity extends AppCompatActivity {}
"""

object MockAppCompat {

  fun setUp(facet: AndroidFacet, fixture: JavaCodeInsightTestFixture) {
    val gradleVersion = GradleVersion.parse(String.format("%1\$d.0.0", MOST_RECENT_API_LEVEL))
    val appCompatCoordinate =
      GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate(gradleVersion.toString())
    val projectSystem =
      TestProjectSystem(facet.module.project, ImmutableList.of(appCompatCoordinate))
    projectSystem.useInTests()

    fixture.addFileToProject(
      "src/android/support/v7/app/AppCompatImageView.java",
      APPCOMPAT_ACTIVITY,
    )
    fixture.addFileToProject(
      "src/android/support/v7/widget/AppCompatImageView.java",
      APPCOMPAT_IMAGE_VIEW_SOURCE,
    )
    fixture.addFileToProject(
      "src/android/support/v7/widget/AppCompatTextView.java",
      APPCOMPAT_TEXT_VIEW_SOURCE,
    )
    fixture.addFileToProject("res/values/attrs.xml", APPCOMPAT_ATTRS)
    fixture.addFileToProject("src/com/example/MyActivity.java", MY_ACTIVITY)
  }
}
