/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.ManifestInfo.ActivityAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.VALUE_SPLIT_ACTION_BAR_WHEN_NARROW;

public class ActionBarHandler extends ActionBarCallback {

  @NotNull private ManifestInfo myManifestInfo;
  @NotNull private String myActivityName;

  ActionBarHandler(@NotNull ManifestInfo manifestInfo) {
    myManifestInfo = manifestInfo;
    myActivityName = "";
  }

  @Override
  public boolean getSplitActionBarWhenNarrow() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null) {
      return VALUE_SPLIT_ACTION_BAR_WHEN_NARROW.equals(attributes.getUiOptions());
    }
    return false;
  }

  @Override
  public boolean isOverflowPopupNeeded() {
    return true;
  }

  @Override
  public List<String> getMenuIdNames() {
    ArrayList<String> menus = new ArrayList<String>(1);
    menus.add("main");
    return menus;
  }

  @Override
  public HomeButtonStyle getHomeButtonStyle() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null && attributes.getParentActivity() != null) {
      return HomeButtonStyle.SHOW_HOME_AS_UP;
    }
    return HomeButtonStyle.NONE;
  }

  public void setActivityName(@NotNull String activityName) {
    myActivityName = activityName;
  }

  private @Nullable ActivityAttributes getActivityAttributes() {
    return myManifestInfo.getActivityAttributes(myActivityName);
  }
}
