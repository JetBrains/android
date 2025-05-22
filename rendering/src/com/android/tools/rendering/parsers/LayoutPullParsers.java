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
package com.android.tools.rendering.parsers;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_FONT_FAMILY;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_PADDING_BOTTOM;
import static com.android.SdkConstants.ATTR_SCALE_TYPE;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.ATTR_TEXT_COLOR;
import static com.android.SdkConstants.ATTR_TEXT_SIZE;
import static com.android.SdkConstants.ATTR_TEXT_STYLE;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_SCREEN_ANDROIDX;
import static com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN;
import static com.android.SdkConstants.TAG_ADAPTIVE_ICON;
import static com.android.SdkConstants.TAG_APPWIDGET_PROVIDER;
import static com.android.SdkConstants.TAG_FONT_FAMILY;
import static com.android.SdkConstants.TAG_MASKABLE_ICON;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.XMLNS_ANDROID;
import static com.android.SdkConstants.XMLNS_URI;
import static com.android.ide.common.rendering.api.SessionParams.RenderingMode.V_SCROLL;

import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.tools.apk.analyzer.ResourceIdResolver;
import com.android.tools.fonts.DownloadableFontCacheService;
import com.android.tools.fonts.ProjectFonts;
import com.android.tools.rendering.IRenderLogger;
import com.android.tools.rendering.RenderTask;
import com.android.tools.rendering.api.NavGraphResolver;
import com.android.tools.res.ResourceRepositoryManager;
import com.android.tools.res.ids.ResourceIdManagerHelper;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Methods for creating layout pull parsers for various different types of files.
 */
public class LayoutPullParsers {
  static final boolean DEBUG = false;

  private static final String[] VALID_XML_TAGS = {
    TAG_APPWIDGET_PROVIDER,
    PREFERENCE_SCREEN,
    CLASS_PREFERENCE_SCREEN_ANDROIDX.oldName(),
    CLASS_PREFERENCE_SCREEN_ANDROIDX.newName()
  };
  private static final String[] ADAPTIVE_ICON_TAGS = {TAG_ADAPTIVE_ICON, TAG_MASKABLE_ICON};
  private static final String[] FONT_FAMILY_TAGS = {TAG_FONT_FAMILY};

