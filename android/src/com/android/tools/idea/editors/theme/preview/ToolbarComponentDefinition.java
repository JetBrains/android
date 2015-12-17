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
package com.android.tools.idea.editors.theme.preview;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.android.SdkConstants.*;


/**
 * Toolbar component definition used for both the framework and appcompat toolbars.
 */
class ToolbarComponentDefinition extends ThemePreviewBuilder.ComponentDefinition {
  private final boolean myIsAppCompat;

  ToolbarComponentDefinition(boolean isAppCompat) {
    super("Toolbar", ThemePreviewBuilder.ComponentGroup.TOOLBAR, isAppCompat ? "android.support.v7.widget.Toolbar" : "Toolbar");

    myIsAppCompat = isAppCompat;

    if (!isAppCompat) {
      setApiLevel(21);
      set(ATTR_LAYOUT_HEIGHT, "?android:attr/actionBarSize");
    }
    else {
      // We are trying to emulate the behaviour of the bar system components using regular content. We remove the content insets so the
      // buttons are correctly located.
      set(APP_PREFIX, "contentInsetStart", "0dp");
      set(APP_PREFIX, "contentInsetLeft", "0dp");
    }

    String attrPrefix = getAttrPrefix(isAppCompat);
    set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    set(ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");

    // we style this toolbar to look as if it was set as the Activity ActionBar
    set(ATTR_BACKGROUND, attrPrefix + "colorPrimary");
    // always use android:theme, as app:theme is deprecated
    set(ATTR_THEME, attrPrefix + "actionBarTheme");
    // TODO: some apps may use ToolBars as ActionBars AND on there own simply inside a layout, should we preview both types?

    addAlias("Actionbar");
  }

  private static String getAttrPrefix(boolean isAppCompat) {
    return isAppCompat ? ATTR_REF_PREFIX : "?android:attr/";
  }

  @Override
  Element build(@NotNull Document document) {
    Element toolbarComponent = super.build(document);
    String attrPrefix = getAttrPrefix(myIsAppCompat);

    Element navIcon = document.createElement(IMAGE_BUTTON);
    navIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
    navIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");
    if (myIsAppCompat) {
      navIcon.setAttribute("style", "?attr/toolbarNavigationButtonStyle");
    } else {
      navIcon.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, "@android:color/transparent");
      navIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, "10dp");
    }
    navIcon.setAttributeNS(ANDROID_URI, ATTR_SRC, attrPrefix + "homeAsUpIndicator");
    navIcon.setAttributeNS(ANDROID_URI, "tint", attrPrefix + "actionMenuTextColor");
    navIcon.setAttributeNS(ThemePreviewBuilder.BUILDER_URI, ThemePreviewBuilder.BUILDER_ATTR_GROUP, group.name());
    toolbarComponent.appendChild(navIcon);

    // Create a title using the same values that the Toolbar title has when created programmatically.
    Element title = document.createElement(TEXT_VIEW);
    title.setAttributeNS(ANDROID_URI, ATTR_TEXT, myIsAppCompat ? "v7 Toolbar" : "Toolbar");
    if (myIsAppCompat) {
      title.setAttributeNS(ANDROID_URI, "textAppearance", "@style/TextAppearance.Widget.AppCompat.Toolbar.Title");
    } else {
      title.setAttributeNS(ANDROID_URI, "textAppearance", "@android:style/TextAppearance.Material.Widget.Toolbar.Title");
    }
    title.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
    title.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");
    title.setAttributeNS(ANDROID_URI, ATTR_GRAVITY, VALUE_CENTER_VERTICAL);
    title.setAttributeNS(ANDROID_URI, "ellipsize", "end");
    title.setAttributeNS(ANDROID_URI, ATTR_SINGLE_LINE, VALUE_TRUE);
    title.setAttributeNS(ThemePreviewBuilder.BUILDER_URI, ThemePreviewBuilder.BUILDER_ATTR_GROUP, group.name());
    toolbarComponent.appendChild(title);

    Element menuIcon = document.createElement(IMAGE_BUTTON);
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, "40dp");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, attrPrefix + "selectableItemBackground");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_RIGHT);
    menuIcon.setAttribute("style", "?attr/toolbarNavigationButtonStyle");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_SRC, "@drawable/abc_ic_menu_moreoverflow_mtrl_alpha");
    menuIcon.setAttributeNS(ThemePreviewBuilder.BUILDER_URI, ThemePreviewBuilder.BUILDER_ATTR_GROUP, group.name());
    menuIcon.setAttributeNS(ANDROID_URI, "tint", attrPrefix + "actionMenuTextColor");
    toolbarComponent.appendChild(menuIcon);

    return toolbarComponent;
  }
}
