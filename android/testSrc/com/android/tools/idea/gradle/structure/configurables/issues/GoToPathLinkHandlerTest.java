/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.navigation.Places;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.intellij.ui.navigation.Place;
import org.junit.Before;
import org.junit.Test;

import static com.android.tools.idea.gradle.structure.configurables.issues.GoToPathLinkHandler.GO_TO_PATH_TYPE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GoToPathLinkHandlerTest {

  private LinkHandler myLinkHandler;
  private ProjectStructureConfigurable myMainConfigurable;
  private Place myPlace;

  @Before
  public void setUp() {
    myPlace = new Place().putPath("name", "object");
    myMainConfigurable = mock(ProjectStructureConfigurable.class);
    PsContext context = mock(PsContext.class);

    when(context.getMainConfigurable()).thenReturn(myMainConfigurable);

    myLinkHandler = new GoToPathLinkHandler(context);
  }

  @Test
  public void navigate() {
    String target = GO_TO_PATH_TYPE + Places.serialize(myPlace);
    myLinkHandler.navigate(target);
    verify(myMainConfigurable).navigateTo(eq(myPlace), eq(true));
  }
}