  private static final EnumSet<ResourceFolderType> FOLDER_NEEDS_READ_ACCESS =
    EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP, ResourceFolderType.MENU, ResourceFolderType.XML, ResourceFolderType.FONT);

  /** Not instantiatable. */
  private LayoutPullParsers() {}

  /**
   * Returns whether the passed {@link RenderXmlFile} starts with any of the given rootTags.
   */
  private static boolean isXmlWithRootTag(@NotNull RenderXmlFile file, @NotNull String[] rootTags) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RenderXmlTag rootTag = file.getRootTag();
    if (rootTag == null) {
      return false;
    }

    String tag = rootTag.getName();
    for (String validRootTags : rootTags) {
      if (validRootTags.equals(tag)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isSupported(@NotNull RenderXmlFile file) {
    ResourceFolderType folderType = file.getFolderType();
    if (folderType == null) {
      return false;
    }
    switch (folderType) {
      case LAYOUT:
      case DRAWABLE:
      case MENU:
        return true;
      case MIPMAP:
        return isXmlWithRootTag(file, ADAPTIVE_ICON_TAGS);
      case XML:
        return isXmlWithRootTag(file, VALID_XML_TAGS);
      case FONT:
        return isXmlWithRootTag(file, FONT_FAMILY_TAGS);
      default:
        return false;
    }
  }

  @Nullable
  public static ILayoutPullParser create(@NotNull final RenderTask renderTask) {
    final ResourceFolderType folderType = renderTask.getContext().getFolderType();
    if (folderType == null) {
      return null;
    }

    if (FOLDER_NEEDS_READ_ACCESS.contains(folderType)
        && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<ILayoutPullParser>)() -> create(renderTask));
    }

    RenderXmlFile file = renderTask.getXmlFile();
    if (file == null) {
      throw new IllegalArgumentException("RenderTask always should always have PsiFile when it has ResourceFolderType");
    }

    ResourceRepositoryManager manager = renderTask.getContext().getModule().getResourceRepositoryManager();

    switch (folderType) {
      case LAYOUT: {
        IRenderLogger logger = renderTask.getLogger();
        ResourceResolver resourceResolver = renderTask.getContext().getConfiguration().getResourceResolver();
        NavGraphResolver navGraphResolver = renderTask.getContext().getModule().getEnvironment().getNavGraphResolver(resourceResolver);
        boolean useToolsNamespace = renderTask.getShowWithToolsVisibilityAndPosition();
        return LayoutRenderPullParser.create(file, logger, navGraphResolver, manager, useToolsNamespace);
      }
      case DRAWABLE:
      case MIPMAP:
        renderTask.setDecorations(false);
        return createDrawableParser(file);
      case MENU:
        renderTask.setDecorations(true);
        return createMenuParser(file, renderTask);
      case XML: {
        // Switch on root type
        RenderXmlTag rootTag = file.getRootTag();
        if (rootTag != null) {
          String tag = rootTag.getName();
          if (tag.equals(TAG_APPWIDGET_PROVIDER)) {
            // Widget
            renderTask.setDecorations(false);
            return createWidgetParser(rootTag);
          }
          else if (tag.equals(PREFERENCE_SCREEN) ||
                   CLASS_PREFERENCE_SCREEN_ANDROIDX.isEquals(tag)) {
            IRenderLogger logger = renderTask.getLogger();
            ResourceResolver resourceResolver = renderTask.getContext().getConfiguration().getResourceResolver();
            NavGraphResolver navGraphResolver = renderTask.getContext().getModule().getEnvironment().getNavGraphResolver(resourceResolver);
            return LayoutRenderPullParser.create(file, logger, navGraphResolver, manager, true);
          }
        }
        return null;
      }
      case FONT:
        renderTask.setTransparentBackground();
        renderTask.setDecorations(false);
        renderTask.setRenderingMode(V_SCROLL);
        ResourceIdResolver resolver = ResourceIdManagerHelper.getResolver(renderTask.getContext().getModule().getResourceIdManager());
        DownloadableFontCacheService fontService = renderTask.getContext().getModule().getEnvironment().getDownloadableFontCacheService();
        return createFontFamilyParser(file, (fontName) -> (new ProjectFonts(fontService, manager, resolver)).getFont(fontName), renderTask.getDefaultForegroundColor());
      default:
        // Should have been prevented by isSupported(PsiFile)
        assert false : folderType;
        return null;
    }
  }

  private static ILayoutPullParser createDrawableParser(RenderXmlFile file) {
    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element imageView = addRootElement(document, IMAGE_VIEW, file.getResourceNamespace());
    setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);

    ResourceFolderType type = file.getFolderType();
    assert type != null;

    setAndroidAttr(imageView, ATTR_SRC, file.getRelativePath());

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    // Allow tools:background in drawable XML files to manually set the render background.
    // Useful for example when dealing with vectors or shapes where the color happens to
    // be close to the IDE default background.
    String background = file.getRootTagAttribute(ATTR_BACKGROUND, TOOLS_URI);
    if (background != null && !background.isEmpty()) {
      setAndroidAttr(imageView, ATTR_BACKGROUND, background);
    }

    // Allow tools:scaleType in drawable XML files to manually set the scale type. This is useful
    // when the drawable looks poor in the default scale type. (http://b.android.com/76267)
    String scaleType = file.getRootTagAttribute(ATTR_SCALE_TYPE, TOOLS_URI);
    if (scaleType != null && !scaleType.isEmpty()) {
      setAndroidAttr(imageView, ATTR_SCALE_TYPE, scaleType);
    }

    return DomPullParser.createFromDocument(document);
  }

  @NotNull
  private static ILayoutPullParser createMenuParser(@NotNull RenderXmlFile file, @NotNull RenderTask task) {
    RenderXmlTag tag = file.getRootTag();

    // LayoutLib renders a menu in an app bar by default. If the menu resource has a tools:showIn="navigation_view" attribute, tell
    // LayoutLib to render it in a navigation view instead.
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), "navigation_view")) {
      task.setDecorations(false);
      return MenuLayoutParserFactory.createInNavigationView(file, task.getContext().getModule().getDependencies());
    }

    return MenuLayoutParserFactory.create(file, task::setMenuResource);
  }

  @Nullable
  private static ILayoutPullParser createWidgetParser(RenderXmlTag rootTag) {
    // See http://developer.android.com/guide/topics/appwidgets/index.html:

    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    String layout = rootTag.getAttributeValue("initialLayout", ANDROID_URI);
    String preview = rootTag.getAttributeValue("previewImage", ANDROID_URI);
    if (layout == null && preview == null) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, layout != null ? VIEW_INCLUDE : IMAGE_VIEW, rootTag.getResourceNamespace());
    if (layout != null) {
      root.setAttribute(ATTR_LAYOUT, layout);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    }
    else {
      root.setAttribute(ATTR_SRC, preview);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    return DomPullParser.createFromDocument(document);
  }

  @VisibleForTesting
  @Nullable
  public static ILayoutPullParser createFontFamilyParser(
    @NotNull RenderXmlFile file,
    @NotNull Function<String, FontFamily> getDownloadableFont,
    @NotNull String fontColor
  ) {
    RenderXmlTag rootTag = file.getRootTag();

    if (rootTag == null || !TAG_FONT_FAMILY.equals(rootTag.getName())) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element rootLayout = addRootElement(document, LINEAR_LAYOUT, file.getResourceNamespace());
    setAndroidAttr(rootLayout, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(rootLayout, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(rootLayout, ATTR_ORIENTATION, VALUE_VERTICAL);

    String loremText = "Lorem ipsum dolor sit amet, consectetur adipisicing elit.";

    ResourceFolderType type = file.getFolderType();
    assert type != null;

    String fontRefName = PREFIX_RESOURCE_REF + type.getName() + "/" + SdkUtils.fileNameToResourceName(file.getName());

    List<RenderXmlTag> fontSubTags = rootTag.getSubTags();
    Stream<String[]> fontStream;

    if (fontSubTags.isEmpty()) {
      // This might be a downloadable font. Check if we have it.
      FontFamily downloadedFont = getDownloadableFont.apply(fontRefName);

      fontStream = downloadedFont != null ? downloadedFont.getFonts().stream()
        .map(font -> new String[]{fontRefName, font.getFontStyle()}) : Stream.empty();
    }
    else {
      fontStream = fontSubTags.stream()
        .map(font -> new String[]{font.getAttributeValue("font", ANDROID_URI), "normal"})
        .filter(font -> !Strings.isNullOrEmpty(font[0]));
    }

    boolean[] hasElements = new boolean[1];
    fontStream.forEach(font -> {
      hasElements[0] = true;
      Element fontElement = document.createElement(TEXT_VIEW);
      setAndroidAttr(fontElement, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(fontElement, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      setAndroidAttr(fontElement, ATTR_TEXT, loremText);
      setAndroidAttr(fontElement, ATTR_FONT_FAMILY, font[0]);
      setAndroidAttr(fontElement, ATTR_TEXT_SIZE, "30sp");
      setAndroidAttr(fontElement, ATTR_TEXT_COLOR, fontColor);
      setAndroidAttr(fontElement, ATTR_PADDING_BOTTOM, "20dp");
      setAndroidAttr(fontElement, ATTR_TEXT_STYLE, font[1]);

      rootLayout.appendChild(fontElement);
    });

    if (!hasElements[0]) {
      return null;
    }

    return DomPullParser.createFromDocument(document);
  }

  public static Element addRootElement(@NotNull Document document, @NotNull String tag, @Nullable ResourceNamespace namespace) {
    Element root = document.createElementNS(namespace != null ? namespace.getXmlNamespaceUri() : null, tag);

    //root.setAttribute(XMLNS_ANDROID, ANDROID_URI);

    // Set up a proper name space
    Attr attr = document.createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    attr.setValue(ANDROID_URI);
    root.getAttributes().setNamedItemNS(attr);

    document.appendChild(root);
    return root;
  }

  public static Element setAndroidAttr(Element element, String name, String value) {
    element.setAttributeNS(ANDROID_URI, name, value);
    //element.setAttribute(ANDROID_NS_NAME + ':' + name, value);
    //Attr attr = element.getOwnerDocument().createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    //attr.setValue(ANDROID_URI);
    //root.getAttributes().setNamedItemNS(attr);

    return element;
  }

  public static ILayoutPullParser createEmptyParser() {
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, FRAME_LAYOUT, null);
    setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    return DomPullParser.createFromDocument(document);
  }
}
