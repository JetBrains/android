/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testing.swing;

import javax.swing.event.TableModelEvent;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatcher;

public final class TableModelEventArgumentMatcher implements ArgumentMatcher<TableModelEvent> {
  private final @NotNull TableModelEvent myExpectedEvent;

  public TableModelEventArgumentMatcher(@NotNull TableModelEvent expectedEvent) {
    myExpectedEvent = expectedEvent;
  }

  @Override
  public boolean matches(@NotNull TableModelEvent actualEvent) {
    return myExpectedEvent.getSource().equals(actualEvent.getSource()) &&
           myExpectedEvent.getType() == actualEvent.getType() &&
           myExpectedEvent.getFirstRow() == actualEvent.getFirstRow() &&
           myExpectedEvent.getLastRow() == actualEvent.getLastRow() &&
           myExpectedEvent.getColumn() == actualEvent.getColumn();
  }
}
