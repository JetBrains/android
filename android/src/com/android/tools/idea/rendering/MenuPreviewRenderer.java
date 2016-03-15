/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.MergedManifest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Renderer which creates a preview of menus and renders them into a layout XML element hierarchy
 * <p>
 * See
 * http://developer.android.com/guide/topics/ui/menus.html
 * http://developer.android.com/guide/topics/resources/menu-resource.html
 *
 * <p>
 * TODO:
 * <ul>
 *   <li> Should we handle actionLayout and actionViewClass attributes on menu items?</li>
 *   <li> Handle action bar compat and action bar sherlock resources for actionbar preview?</li>
 *   <li> Handle Gingerbread style menus (and earlier) ?</li>
 *   <li> Be more resilient for custom themes not inheriting the necessary menu resources</li>
 * </ul>
 */
public class MenuPreviewRenderer extends LayoutPullParserFactory {
  private static final String ATTR_ORDER_IN_CATEGORY = "orderInCategory";
  private static final String ATTR_MENU_CATEGORY = "menuCategory";
  private static final String ATTR_CHECKABLE = "checkable";
  private static final String ATTR_ALPHABETIC_SHORTCUT = "alphabeticShortcut";
  private static final String ATTR_DUPLICATE_PARENT_STATE = "duplicateParentState";
  private static final String ATTR_NUMERIC_SHORTCUT = "numericShortcut";
  private static final String ATTR_CHECKABLE_BEHAVIOR = "checkableBehavior";
  private static final String ATTR_FOCUSABLE = "focusable";
  private static final String ATTR_CLICKABLE = "clickable";
  private static final String ATTR_ELLIPSIZE = "ellipsize";
  private static final String ATTR_FADING_EDGE = "fadingEdge";
  public static final String ATTR_TEXT_COLOR = "textColor";
  public static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
  public static final String ATTR_TEXT_APPEARANCE = "textAppearance";
  private static final String VALUE_MARQUEE = "marquee";
  private static final String VALUE_SINGLE = "single";
  private static final String VALUE_ALL = "all";
  private static final String VALUE_WITH_TEXT = "withText";
  private static final String VALUE_NEVER = "never";

  private final ResourceResolver myResolver;
  private final Document myDocument;
  private final Module myModule;
  private final int myApiLevel;
  private final Map<Element, Object> viewCookies = Maps.newHashMap();
  private boolean myThemeIsLight;
  private final XmlTag myRootTag;

  public MenuPreviewRenderer(RenderTask renderTask, XmlFile file) {
    myRootTag = file.getRootTag();
    myResolver = renderTask.getResourceResolver();
    assert myResolver != null;

    myDocument = DomPullParser.createEmptyPlainDocument();
    assert myDocument != null;

    myModule = renderTask.getModule();

    Configuration configuration = renderTask.getConfiguration();
    IAndroidTarget target = configuration.getTarget();
    myApiLevel = target != null ? target.getVersion().getApiLevel() : 1;
    myThemeIsLight = isLightTheme(myResolver);
  }

  private static boolean isLightTheme(ResourceResolver resolver) {
    String current = (resolver.isProjectTheme() ? STYLE_RESOURCE_PREFIX : ANDROID_STYLE_RESOURCE_PREFIX) + resolver.getThemeName();
    return resolver.themeExtends("@android:style/Theme.Light", current);
  }

