/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.tools.configurations.Configuration;
import junit.framework.TestCase;

import static com.android.tools.idea.uibuilder.model.TextDirection.LEFT_TO_RIGHT;
import static com.android.tools.idea.uibuilder.model.TextDirection.RIGHT_TO_LEFT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TextDirectionTest extends TestCase {
  public void test() {
    assertSame(LEFT_TO_RIGHT, TextDirection.fromConfiguration(null));

    Configuration configuration = mock(Configuration.class);
    FolderConfiguration folderConfig = new FolderConfiguration();
    when(configuration.getFullConfig()).thenReturn(folderConfig);

    // Nothing specified
    assertSame(LEFT_TO_RIGHT, TextDirection.fromConfiguration(configuration));

    // LTR specified
    folderConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
    assertSame(LEFT_TO_RIGHT, TextDirection.fromConfiguration(configuration));

    // RTL specified
    folderConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
    assertSame(RIGHT_TO_LEFT, TextDirection.fromConfiguration(configuration));


    assertSame(SegmentType.START, LEFT_TO_RIGHT.getLeftSegment());
    assertSame(SegmentType.END, RIGHT_TO_LEFT.getLeftSegment());

    assertSame(SegmentType.END, LEFT_TO_RIGHT.getRightSegment());
    assertSame(SegmentType.START, RIGHT_TO_LEFT.getRightSegment());

    assertTrue(LEFT_TO_RIGHT.isLeftSegment(SegmentType.START));
    assertFalse(LEFT_TO_RIGHT.isLeftSegment(SegmentType.END));
    assertTrue(RIGHT_TO_LEFT.isLeftSegment(SegmentType.END));
    assertFalse(RIGHT_TO_LEFT.isLeftSegment(SegmentType.START));
  }
}