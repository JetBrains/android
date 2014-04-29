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

import com.android.SdkConstants;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.ManifestInfo.ActivityAttributes;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.VALUE_SPLIT_ACTION_BAR_WHEN_NARROW;

/**
 * A callback to provide information related to the Action Bar as required by the
 * {@link LayoutLibrary}
 */
public class ActionBarHandler extends ActionBarCallback {

  @NotNull
  private RenderService myRenderService;
  @Nullable
  private List<String> myMenus;


  ActionBarHandler(@NotNull RenderService renderService) {
    myRenderService = renderService;
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
    if (myMenus != null) {
      return myMenus;
    }
    String commaSeparatedMenus = getRootTagAttributeSafely(myRenderService.getPsiFile(), "menu", SdkConstants.TOOLS_URI);
    if (commaSeparatedMenus != null) {
      ArrayList<String> menus = new ArrayList<String>();
      Iterables.addAll(menus, Splitter.on(',').trimResults().omitEmptyStrings().split(commaSeparatedMenus));
      if (menus.size() > 0) {
        return menus;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public HomeButtonStyle getHomeButtonStyle() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null && attributes.getParentActivity() != null) {
      return HomeButtonStyle.SHOW_HOME_AS_UP;
    }
    return HomeButtonStyle.NONE;
  }

  public void setMenuIdNames(@Nullable List<String> menus) {
    myMenus = menus;
  }

  private @Nullable ActivityAttributes getActivityAttributes() {
    ManifestInfo manifest = ManifestInfo.get(myRenderService.getModule(), false);
    String activity = StringUtil.notNullize(myRenderService.getConfiguration().getActivity());
    return manifest.getActivityAttributes(activity);
  }

  /**
   * Get the value of an attribute in the {@link XmlFile} safely (meaning it will acquire the read lock first).
   */
  @Nullable
  private static String getRootTagAttributeSafely(@NotNull final XmlFile file,
                                                  @NotNull final String attribute,
                                                  @Nullable final String namespace) {
    Application application = ApplicationManager.getApplication();
    if (!application.isReadAccessAllowed()) {
      return application.runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          return getRootTagAttributeSafely(file, attribute, namespace);
        }
      });
    } else {
      XmlTag tag = file.getRootTag();
      if (tag != null) {
        XmlAttribute attr = tag.getAttribute(attribute, namespace);
        if (attr != null) {
          return attr.getValue();
        }
      }
      return null;
    }
  }
}