  public ILayoutPullParser render() {
    if (myRootTag == null) {
      return createEmptyParser();
    }

    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    Element root = addRootElement(myDocument, LINEAR_LAYOUT);
    setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    setAndroidAttr(root, ATTR_ORIENTATION, VALUE_VERTICAL);

    // Create representative action bar?
    boolean createdActionBar = false;
    if (myApiLevel >= 11) {
      createdActionBar = addActionBar(root);
    }

    // Next create menu
    Element popup = createMenuPopup();
    if (createdActionBar) {
      setAndroidAttr(popup, ATTR_LAYOUT_MARGIN_TOP, "-10dp");
    }
    root.appendChild(popup);
    populateMenu(popup);

    addFidelityWarning(myDocument, root, "Menu");

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(myDocument, true));
    }

    return new DomPullParser(myDocument.getDocumentElement()).setViewCookies(viewCookies);
  }

  private Element createActionBar() {
    Element layout = myDocument.createElement(LINEAR_LAYOUT);
    setAndroidAttr(layout, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(layout, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(layout, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_RIGHT);
    setAndroidAttr(layout, ATTR_ORIENTATION, VALUE_HORIZONTAL);
    setAndroidAttr(layout, ATTR_GRAVITY, GRAVITY_VALUE_CENTER_VERTICAL + "|" + GRAVITY_VALUE_RIGHT);

    if (myApiLevel >= 11 && myResolver.getFrameworkResource(ResourceType.DRAWABLE, "action_bar_background") != null) {
      setAndroidAttr(layout, ATTR_BACKGROUND, "@android:drawable/action_bar_background");
    } else {
      setAndroidAttr(layout, ATTR_BACKGROUND, "#ff85878a");
    }

    if (myApiLevel >= 11 && myResolver.getFrameworkResource(ResourceType.ATTR, "actionBarSize") != null) {
      setAndroidAttr(layout, ATTR_LAYOUT_HEIGHT, "?android:attr/actionBarSize");
    } else if (myResolver.getProjectResource(ResourceType.ATTR, "actionBarSize") != null) { // ActionBarCompat
      setAndroidAttr(layout, ATTR_LAYOUT_HEIGHT, "?attr/actionBarSize");
    } else {
      setAndroidAttr(layout, ATTR_LAYOUT_HEIGHT, "48dp");
    }

    MergedManifest manifestInfo = ManifestInfo.get(myModule);
    String applicationIcon = manifestInfo.getApplicationIcon();
    if (applicationIcon != null) {
      Element imageView = myDocument.createElement(IMAGE_VIEW);
      layout.appendChild(imageView);
      setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      setAndroidAttr(imageView, ATTR_SRC, applicationIcon);
      setAndroidAttr(imageView, "scaleX", "0.9"); // HACK; find out what the real action bar does to the app icon
      setAndroidAttr(imageView, "scaleY", "0.9");

      Element dummy = myDocument.createElement(VIEW);
      layout.appendChild(dummy);
      setAndroidAttr(dummy, ATTR_LAYOUT_WEIGHT, "1");
      setAndroidAttr(dummy, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(dummy, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    return layout;
  }

  private Element createMenuPopup() {
    Element layout = myDocument.createElement(LINEAR_LAYOUT);
    setAndroidAttr(layout, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
    setAndroidAttr(layout, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(layout, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_RIGHT);
    setAndroidAttr(layout, ATTR_ORIENTATION, VALUE_VERTICAL);

    if (myThemeIsLight) {
      if (myApiLevel >= 11 && myResolver.getFrameworkResource(ResourceType.DRAWABLE, "menu_panel_holo_light") != null) {
        setAndroidAttr(layout, ATTR_BACKGROUND, "@android:drawable/menu_panel_holo_light");
      } else {
        setAndroidAttr(layout, ATTR_BACKGROUND, "@android:drawable/popup_full_bright");
      }
    } else {
      if (myApiLevel >= 11 && myResolver.getFrameworkResource(ResourceType.DRAWABLE, "menu_panel_holo_dark") != null) {
        setAndroidAttr(layout, ATTR_BACKGROUND, "@android:drawable/menu_panel_holo_dark");
      } else {
        setAndroidAttr(layout, ATTR_BACKGROUND, "@android:drawable/popup_full_dark");
      }
    }

    return layout;
  }

  private static void addFidelityWarning(Document document, Element root, String typeName) {
    // Spacer: absorb all weight in the middle to push the warning view below all the way to the bottom
    Element spacer = document.createElement(VIEW);
    root.appendChild(spacer);
    setAndroidAttr(spacer, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(spacer, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    setAndroidAttr(spacer, ATTR_LAYOUT_WEIGHT, "1");

    Element warningView = document.createElement(TEXT_VIEW);
    root.appendChild(warningView);
    setAndroidAttr(warningView, ATTR_TEXT, "(Note: " + typeName + " preview is only approximate)");
    setAndroidAttr(warningView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(warningView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(warningView, ATTR_LAYOUT_MARGIN, "5dp");
    setAndroidAttr(warningView, "textColor", "#ff0000");
    setAndroidAttr(warningView, ATTR_GRAVITY, GRAVITY_VALUE_CENTER);
  }

  private boolean addActionBar(Element root) {
    List<Pair<String,XmlTag>> icons = Lists.newArrayList();

    if (myRootTag != null) {
      for (XmlTag tag : myRootTag.getSubTags()) {
        String icon = tag.getAttributeValue(ATTR_ICON, ANDROID_URI);
        if (icon != null) {
          icons.add(Pair.create(icon, tag));
          if (icons.size() > 6) {
            break;
          }
        }
      }
    }

    // Fake action bar
    if (!icons.isEmpty()) {
      Element linear = createActionBar();
      root.appendChild(linear);

      for (Pair<String, XmlTag> pair : icons) {
        String iconUrl = pair.getFirst();
        XmlTag tag = pair.getSecond();

        String showAsAction = tag.getAttributeValue(ATTR_SHOW_AS_ACTION, ANDROID_URI);
        if (showAsAction == null) {
          showAsAction = tag.getAttributeValue(ATTR_SHOW_AS_ACTION, AUTO_URI); // ActionBar compat
          if (showAsAction == null) {
            showAsAction = "";
          }
        }
        if (showAsAction.contains(VALUE_NEVER)) {
          continue;
        }

        Element imageView = myDocument.createElement(IMAGE_VIEW);
        linear.appendChild(imageView);
        setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        setAndroidAttr(imageView, ATTR_SRC, iconUrl);
        viewCookies.put(imageView, tag);
        if (showAsAction.contains(VALUE_WITH_TEXT)) {
          String title = tag.getAttributeValue(ATTR_TITLE, ANDROID_URI);
          if (title != null) {
            Element textView = myDocument.createElement(TEXT_VIEW);
            linear.appendChild(textView);
            setAndroidAttr(textView, ATTR_TEXT, title);
            setAndroidAttr(textView, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
            setAndroidAttr(textView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
            setAndroidAttr(textView, ATTR_LAYOUT_MARGIN_RIGHT, "8dp");
            viewCookies.put(textView, tag);
          }
        } else {
          setAndroidAttr(imageView, ATTR_LAYOUT_MARGIN_RIGHT, "8dp");
        }
      }

      // More/Overflow button

      // TODO: Dark vs light
      myThemeIsLight = true; // Actionbar is always light here; we need to hardcode the background etc
      String name = myThemeIsLight ? "ic_menu_moreoverflow_holo_light" : "ic_menu_moreoverflow_holo_dark";
      // TODO: Also look for ic_menu_moreoverflow_holo_light
      if (myApiLevel >= 11 && myResolver.getFrameworkResource(ResourceType.DRAWABLE, name) != null) {
        Element imageView = myDocument.createElement(IMAGE_VIEW);
        linear.appendChild(imageView);
        setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        setAndroidAttr(imageView, ATTR_SRC, "@android:drawable/" + name);
        setAndroidAttr(imageView, ATTR_LAYOUT_MARGIN_RIGHT, "8dp");
      }

      return true;
    }

    return false;
  }

  private void populateMenu(@NotNull Element root) {
    boolean dividerSupported = myApiLevel >= 11;

    // The divider and dividerPadding attribute on LinearLayout doesn't work in layoutlib
    // yet (see issue 29959) so use workaround of inserting individual <ImageView>
    // items with a background corresponding to ?android:attr/dividerHorizontal instead?
    boolean useDividerAttribute = false;

    //noinspection ConstantConditions
    if (dividerSupported && useDividerAttribute) {
      setAndroidAttr(root, "divider", "?android:attr/actionBarDivider");
      setAndroidAttr(root, "dividerPadding", "12dip");
    }

    String textNormalHeight;
    if (myApiLevel >= 14 && myResolver.findResValue("?android:attr/dropdownListPreferredItemHeight", true) != null) {
      textNormalHeight = "?android:attr/dropdownListPreferredItemHeight";
    } else if (myApiLevel >= 14 && myResolver.findResValue("?android:attr/listPreferredItemHeightSmall", true) != null) {
      textNormalHeight =  "?android:attr/listPreferredItemHeightSmall";
    } else {
      textNormalHeight = "48dip";
    }

    boolean hasPopupTextAppearance = false;
    if (myApiLevel >= 11 && myResolver.findResValue("?android:attr/textAppearanceLargePopupMenu", true) != null) {
      hasPopupTextAppearance = true;
    }

    boolean first = root.getChildNodes().getLength() == 0;
    List<MenuItem> items = readMenu();
    for (MenuItem menuItem : items) {
      XmlTag item = menuItem.tag;

      if (!menuItem.visible) {
        continue;
      }

      if (first) {
        first = false;
      } else //noinspection ConstantConditions
        if (!useDividerAttribute) {
          Element imageView = myDocument.createElement(IMAGE_VIEW);
          root.appendChild(imageView);
          setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
          setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, "1dp");
          if (dividerSupported) {
            setAndroidAttr(imageView, ATTR_BACKGROUND, "?android:attr/dividerHorizontal"); // instead of divider
          }
        }

      // Make a ListMenuItemView (which is a LinearLayout) as configured in popup_menu_item_layout.xml
      Element listMenuView = myDocument.createElement(LINEAR_LAYOUT);
      root.appendChild(listMenuView);
      viewCookies.put(listMenuView, item);
      setAndroidAttr(listMenuView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
      setAndroidAttr(listMenuView, ATTR_LAYOUT_HEIGHT, textNormalHeight);
      setAndroidAttr(listMenuView, "minWidth", "196dip"); // From popup_menu_item_layout
      setAndroidAttr(listMenuView, "paddingEnd", "16dip");

      // TODO: Insert icon here?
      // If so, prepend to listMenuView
      // Depends on mMenu.getOptionalIconsVisible, off by default
      //if (myApiLevel < 11) {
      //  String icon = item.getAttributeValue(ATTR_ICON, ANDROID_URI);
      //  if (icon != null) {
      //    setAndroidAttr(itemView, ATTR_DRAWABLE_LEFT, icon);
      //  }
      //}

      Element relative = myDocument.createElement(RELATIVE_LAYOUT);
      listMenuView.appendChild(relative);
      setAndroidAttr(relative, ATTR_LAYOUT_WIDTH, VALUE_ZERO_DP);
      setAndroidAttr(relative, ATTR_LAYOUT_WEIGHT, VALUE_1);
      setAndroidAttr(relative, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      setAndroidAttr(relative, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER_VERTICAL);
      setAndroidAttr(relative, ATTR_LAYOUT_MARGIN_LEFT, "16dip");
      setAndroidAttr(relative, ATTR_DUPLICATE_PARENT_STATE, VALUE_TRUE);

      Element itemView = myDocument.createElement(TEXT_VIEW);
      relative.appendChild(itemView);
      setAndroidAttr(itemView, ATTR_ID, "@+id/title");
      setAndroidAttr(itemView, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(itemView, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      setAndroidAttr(itemView, ATTR_LAYOUT_ALIGN_PARENT_TOP, VALUE_TRUE);
      setAndroidAttr(itemView, ATTR_LAYOUT_ALIGN_PARENT_LEFT, VALUE_TRUE);

      if (hasPopupTextAppearance) {
        setAndroidAttr(itemView, ATTR_TEXT_APPEARANCE, "?android:attr/textAppearanceLargePopupMenu");
      } else {
        setAndroidAttr(itemView, ATTR_TEXT_SIZE, "22sp");
        setAndroidAttr(itemView, ATTR_TEXT_COLOR, "?android:attr/textColorPrimary");
      }

      setAndroidAttr(itemView, ATTR_SINGLE_LINE, VALUE_TRUE);
      setAndroidAttr(itemView, ATTR_DUPLICATE_PARENT_STATE, VALUE_TRUE);
      if (myApiLevel >= 14) { // doesn't render on older version
        setAndroidAttr(itemView, ATTR_ELLIPSIZE, VALUE_MARQUEE);
      }
      setAndroidAttr(itemView, ATTR_FADING_EDGE, "horizontal");
      setAndroidAttr(itemView, ATTR_TEXT_ALIGNMENT, "viewStart");

      String title = item.getAttributeValue(ATTR_TITLE, ANDROID_URI);
      if (title != null) {
        setAndroidAttr(itemView, ATTR_TEXT, title);
      }
      String visibility = item.getAttributeValue(ATTR_VISIBILITY, ANDROID_URI);
      if (visibility != null) {
        setAndroidAttr(itemView, ATTR_VISIBILITY, visibility);
      }
      if (!menuItem.enabled) {
        setAndroidAttr(itemView, ATTR_ENABLED, VALUE_FALSE);
      }

      String shortcut = item.getAttributeValue(ATTR_ALPHABETIC_SHORTCUT, ANDROID_URI);
      if (shortcut == null) {
        shortcut = item.getAttributeValue(ATTR_NUMERIC_SHORTCUT, ANDROID_URI);
      }
      if (shortcut != null) {
        Element shortCut = myDocument.createElement(TEXT_VIEW);
        relative.appendChild(shortCut);
        setAndroidAttr(shortCut, ATTR_TEXT, shortcut);
        setAndroidAttr(shortCut, ATTR_ID, "@+id/shortcut");
        setAndroidAttr(shortCut, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        setAndroidAttr(shortCut, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        setAndroidAttr(shortCut, ATTR_LAYOUT_BELOW, "@id/title");
        setAndroidAttr(shortCut, ATTR_LAYOUT_ALIGN_PARENT_LEFT, VALUE_TRUE);

        if (hasPopupTextAppearance) {
          setAndroidAttr(shortCut, ATTR_TEXT_APPEARANCE, "?android:attr/textAppearanceSmallPopupMenu");
        } else {
          setAndroidAttr(shortCut, ATTR_TEXT_SIZE, "14sp");
          setAndroidAttr(shortCut, ATTR_TEXT_COLOR, "?android:attr/textColorSecondary");
        }

        setAndroidAttr(shortCut, ATTR_SINGLE_LINE, VALUE_TRUE);
        setAndroidAttr(shortCut, ATTR_DUPLICATE_PARENT_STATE, VALUE_TRUE);
        setAndroidAttr(shortCut, ATTR_TEXT_ALIGNMENT, "viewStart");
        if (visibility != null) {
          setAndroidAttr(shortCut, ATTR_VISIBILITY, visibility);
        }
      }

      // com.android.internal.view.menu.ListMenuItemView inserts a checkbox or radio button here
      if (menuItem.checkable != 0) {
        Element toggle = myDocument.createElement(menuItem.checkable == 1 ? CHECK_BOX : RADIO_BUTTON);
        listMenuView.appendChild(toggle);
        setAndroidAttr(toggle, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        setAndroidAttr(toggle, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        setAndroidAttr(toggle, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER_VERTICAL);
        setAndroidAttr(toggle, ATTR_FOCUSABLE, VALUE_FALSE);
        setAndroidAttr(toggle, ATTR_CLICKABLE, VALUE_FALSE);
        setAndroidAttr(toggle, ATTR_DUPLICATE_PARENT_STATE, VALUE_TRUE);
        String checked = item.getAttributeValue(ATTR_CHECKED, ANDROID_URI);
        if (checked != null) {
          toggle.setAttributeNS(ANDROID_URI, ATTR_CHECKED, checked);
        }
        if (!menuItem.enabled) {
          setAndroidAttr(toggle, ATTR_ENABLED, VALUE_FALSE);
        }
        if (visibility != null) {
          setAndroidAttr(toggle, ATTR_VISIBILITY, visibility);
        }
      }
    }
  }

  private List<MenuItem> readMenu() {
    ArrayList<MenuItem> items = Lists.newArrayList();
    addMenuItems(items, myRootTag);

    return items;
  }

  private void addMenuItems(ArrayList<MenuItem> items, XmlTag menuTag) {
    for (XmlTag tag : menuTag.getSubTags()) {
      String tagName = tag.getName();
      if (TAG_ITEM.equals(tagName)) {
        MenuItem item = readItem(tag);
        items.add(findInsertIndex(items, item.ordering), item);
      } else if (TAG_GROUP.equals(tagName)) {
        readGroup(tag);
        addMenuItems(items, tag);
        resetGroup();
      } else //noinspection StatementWithEmptyBody
        if (TAG_MENU.equals(tagName)) {
        // We don't need to process these at designtime; not rendering sub-menus (actionbar menus also do not give
        // a visual indication of these)
      }
    }
  }

  private static boolean getBoolean(XmlTag tag, String attributeName, boolean defaultValue) {
    String value = tag.getAttributeValue(attributeName, ANDROID_URI);
    if (value != null) {
      return Boolean.valueOf(value);
    }
    return defaultValue;
  }

  private static int getInt(XmlTag tag, String attributeName, int defaultValue) {
    String value = tag.getAttributeValue(attributeName, ANDROID_URI);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        // fall through
      }
    }
    return defaultValue;
  }

  private static int getCategory(XmlTag tag, int defaultValue) {
    String category = tag.getAttributeValue(ATTR_MENU_CATEGORY, ANDROID_URI);
    if (category != null) {
      // Constants from attrs.xml: MenuGroup#menuCategory
      if (category.equals("container")) {
        return 0x00010000;
      } else if (category.equals("system")) {
        return 0x00020000;
      } else if (category.equals("secondary")) {
        return 0x00030000;
      } else if (category.equals("alternative")) {
        return 0x00040000;
      }
    }

    return defaultValue;
  }

  private static final int CHECKABLE_NONE = 0;
  private static final int CHECKABLE_ALL = 1;
  private static final int CHECKABLE_EXCLUSIVE = 2;

  /**
   * A menu item. The tag field points to the XML attributes for the original item; all the other
   * state pertains to ordering or inherited attributes from the surrounding group(s).
   */
  private static final class MenuItem {
    public final XmlTag tag;
    public final int ordering;
    public final boolean visible;
    public final boolean enabled;
    public final int checkable;

    public MenuItem(XmlTag tag, int ordering, int checkable, boolean visible, boolean enabled) {
      this.tag = tag;
      this.ordering = ordering;
      this.checkable = checkable;
      this.visible = visible;
      this.enabled = enabled;
    }
  }

  // Condensed from android.view.MenuInflater.MenuState

  private int myGroupCategory;
  private int myGroupOrder;
  private int myGroupCheckable;
  private boolean myGroupVisible = true;
  private boolean myGroupEnabled = true;

  public void resetGroup() {
    myGroupCategory = 0;
    myGroupOrder = 0;
    myGroupCheckable = 0;
    myGroupVisible = true;
    myGroupEnabled = true;
  }

  public void readGroup(XmlTag tag) {
    assert tag.getName().equals(TAG_GROUP) : tag.getName();

    myGroupCheckable = CHECKABLE_NONE;
    String checkableBehavior = tag.getAttributeValue(ATTR_CHECKABLE_BEHAVIOR, ANDROID_URI);
    if (VALUE_SINGLE.equals(checkableBehavior)) {
      myGroupCheckable = CHECKABLE_ALL;
    } else if (VALUE_ALL.equals(checkableBehavior)) {
      myGroupCheckable = CHECKABLE_EXCLUSIVE;
    } else {
      myGroupCheckable = CHECKABLE_NONE;
    }

    myGroupCategory = getCategory(tag, 0);
    myGroupOrder = getInt(tag, ATTR_ORDER_IN_CATEGORY, 0);
    myGroupVisible = getBoolean(tag, ATTR_VISIBLE, true);
    myGroupEnabled = getBoolean(tag, ATTR_ENABLED, true);
  }

  public MenuItem readItem(XmlTag tag) {
    assert tag.getName().equals(TAG_ITEM) : tag.getName();
    int category = getCategory(tag, myGroupCategory);
    int order = getInt(tag, ATTR_ORDER_IN_CATEGORY, myGroupOrder);
    boolean itemVisible = getBoolean(tag, ATTR_VISIBLE, myGroupVisible);
    boolean itemEnabled = getBoolean(tag, ATTR_ENABLED, myGroupEnabled);
    int itemCategoryOrder = (category & CATEGORY_MASK) | (order & USER_MASK);
    XmlAttribute checkableAttribute = tag.getAttribute(ATTR_CHECKABLE, ANDROID_URI);
    int itemCheckable;
    if (checkableAttribute != null) {
      itemCheckable = Boolean.valueOf(checkableAttribute.getValue()) ? 1 : 0;
    } else {
      itemCheckable = myGroupCheckable;
    }

    return new MenuItem(tag, getOrdering(itemCategoryOrder), itemCheckable, itemVisible, itemEnabled);
  }

  // Condensed from android.support.v7.internal.view.menu.MenuBuilder; code to handle ordering
  // such that we end up with the same sort order as at runtime

  private static int getOrdering(int categoryOrder) {
    final int index = (categoryOrder & CATEGORY_MASK) >> CATEGORY_SHIFT;
    assert index >= 0 && index < ourCategoryToOrder.length;
    return (ourCategoryToOrder[index] << CATEGORY_SHIFT) | (categoryOrder & USER_MASK);
  }

  private static int findInsertIndex(ArrayList<MenuItem> items, int ordering) {
    for (int i = items.size() - 1; i >= 0; i--) {
      MenuItem item = items.get(i);
      if (item.ordering <= ordering) {
        return i + 1;
      }
    }

    return 0;
  }

  private static final int[] ourCategoryToOrder = new int[] {
    1, /* No category */
    4, /* CONTAINER */
    5, /* SYSTEM */
    3, /* SECONDARY */
    2, /* ALTERNATIVE */
    0, /* SELECTED_ALTERNATIVE */
  };

  private static final int USER_MASK = 0x0000ffff;
  private static final int CATEGORY_MASK = 0xffff0000;
  private static final int CATEGORY_SHIFT = 16;
}
