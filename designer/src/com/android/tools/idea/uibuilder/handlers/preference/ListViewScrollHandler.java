/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.preference;

import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import com.android.tools.idea.uibuilder.api.ScrollHandler;
import org.jetbrains.annotations.NotNull;

final class ListViewScrollHandler implements ScrollHandler {
  private final AbsListView myListView;

  ListViewScrollHandler(@NotNull ListView listView) {
    myListView = listView;
  }

  @Override
  public int update(int scrollAmount) {
    if (scrollAmount == 0 || myListView.getChildCount() == 0) {
      return 0;
    }

    View view = myListView.getChildAt(scrollAmount > 0 ? myListView.getChildCount() - 1 : 0);
    scrollAmount *= 10;

    myListView.setSelectionFromTop(myListView.getPositionForView(view), view.getTop() - scrollAmount);
    return scrollAmount;
  }

  @Override
  public void commit(int scrollAmount) {
    update(scrollAmount);
  }

  @Override
  public boolean canScroll(int scrollAmount) {
    return true;
  }
}
