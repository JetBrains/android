// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.uibuilder.api.actions;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

public class ViewActionsSeparatorTest {
  @Mock
  private ViewEditor myEditor;
  @Mock
  private ViewHandler myHandler;
  @Mock
  private NlComponent myComponent;
  private List<NlComponent> mySelected;

  @Before
  public void setUp() {
    initMocks(this);
    mySelected = Collections.emptyList();
  }

  @Test
  public void testSeparatorIsInvisibleWhenAllFollowingActionsAreInvisible() {
    ViewActionSeparator separator = new ViewActionSeparator();

    List<ViewAction> actions = new ArrayList<>();
    actions.add(createAction(true));
    actions.add(separator);
    actions.add(createAction(false));
    actions.add(createAction(false));

    ViewActionSeparator.setupFollowingActions(actions);
    assertThat(separator.isVisible(myEditor, myHandler, myComponent, mySelected)).isFalse();
  }

  @Test
  public void testSeparatorIsVisibleWhenOneFollowingActionIsVisible() {
    ViewActionSeparator separator = new ViewActionSeparator();

    List<ViewAction> actions = new ArrayList<>();
    actions.add(createAction(true));
    actions.add(separator);
    actions.add(createAction(false));
    actions.add(createAction(false));
    actions.add(createAction(false));
    actions.add(createAction(false));
    actions.add(createAction(true));

    ViewActionSeparator.setupFollowingActions(actions);
    assertThat(separator.isVisible(myEditor, myHandler, myComponent, mySelected)).isTrue();
  }

  @NotNull
  private static ViewAction createAction(boolean visible) {
    return new AbstractViewAction(null, "") {

      @Override
      public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                     @NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selectedChildren,
                                     int modifiersEx) {
        presentation.setVisible(visible);
      }

      @Override
      public void perform(@NotNull ViewEditor editor,
                          @NotNull ViewHandler handler,
                          @NotNull NlComponent component,
                          @NotNull List<NlComponent> selectedChildren,
                          int modifiers) {
      }
    };
  }
}
