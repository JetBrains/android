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
package com.android.tools.idea.rendering.parsers;

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

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.fonts.ProjectFonts;
import com.android.tools.idea.rendering.AndroidXmlFiles;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
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
   * Returns whether the passed file is an {@link XmlFile} and starts with any of the given rootTags
   */
  private static boolean isXmlWithRootTag(@NotNull PsiFile file, @NotNull String[] rootTags) {
    if (!(file instanceof XmlFile)) {
      return false;
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlTag rootTag = ((XmlFile)file).getRootTag();
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

  public static boolean isSupported(@NotNull PsiFile file) {
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
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

    XmlFile file = renderTask.getXmlFile();
    if (file == null) {
      throw new IllegalArgumentException("RenderTask always should always have PsiFile when it has ResourceFolderType");
    }

    ResourceRepositoryManager manager = renderTask.getContext().getModule().getResourceRepositoryManager();

    switch (folderType) {
      case LAYOUT: {
        IRenderLogger logger = renderTask.getLogger();
        HardwareConfig hardwareConfig = renderTask.getHardwareConfigHelper().getConfig();
        ResourceResolver resourceResolver = renderTask.getContext().getConfiguration().getResourceResolver();
        boolean useToolsNamespace = renderTask.getShowWithToolsVisibilityAndPosition();
        return LayoutPsiPullParser
          .create(file, logger, Collections.emptySet(), hardwareConfig.getDensity(), resourceResolver, manager, useToolsNamespace);
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
        XmlTag rootTag = file.getRootTag();
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
            HardwareConfig hardwareConfig = renderTask.getHardwareConfigHelper().getConfig();
            ResourceResolver resourceResolver = renderTask.getContext().getConfiguration().getResourceResolver();
            return LayoutPsiPullParser.create(file, logger,  Collections.emptySet(), hardwareConfig.getDensity(), resourceResolver, manager, true);
          }
        }
        return null;
      }
      case FONT:
        renderTask.setTransparentBackground();
        renderTask.setDecorations(false);
        renderTask.setRenderingMode(V_SCROLL);
        return createFontFamilyParser(file, (fontName) -> (new ProjectFonts(manager)).getFont(fontName));
      default:
        // Should have been prevented by isSupported(PsiFile)
        assert false : folderType;
        return null;
    }
  }

  private static ILayoutPullParser createDrawableParser(XmlFile file) {
    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element imageView = addRootElement(document, IMAGE_VIEW, IdeResourcesUtil.getResourceNamespace(file));
    setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);

    ResourceFolderType type = IdeResourcesUtil.getFolderType(file);
    assert type != null;

    setAndroidAttr(imageView, ATTR_SRC, file.getVirtualFile().getPath());

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    // Allow tools:background in drawable XML files to manually set the render background.
    // Useful for example when dealing with vectors or shapes where the color happens to
    // be close to the IDE default background.
    String background = AndroidXmlFiles.getRootTagAttributeSafely(file, ATTR_BACKGROUND, TOOLS_URI);
    if (background != null && !background.isEmpty()) {
      setAndroidAttr(imageView, ATTR_BACKGROUND, background);
    }

    // Allow tools:scaleType in drawable XML files to manually set the scale type. This is useful
    // when the drawable looks poor in the default scale type. (http://b.android.com/76267)
    String scaleType = AndroidXmlFiles.getRootTagAttributeSafely(file, ATTR_SCALE_TYPE, TOOLS_URI);
    if (scaleType != null && !scaleType.isEmpty()) {
      setAndroidAttr(imageView, ATTR_SCALE_TYPE, scaleType);
    }

    return DomPullParser.createFromDocument(document);
  }

  @NotNull
  private static ILayoutPullParser createMenuParser(@NotNull XmlFile file, @NotNull RenderTask task) {
    XmlTag tag = file.getRootTag();

    // LayoutLib renders a menu in an app bar by default. If the menu resource has a tools:showIn="navigation_view" attribute, tell
    // LayoutLib to render it in a navigation view instead.
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), "navigation_view")) {
      task.setDecorations(false);
      return MenuLayoutParserFactory.createInNavigationView(file);
    }

    return MenuLayoutParserFactory.create(file, task.getLayoutlibCallback());
  }

  @Nullable
  private static ILayoutPullParser createWidgetParser(XmlTag rootTag) {
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
    Element root = addRootElement(document, layout != null ? VIEW_INCLUDE : IMAGE_VIEW, IdeResourcesUtil.getResourceNamespace(rootTag));
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
  static ILayoutPullParser createFontFamilyParser(XmlFile file, @NotNull Function<String, FontFamily> getDownloadableFont) {
    XmlTag rootTag = file.getRootTag();

    if (rootTag == null || !TAG_FONT_FAMILY.equals(rootTag.getName())) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element rootLayout = addRootElement(document, LINEAR_LAYOUT, IdeResourcesUtil.getResourceNamespace(file));
    setAndroidAttr(rootLayout, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(rootLayout, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(rootLayout, ATTR_ORIENTATION, VALUE_VERTICAL);

    String loremText = new LoremGenerator().generate(8, true);

    ResourceFolderType type = IdeResourcesUtil.getFolderType(file);
    assert type != null;

    String fontRefName = PREFIX_RESOURCE_REF + type.getName() + "/" + SdkUtils.fileNameToResourceName(file.getName());

    XmlTag[] fontSubTags = rootTag.getSubTags();
    Stream<String[]> fontStream;

    if (fontSubTags.length == 0) {
      // This might be a downloadable font. Check if we have it.
      FontFamily downloadedFont = getDownloadableFont.apply(fontRefName);

      DownloadableFontCacheService fontCacheService = DownloadableFontCacheService.getInstance();
      @SuppressWarnings("ConstantConditions")
      Predicate<FontDetail> exists = font -> fontCacheService.getCachedFontFile(font).exists();

      fontStream = downloadedFont != null ? downloadedFont.getFonts().stream()
        .filter(exists)
        .map(font -> new String[]{fontRefName, font.getFontStyle()}) : Stream.empty();
    }
    else {
      fontStream = Arrays.stream(fontSubTags)
        .map(font -> new String[]{font.getAttributeValue("font", ANDROID_URI), "normal"})
        .filter(font -> StringUtil.isNotEmpty(font[0]));
    }

    boolean[] hasElements = new boolean[1];
    String fontColor = '#' + ColorUtil.toHex(UIUtil.getLabelForeground());
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

  public static boolean needSave(@Nullable ResourceFolderType type) {
    // Only layouts are delegates to the LayoutlibCallback#getParser where we can supply a
    // parser directly from the live document; others read contents from disk via layoutlib.
    // TODO: Work on adding layoutlib support for this.
    return type != ResourceFolderType.LAYOUT;
  }

  public static void saveFileIfNecessary(PsiFile psiFile) {
    if (!needSave(IdeResourcesUtil.getFolderType(psiFile.getVirtualFile()))) { // Avoid need for read lock in get parent
      return;
    }

    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return;
    }

    final FileDocumentManager fileManager = FileDocumentManager.getInstance();
    if (!fileManager.isFileModified(file)) {
      return;
    }

    final com.intellij.openapi.editor.Document document;
    document = fileManager.getCachedDocument(file);
    if (document == null || !fileManager.isDocumentUnsaved(document)) {
      return;
    }

    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> application.runWriteAction(() -> fileManager.saveDocument(document)));
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
