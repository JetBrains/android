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

package com.android.tools.idea.javadoc;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;
import static com.android.tools.idea.rendering.StudioRenderServiceKt.taskBuilder;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.android.tools.idea.util.NonBlockingReadActionUtilKt.waitInterruptibly;
import static com.android.utils.SdkUtils.hasImageExtension;
import static com.intellij.codeInsight.documentation.DocumentationComponent.COLOR_KEY;
import static com.intellij.openapi.util.io.FileUtilRt.copy;
import static com.intellij.util.io.URLUtil.FILE_PROTOCOL;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.aar.AarResourceRepository;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.ShowFixFactory;
import com.android.tools.idea.rendering.StudioRenderService;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.idea.res.ResourceFilesUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceFolderRegistry;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.res.StateList;
import com.android.tools.idea.res.StateListState;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDocRenderer {

  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(@NotNull Module module, @NotNull ResourceType type, @NotNull String name, boolean framework) {
    return render(module, null, type, name, framework);
  }

  /** Renders the Javadoc for a resource of given type and name. If configuration is not null, it will be used to resolve the resource.  */
  @Nullable
  public static String render(@NotNull Module module, @Nullable Configuration configuration, @NotNull ResourceType type,
                              @NotNull String name, boolean framework) {
    return render(module, configuration, ResourceUrl.create(type, name, framework));
  }

  /** Renders the Javadoc for a resource of given type and name. If configuration is not null, it will be used to resolve the resource. */
  @Nullable
  public static String render(@NotNull Module module, @Nullable Configuration configuration, @NotNull ResourceUrl url) {
    ResourceValueRenderer renderer = ResourceValueRenderer.create(url.type, module, configuration);
    boolean framework = url.isFramework();
    if (renderer == null || framework && renderer.getFrameworkResources() == null || !framework && renderer.getAppResources() == null) {
      return null;
    }

    String valueDoc = renderer.render(url);
    if (url.type.equals(ResourceType.ATTR)) {
      String attrDoc = renderAttributeDoc(module, configuration, (url.isFramework() ? ANDROID_NS_NAME_PREFIX : "") + url.name);
      if (valueDoc == null) {
        return attrDoc;
      }
      return injectExternalDocumentation(attrDoc, valueDoc);
    }
    return valueDoc;
  }

  @NotNull
  private static String renderAttributeDoc(@NotNull Module module, @Nullable Configuration configuration, @NotNull String name) {
    AttributeDefinition def = ResolutionUtils.getAttributeDefinition(module, configuration, name);
    String doc = (def == null) ? null : def.getDescription(null);
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    builder.beginBold();
    builder.add(name);
    builder.endBold();
    int api = ResolutionUtils.getOriginalApiLevel(name, module.getProject());
    if (api > 1) {
      builder.add(" (Added in API level ");
      builder.add(String.valueOf(api));
      builder.add(")");
    }
    builder.addHtml("<br/>");
    if (!StringUtil.isEmpty(doc)) {
      builder.addHtml(doc);
      builder.addHtml("<br/>");
    }
    builder.addHtml("<hr/>");
    builder.closeHtmlBody();
    return builder.getHtml();
  }

  /** Combines external javadoc into the documentation rendered by the {@link #render} method */
  @Nullable
  public static String injectExternalDocumentation(@Nullable String rendered, @Nullable String external) {
    if (rendered == null) {
      return external;
    } else if (external == null) {
      return rendered;
    }

    // Strip out styles.
    int styleStart = external.indexOf("<style");
    int styleEnd = external.indexOf("</style>");
    if (styleStart != -1 && styleEnd != -1) {
      String style = external.substring(styleStart, styleEnd + "</style>".length());
      external = external.substring(0, styleStart) + external.substring(styleEnd + "</style>".length());
      // Insert into our own head
      int insert = rendered.indexOf("<body>");
      if (insert != -1) {
        int headEnd = rendered.lastIndexOf("</head>", insert);
        if (headEnd != -1) {
          insert = headEnd;
          rendered = rendered.substring(0, insert) + style + rendered.substring(insert);
        } else {
          rendered = rendered.substring(0, insert) + "<head>" + style + "</head>" + rendered.substring(insert);
        }
      }
    }

    // Strip out HTML tags from the external documentation
    external = getTagContent(getTagContent(external, "body"), "html");

    int bodyEnd = StringUtil.indexOfIgnoreCase(rendered, "</body>", 0);
    if (bodyEnd == -1) {
      bodyEnd = StringUtil.indexOfIgnoreCase(rendered, "</html>", 0);
    }

    if (bodyEnd != -1) {
      return rendered.substring(0, bodyEnd) + external + rendered.substring(bodyEnd);
    }
    // cant find any closing tags, so just append to the end
    return rendered + external;
  }

  /**
   * Gets the content of the tags, if the tags can not be found, then the input string is returned.
   */
  @NotNull
  private static String getTagContent(@NotNull String text, @NotNull String tag) {
    int start = StringUtil.indexOfIgnoreCase(text, "<" + tag, 0);
    int end = StringUtil.indexOfIgnoreCase(text, "</" + tag + ">", 0);
    if (start != -1 && end != -1) {
      start = StringUtil.indexOfIgnoreCase(text, ">", start);
      if (start != -1) {
        return text.substring(start + 1, end);
      }
    }
    return text;
  }

  private static abstract class ResourceValueRenderer implements ResourceItemResolver.ResourceProvider {
    protected final Module myModule;
    protected final Configuration myConfiguration;
    protected LocalResourceRepository myAppResources;
    protected ResourceResolver myResourceResolver;
    protected boolean mySmall;

    protected ResourceValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      myModule = module;
      myConfiguration = configuration;
    }

    public void setSmall(boolean small) {
      mySmall = small;
    }

    public abstract void renderToHtml(@NotNull HtmlBuilder builder, @NotNull ItemInfo item, @NotNull ResourceUrl url,
                                      boolean showResolution, @Nullable ResourceValue resourceValue);

    /** Creates a renderer suitable for the given resource type */
    @Nullable
    public static ResourceValueRenderer create(@NotNull ResourceType type, @NotNull Module module, @Nullable Configuration configuration) {
      switch (type) {
        case ATTR:
        case STRING:
        case DIMEN:
        case INTEGER:
        case BOOL:
        case STYLE:
          return new TextValueRenderer(module, configuration);
        case ARRAY:
          return new ArrayRenderer(module, configuration);
        case MIPMAP:
        case DRAWABLE:
          return new DrawableValueRenderer(module, configuration);
        case COLOR:
          return new ColorValueRenderer(module, configuration);
        default:
          // Ignore
          return null;
      }
    }

    @Nullable
    public String render(@NotNull ResourceUrl url) {
      List<ItemInfo> items = gatherItems(url);
      if (items != null) {
        Collections.sort(items);
        return renderKeyValues(items, url);
      }

      return null;
    }

    @Nullable
    private List<ItemInfo> gatherItems(@NotNull ResourceUrl url) {
      ResourceType type = url.type;
      String resourceName = url.name;
      boolean framework = url.isFramework();

      if (framework) {
        List<ItemInfo> results = new ArrayList<>();
        addItemsFromFramework(null, MASK_NORMAL, 0, type, resourceName, results);
        return results;
      }

      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return null;
      }

      List<ItemInfo> results = new ArrayList<>();

      LocalResourceRepository resources = getAppResources();

      List<AndroidFacet> dependencies =  AndroidDependenciesCache.getAllAndroidDependencies(myModule, true);
      int rank = 0;

      for (AndroidFacet reachableFacet : Iterables.concat(ImmutableList.of(facet), dependencies)) {
        String facetModuleName = reachableFacet.getHolderModule().getName();
        SourceProviders sourceProviders = SourceProviders.getInstance(reachableFacet);
        Set<NamedIdeaSourceProvider> selectedProviders = new HashSet<>();
        for (NamedIdeaSourceProvider sourceProvider : ImmutableList.copyOf(sourceProviders.getCurrentSourceProviders()).reverse()) {
          addItemsFromSourceSet(sourceProvider.getName() + " (" + facetModuleName + ")", MASK_FLAVOR_SELECTED, rank++, sourceProvider, type,
                                resourceName, results, reachableFacet);
          selectedProviders.add(sourceProvider);
        }
        for (NamedIdeaSourceProvider sourceProvider : ImmutableList.copyOf(sourceProviders.getCurrentAndSomeFrequentlyUsedInactiveSourceProviders()).reverse()) {
          if (!selectedProviders.contains(sourceProvider)) {
            addItemsFromSourceSet(sourceProvider.getName() + " (" + facetModuleName + ")", MASK_NORMAL, rank++, sourceProvider, type,
                                  resourceName, results, reachableFacet);
            selectedProviders.add(sourceProvider);
          }
        }
      }

      if (resources != null) {
        // Go through all the binary libraries and look for additional resources there
        for (AarResourceRepository dependency : StudioResourceRepositoryManager.getInstance(facet).getLibraryResources()) {
          addItemsFromRepository(dependency.getDisplayName(), MASK_NORMAL, rank++, dependency, false, type, resourceName, results);
        }
      }

      return results;
    }

    private static void addItemsFromSourceSet(@Nullable String flavor,
                                              int mask,
                                              int rank,
                                              @NotNull NamedIdeaSourceProvider sourceProvider,
                                              @NotNull ResourceType type,
                                              @NotNull String name,
                                              @NotNull List<ItemInfo> results,
                                              @NotNull AndroidFacet facet) {
      Iterable<VirtualFile> resDirectories = sourceProvider.getResDirectories();
      for (VirtualFile dir : resDirectories) {
        ResourceFolderRepository resources = ResourceFolderRegistry.getInstance(facet.getModule().getProject()).get(facet, dir);
        addItemsFromRepository(flavor, mask, rank, resources, false, type, name, results);
      }
    }

    private void addItemsFromFramework(@Nullable String flavor,
                                       int mask,
                                       int rank,
                                       @NotNull ResourceType type,
                                       @NotNull String name,
                                       @NotNull List<ItemInfo> results) {
      ResourceRepository frameworkResources = getFrameworkResources();
      if (frameworkResources == null) {
        return;
      }

      addItemsFromRepository(flavor, mask, rank, frameworkResources, true, type, name, results);
    }

    private static void addItemsFromRepository(@Nullable String flavor,
                                               int mask,
                                               int rank,
                                               @NotNull ResourceRepository resources,
                                               boolean isFramework,
                                               @NotNull ResourceType type,
                                               @NotNull String name,
                                               @NotNull List<ItemInfo> results) {
      ResourceNamespace namespace = isFramework ? ResourceNamespace.ANDROID : ResourceNamespace.TODO();
      List<ResourceItem> items = resources.getResources(namespace, type, name);
      for (ResourceItem item : items) {
        String folderName = null;
        PathString source = item.getSource();
        if (source != null) {
          folderName = source.getParentFileName();
        }
        if (folderName == null) {
          folderName = "?";
        }
        String folder = renderFolderName(folderName);
        ResourceValue value = item.getResourceValue();
        ItemInfo info = new ItemInfo(value, item.getConfiguration(), folder, flavor, rank, mask);
        results.add(info);
      }
    }

    @Nullable
    private String renderKeyValues(@NotNull List<ItemInfo> items, @NotNull ResourceUrl url) {
      if (items.isEmpty()) {
        return null;
      }

      markHidden(items);

      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody();
      if (items.size() == 1) {
        renderToHtml(builder, items.get(0), url, true, items.get(0).value);
      } else {
        builder.beginTable("valign=\"top\"");

        boolean haveFlavors = haveFlavors(items);
        if (haveFlavors) {
          builder.addTableRow(true, "Flavor/Library", "Configuration", "Value");
        } else {
          builder.addTableRow(true, "Configuration", "Value");
        }

        String prevFlavor = null;
        boolean showResolution = true;
        for (ItemInfo info : items) {
          String folder = info.folder;
          String flavor = StringUtil.notNullize(info.flavor);
          if (flavor.equals(prevFlavor)) {
            flavor = "";
          } else {
            prevFlavor = flavor;
          }

          builder.addHtml("<tr>");
          if (haveFlavors) {
            // Bold selected flavors?
            String style = ( (info.displayMask & MASK_FLAVOR_SELECTED) != 0) ? "b" : null;
            addTableCell(builder, style, flavor, null, null, false);
          }
          addTableCell(builder, null, folder, null, null, false);
          String style = ( (info.displayMask & MASK_ITEM_HIDDEN) != 0) ? "s" : null;
          addTableCell(builder, style, null, info, url, showResolution);
          showResolution = false; // Only show for first item
          builder.addHtml("</tr>");
        }

        builder.endTable();
      }
      builder.closeHtmlBody();
      return builder.getHtml();
    }

    private void addTableCell(@NotNull HtmlBuilder builder,
                              @Nullable String attribute,
                              @Nullable String text,
                              @Nullable ItemInfo info,
                              @Nullable ResourceUrl url,
                              boolean showResolution) {
      builder.addHtml("<td valign=\"top\">");
      if (attribute != null) {
        builder.addHtml("<").addHtml(attribute).addHtml(">");
      }

      if (text != null) {
        builder.add(text);
      } else {
        assert info != null;
        assert url != null;
        renderToHtml(builder, info, url, showResolution, info.value);
      }

      if (attribute != null) {
        builder.addHtml("</").addHtml(attribute).addHtml(">");
      }
      builder.addHtml("</td>");
    }

    @NotNull
    protected ResourceItemResolver createResolver(@NotNull ItemInfo item) {
      return createResolver(item.value, item.configuration);
    }

    @NotNull
    protected ResourceItemResolver createResolver(@Nullable ResourceValue value, @NotNull FolderConfiguration configuration) {
      ResourceItemResolver resolver = new ResourceItemResolver(configuration, this, null);
      List<ResourceValue> lookupChain = new ArrayList<>();
      lookupChain.add(value);
      resolver.setLookupChainList(lookupChain);
      return resolver;
    }

    protected void displayChain(@NotNull ResourceUrl url, @NotNull List<ResourceValue> lookupChain,
                                @NotNull HtmlBuilder builder, boolean newlineBefore, boolean newlineAfter) {
      if (!lookupChain.isEmpty()) {
        if (newlineBefore) {
          builder.newline();
        }
        String text = ResourceItemResolver.getDisplayString(url.toString(), lookupChain);
        builder.add(text);
        builder.newline();
        if (newlineAfter) {
          builder.newline();
        }
      }
    }

    // ---- Implements ResourceItemResolver.ResourceProvider ----

    @Override
    @Nullable
    public ResourceRepository getFrameworkResources() {
      StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myModule);
      return repositoryManager == null ? null : repositoryManager.getFrameworkResources(ImmutableSet.of());
    }

    @Override
    @Nullable
    public LocalResourceRepository getAppResources() {
      if (myAppResources == null) {
        myAppResources = StudioResourceRepositoryManager.getAppResources(myModule);
      }

      return myAppResources;
    }

    @Override
    @Nullable
    public ResourceResolver getResolver(boolean createIfNecessary) {
      if (myResourceResolver == null && createIfNecessary) {
        if (myConfiguration != null) {
          myResourceResolver = myConfiguration.getResourceResolver();
          return myResourceResolver;
        }

        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          VirtualFile layout = IdeResourcesUtil.pickAnyLayoutFile(facet);
          if (layout != null) {
            Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout);
            myResourceResolver = configuration.getResourceResolver();
          }
        }
      }

      return myResourceResolver;
    }

    public static void renderError(@NotNull HtmlBuilder builder, @Nullable String error) {
      builder.beginColor(JBColor.RED);
      builder.addBold(error == null ? "Error" : error);
      builder.endColor();
    }
  }

  private static boolean haveFlavors(List<ItemInfo> items) {
    for (ItemInfo info : items) {
      if (info.flavor != null) {
        return true;
      }
    }

    return false;
  }

  private static void markHidden(List<ItemInfo> items) {
    Set<String> hiddenQualifiers = new HashSet<>();
    for (ItemInfo info : items) {
      String folder = info.folder;

      if (hiddenQualifiers.contains(folder)) {
        info.displayMask |= MASK_ITEM_HIDDEN;
      }
      hiddenQualifiers.add(folder);
    }
  }

  private static String renderFolderName(String name) {
    String prefix = SdkConstants.FD_RES_VALUES;

    if (name.equals(prefix)) {
      return "Default";
    }

    if (name.startsWith(prefix + '-')) {
      return name.substring(prefix.length() + 1);
    } else {
      return name;
    }
  }

  private static class TextValueRenderer extends ResourceValueRenderer {
    private TextValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    private static String resolveStringValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue itemValue, @NotNull ResourceUrl url) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(new ArrayList<>());

      if (itemValue != null) {
        String value = itemValue.getValue();
        if (value != null) {
          ResourceUrl parsed = ResourceUrl.parse(value);
          if (parsed != null) {
            ResourceValue v = new ResourceValueImpl(urlToReference(url), null);
            v.setValue(url.toString());
            ResourceValue resourceValue = resolver.resolveResValue(v);
            if (resourceValue.getValue() != null) {
              return resourceValue.getValue();
            }
          }
          return value;
        } else {
          ResourceValue v = new ResourceValueImpl(urlToReference(url), null);
          v.setValue(url.toString());
          ResourceValue resourceValue = resolver.resolveResValue(v);
          if (resourceValue.getValue() != null) {
            return resourceValue.getValue();
          } else if (resourceValue instanceof StyleResourceValue) {
            return resourceValue.getResourceUrl().toString();
          }

          return url.toString();
        }
      }
      return null;
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      String value = resolveStringValue(resolver, resourceValue, url);
      List<ResourceValue> lookupChain = resolver.getLookupChain();

      if (value != null) {
        boolean found = false;
        if (url.isTheme()) {
          // If it's a theme attribute such as ?foo, it might resolve to a value we can
          // preview in a better way, such as a drawable, color or array. In that case,
          // look at the resolution chain and figure out the type of the resolved value,
          // and if appropriate, append a customized rendering.
          if (value.startsWith("#")) {
            Color color = IdeResourcesUtil.parseColor(value);
            if (color != null) {
              found = true;
              ResourceValueRenderer renderer = ResourceValueRenderer.create(ResourceType.COLOR, myModule, myConfiguration);
              assert renderer != null;
              ResourceValueImpl resolved = new ResourceValueImpl(urlToReference(url), null);
              resolved.setValue(value);
              renderer.renderToHtml(builder, item, url, false, resolved);
              builder.newline();
            }
          } else if (value.endsWith(DOT_PNG)) {
            if (ResourceFilesUtil.isFileResource(value)) {
              found = true;
              ResourceValueRenderer renderer = ResourceValueRenderer.create(ResourceType.DRAWABLE, myModule, myConfiguration);
              assert renderer != null;
              ResourceValueImpl resolved = new ResourceValueImpl(urlToReference(url), null);
              resolved.setValue(value);
              renderer.renderToHtml(builder, item, url, false, resolved);
              builder.newline();
            }
          }

          if (!found) {
            assert lookupChain != null;
            for (int i = lookupChain.size(); --i >= 0;) {
              ResourceValue rv = lookupChain.get(i);
              if (rv != null) {
                String value2 = rv.getValue();
                if (value2 != null) {
                  ResourceUrl resourceUrl = ResourceUrl.parse(value2, rv.isFramework());
                  if (resourceUrl != null && !resourceUrl.isTheme()) {
                    ResourceValueRenderer renderer = create(resourceUrl.type, myModule, myConfiguration);
                    if (renderer != null && renderer.getClass() != this.getClass()) {
                      found = true;
                      ResourceValue resolved = new ResourceValueImpl(urlToReference(resourceUrl), null);
                      resolved.setValue(value);
                      renderer.renderToHtml(builder, item, resourceUrl, false, resolved);
                      builder.newline();
                      break;
                    }
                  }
                }
              }
            }
          }
        }

        if (!found && (!showResolution || lookupChain == null || lookupChain.isEmpty())) {
          builder.add(value);
        }
      } else if (item.value != null && item.value.getValue() != null) {
        builder.add(item.value.getValue());
      }

      if (showResolution) {
        assert lookupChain != null;
        displayChain(url, lookupChain, builder, true, true);

        if (!lookupChain.isEmpty()) {
          // See if we resolved to a style; if so, show its attributes
          ResourceValue rv = lookupChain.get(lookupChain.size() - 1);
          if (rv instanceof StyleResourceValue) {
            StyleResourceValue srv = (StyleResourceValue)rv;
            displayStyleValues(builder, item, resolver, srv);
          }
        }
      }
    }

    private void displayStyleValues(HtmlBuilder builder, ItemInfo item, ResourceItemResolver resolver, StyleResourceValue styleValue) {
      List<ResourceValue> lookupChain = resolver.getLookupChain();
      builder.addHtml("<hr>");
      builder.addBold(styleValue.getName()).add(":").newline();

      Set<String> visitedStyleValues = new HashSet<>();
      Set<String> masked = new HashSet<>();
      while (styleValue != null) {
        if (!visitedStyleValues.add(styleValue.asReference().getQualifiedName())) {
          // We have detected a loop in the styles inheritance
          break;
        }

        // Make sure the contents for the style are always generated in the same order. Helps with testing and the
        // user will know where to find attributes.
        ImmutableList<StyleItemResourceValue> values = Ordering.usingToString().immutableSortedCopy(styleValue.getDefinedItems());
        for (StyleItemResourceValue itemResourceValue : values) {
          String name = itemResourceValue.getAttrName();
          if (masked.add(name)) {
            String value = null;
            ResourceReference attr = itemResourceValue.getAttr();
            if (attr != null) {
              ResourceValue v = styleValue.getItem(attr);
              if (v != null) {
                value = v.getValue();
              }
            }

            builder.addNbsps(4);
            if (attr != null && attr.getNamespace() == ResourceNamespace.ANDROID) {
              // TODO: namespaces
              builder.add(PREFIX_ANDROID);
            }
            builder.addBold(name).add(" = ").add(value == null ? "null" : value);
            if (value != null) {
              ResourceUrl url = ResourceUrl.parse(value, styleValue.isFramework());
              if (url != null) {
                ResourceUrl resolvedUrl = url;
                int count = 0;
                while (resolvedUrl != null) {
                  if (lookupChain != null) {
                    lookupChain.clear();
                  }
                  ResourceValue resourceValue;
                  if (resolvedUrl.isTheme()) {
                    ResourceReference ref =
                      new ResourceReference(ResourceNamespace.fromBoolean(resolvedUrl.isFramework()), ResourceType.ATTR, resolvedUrl.name);
                    resourceValue = resolver.findItemInTheme(ref);
                  }
                  else {
                    resourceValue = resolver.findResValue(resolvedUrl.toString(), resolvedUrl.isFramework());
                  }
                  if (resourceValue == null || resourceValue.getValue() == null) {
                    break;
                  }
                  url = resolvedUrl;
                  value = resourceValue.getValue();
                  resolvedUrl = ResourceUrl.parse(value, resolvedUrl.isFramework());
                  if (count++ == MAX_RESOURCE_INDIRECTION) { // prevent deep recursion (likely an invalid resource cycle)
                    break;
                  }
                }

                ResourceValueRenderer renderer = create(url.type, myModule, myConfiguration);
                if (renderer != null && renderer.getClass() != this.getClass()) {
                  builder.newline();
                  renderer.setSmall(true);
                  ResourceValue resolved = new ResourceValueImpl(urlToReference(url), value);
                  renderer.renderToHtml(builder, item, url, false, resolved);
                }
                else {
                  builder.add(" => ");

                  // AAR Library? Strip off prefix
                  int index = value.indexOf(FilenameConstants.EXPLODED_AAR);
                  if (index != -1) {
                    value = value.substring(index + FilenameConstants.EXPLODED_AAR.length() + 1);
                  }

                  builder.add(value);
                  builder.newline();
                }
              }
              else {
                builder.newline();
              }
            }
            else {
              builder.newline();
            }
          }
        }

        styleValue = resolver.getParent(styleValue);
        if (styleValue != null) {
          builder.newline();
          builder.add("Inherits from: ").add(styleValue.getResourceUrl().toString()).add(":").newline();
        }
      }
    }
  }

  @NotNull
  private static ResourceReference urlToReference(ResourceUrl url) {
    // TODO: namespaces.
    return new ResourceReference(ResourceNamespace.fromBoolean(url.isFramework()), url.type, url.name);
  }

  private static class ArrayRenderer extends ResourceValueRenderer {
    private ArrayRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    private static ResourceValue resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value) {
      if (value != null) {
        assert resolver.getLookupChain() != null;
        resolver.setLookupChainList(new ArrayList<>());
        return resolver.resolveResValue(value);
      }
      return null;
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);
      ResourceValue value = resolveValue(resolver, resourceValue);
      if (value instanceof ArrayResourceValue) {
        ArrayResourceValue arv = (ArrayResourceValue)value;
        builder.add(Joiner.on(", ").skipNulls().join(arv));
      } else if (value != null) {
        builder.add(value.toString());
      }

      if (showResolution) {
        List<ResourceValue> lookupChain = resolver.getLookupChain();
        assert lookupChain != null;
        // For arrays we end up pointing to the first element with PsiResourceItem.getValue, so only show the
        // resolution chain if it reveals something interesting (e.g. intermediate aliases)
        if (lookupChain.size() > 1) {
          displayChain(url, lookupChain, builder, true, false);
        }
      }
    }
  }

  private static class DrawableValueRenderer extends ResourceValueRenderer {
    private DrawableValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    private static ResourceValue resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(new ArrayList<>());
      if (value != null) {
        value = resolver.resolveResValue(value);
      }
      return value;
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      FolderConfiguration configuration = item.configuration;
      DensityQualifier densityQualifier = configuration.getDensityQualifier();
      ResourceItemResolver resolver;
      if (!ResourceQualifier.isValid(densityQualifier)) {
        // default to mdpi for when we show images in a not-dpi specific mode, (e.g. when showing a drawable statelist)
        densityQualifier = new DensityQualifier(Density.MEDIUM);
        // we need to make a copy of the FolderConfiguration, as we we change the actual one, it will chance for model inside the IDE
        configuration = FolderConfiguration.copyOf(item.configuration);
        // if we don't have a densityQualifier, we need to set one, as otherwise the resolver will return random dpi images.
        configuration.setDensityQualifier(densityQualifier);
      }
      resolver = createResolver(item.value, configuration);

      resourceValue = resolveValue(resolver, resourceValue);
      assert resolver.getLookupChain() != null;
      List<ResourceValue> lookupChain = new ArrayList<>(resolver.getLookupChain());

      if (resourceValue != null) {
        renderDrawableToHtml(resolver, builder, resourceValue, showResolution, configuration, 0);
      }
      else if (item.value != null) {
        renderError(builder, item.value.getValue());
      }

      if (showResolution) {
        displayChain(url, lookupChain, builder, true, false);
      }
    }

    private void renderDrawableToHtml(@NotNull ResourceItemResolver resolver,
                                      @NotNull HtmlBuilder builder,
                                      @NotNull ResourceValue resolvedValue,
                                      boolean showResolution,
                                      @NotNull FolderConfiguration configuration,
                                      int depth) {

      if (depth >= MAX_RESOURCE_INDIRECTION) {
        // user error
        renderError(builder, "Resource indirection too deep; might be cyclical");
        return;
      }

      StateList stateList = IdeResourcesUtil.resolveStateList(resolver, resolvedValue, myModule.getProject());
      if (stateList != null) {
        List<StateListState> states = stateList.getStates();
        if (states.isEmpty()) {
          // user error
          renderError(builder, "Empty StateList");
        }
        else {
          builder.addHtml("<table>");
          for (StateListState state : states) {
            builder.addHtml("<tr>");
            builder.addHtml("<td>");

            boolean oldSmall = mySmall;
            mySmall = true;

            ResourceValue resolvedStateResource = resolver.findResValue(state.getValue(), false);
            List<ResourceValue> lookupChain = null;
            if (resolvedStateResource != null) {
              resolvedStateResource = resolveValue(resolver, resolvedStateResource);
              assert resolver.getLookupChain() != null;
              lookupChain = showResolution ? new ArrayList<>(resolver.getLookupChain()) : null;
            }
            if (resolvedStateResource != null) {
              renderDrawableToHtml(resolver, builder, resolvedStateResource, showResolution, configuration, depth + 1);
            }
            else {
              renderError(builder, state.getValue()); // user error, can't find image
            }

            mySmall = oldSmall;

            builder.addHtml("</td>");

            builder.addHtml("<td>");
            builder.addHtml(state.getDescription());
            builder.addHtml("</td>");

            if (lookupChain != null) {
              builder.addHtml("<td>");
              ResourceUrl resUrl = ResourceUrl.parse(state.getValue());
              assert resUrl != null;
              displayChain(resUrl, lookupChain, builder, true, false);
              builder.addHtml("</td>");
            }

            builder.addHtml("</tr>");
          }
          builder.addHtml("</table>");
        }
      }
      else {
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        assert facet != null;
        FolderConfiguration folderConfiguration = ResolutionUtils.getFolderConfiguration(facet, resolvedValue, configuration);
        DensityQualifier densityQualifier = folderConfiguration.getDensityQualifier();
        if (!ResourceQualifier.isValid(densityQualifier)) {
          densityQualifier = configuration.getDensityQualifier();
          assert ResourceQualifier.isValid(densityQualifier); // this can never be null, as we have set this in the first renderToHtml method
        }
        String value = resolvedValue.getValue();
        assert value != null; // the value is always the path to the drawable file
        renderDrawableToHtml(builder, value, densityQualifier.getValue(), resolvedValue);
      }
    }

    private void renderDrawableToHtml(@NotNull HtmlBuilder builder, @NotNull String result, @NotNull Density density,
                                      @NotNull ResourceValue resolvedValue) {
      if (ResourceFilesUtil.isFileResource(result)) {
        VirtualFile file = toVirtualFile(ResourcesUtil.toFileResourcePathString(result));
        if (file == null) {
          renderError(builder, result);
        }
        else {
          renderDrawableToHtml(builder, file, density, resolvedValue);
        }
      }
      else if (result.startsWith("#")) {
        // A Drawable can also point to a color (but NOT a color state list).
        ColorValueRenderer colorRenderer = (ColorValueRenderer) ResourceValueRenderer.create(ResourceType.COLOR, myModule, myConfiguration);
        assert colorRenderer != null;
        colorRenderer.setSmall(mySmall);
        colorRenderer.renderColorToHtml(builder, result, 1f);
      }
      else {
        renderError(builder, result);
      }
    }

    private void renderDrawableToHtml(@NotNull HtmlBuilder builder, @NotNull VirtualFile virtualFile, @NotNull Density density,
                                      @NotNull ResourceValue resolvedValue) {
      String path = virtualFile.getPath();
      boolean isWebP = path.endsWith(DOT_WEBP);
      if (hasImageExtension(path) && !isWebP) { // webp: must render with layoutlib.
        File file;
        if (virtualFile.getFileSystem().getProtocol().equals(FILE_PROTOCOL)) {
          file = VfsUtilCore.virtualToIoFile(virtualFile);
        }
        else {
          try {
            file = FileUtilRt.createTempFile("render", DOT_PNG, true);
            try (InputStream input = virtualFile.getInputStream();
                OutputStream output = Files.newOutputStream(file.toPath(), StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
              copy(input, output);
            }
          }
          catch (IOException e) {
            renderError(builder, e.toString());
            return;
          }
        }

        URL fileUrl = fileToUrl(file);

        if (fileUrl != null) {
          builder.beginDiv("background-color:gray;padding:10px");
          builder.addImage(fileUrl, path);
          builder.endDiv();

          Dimension size = getSize(virtualFile);
          if (size != null) {
            builder.addHtml(String.format(Locale.US, "%1$d&#xd7;%2$d px (%3$d&#xd7;%4$d dp @ %5$s)", size.width, size.height,
                                          px2dp(size.width, density), px2dp(size.height, density), density.getResourceValue()));
          }
        }
      }
      else {
        if (myConfiguration != null) {
          AndroidFacet facet = AndroidFacet.getInstance(myModule);
          assert facet != null;
          final RenderService service = StudioRenderService.getInstance(myModule.getProject());
          RenderLogger logger = new RenderLogger(null, null, StudioFlags.NELE_LOG_ANDROID_FRAMEWORK.get(), ShowFixFactory.INSTANCE);
          CompletableFuture<RenderTask> renderTaskFuture = taskBuilder(service, facet, myConfiguration, logger).build();
          CompletableFuture<BufferedImage> future = renderTaskFuture.thenCompose(renderTask -> {
            if (renderTask == null) {
              return CompletableFuture.completedFuture(null);
            }
            renderTask.getLayoutlibCallback().setLogger(logger);

            // Find intrinsic size.
            int width = 100;
            int height = 100;
            if (isWebP) {
              Dimension size = getSize(virtualFile);
              if (size != null) {
                width = size.width;
                height = size.height;
              }
            }

            renderTask.setOverrideRenderSize(width, height);
            return renderTask.renderDrawable(resolvedValue).whenComplete((image, ex) -> renderTask.dispose());
          });
          BufferedImage image;
          try {
            image = waitInterruptibly(future);
          }
          catch (InterruptedException | ExecutionException e) {
            renderError(builder, e.toString());
            return;
          }
          if (image != null) {
            // Need to write it somewhere.
            try {
              File tempFile = FileUtilRt.createTempFile("render", DOT_PNG, true);
              boolean ok = ImageIO.write(image, "PNG", tempFile);
              if (ok) {
                URL fileUrl = fileToUrl(tempFile);
                if (fileUrl != null) {
                  builder.beginDiv("background-color:gray;padding:10px");
                  builder.addImage(fileUrl, null);
                  builder.endDiv();
                }
              }
            }
            catch (IOException e) {
              renderError(builder, e.toString());
            }
          } else {
            renderError(builder, "Couldn't render " + virtualFile);
          }
        } else {
          renderError(builder, path);
        }
      }
    }

    @Nullable
    private static URL fileToUrl(@NotNull File file) {
      try {
        return SdkUtils.fileToUrl(file);
      }
      catch (MalformedURLException e) {
        return null;
      }
    }

    private static int px2dp(int px, Density density) {
      return (int)((float)px * Density.MEDIUM.getDpiValue() / density.getDpiValue());
    }
  }

  private static class ColorValueRenderer extends ResourceValueRenderer {
    private ColorValueRenderer(@NotNull Module module, @Nullable Configuration configuration) {
      super(module, configuration);
    }

    @Nullable
    private static ResourceValue resolveValue(@NotNull ResourceItemResolver resolver, @Nullable ResourceValue value) {
      assert resolver.getLookupChain() != null;
      resolver.setLookupChainList(new ArrayList<>());
      if (value != null) {
        value = resolver.resolveResValue(value);
      }
      return value;
    }

    private static float resolveAlpha(@NotNull ResourceItemResolver resolver, @Nullable String alphaValue) {
      float alpha = 1.0f;
      if (alphaValue != null) {
        try {
          alpha = Float.parseFloat(IdeResourcesUtil.resolveStringValue(resolver, alphaValue));
        }
        catch (NumberFormatException ignored) { }
      }
      return alpha;
    }

    @Override
    public void renderToHtml(@NotNull HtmlBuilder builder,
                             @NotNull ItemInfo item,
                             @NotNull ResourceUrl url,
                             boolean showResolution,
                             @Nullable ResourceValue resourceValue) {
      ResourceItemResolver resolver = createResolver(item);

      resourceValue = resolveValue(resolver, resourceValue);
      assert resolver.getLookupChain() != null;
      List<ResourceValue> lookupChain = new ArrayList<>(resolver.getLookupChain());

      if (resourceValue != null) {
        renderColorToHtml(resolver, builder, resourceValue, showResolution, 1, 0);
      }
      else if (item.value != null) {
        renderError(builder, item.value.getValue());
      }

      if (showResolution) {
        displayChain(url, lookupChain, builder, true, false);
      }
    }

    private void renderColorToHtml(@NotNull ResourceItemResolver resolver, @NotNull HtmlBuilder builder, @NotNull ResourceValue resourceValue, boolean showResolution, float alpha, int depth) {

      if (depth >= MAX_RESOURCE_INDIRECTION) {
        // user error
        renderError(builder, "Resource indirection too deep; might be cyclical");
        return;
      }

      StateList stateList = IdeResourcesUtil.resolveStateList(resolver, resourceValue, myModule.getProject());
      if (stateList != null) {
        List<StateListState> states = stateList.getStates();
        if (states.isEmpty()) {
          // user error
          renderError(builder, "Empty StateList");
        }
        else {
          builder.addHtml("<table>");
          for (StateListState state : states) {
            builder.addHtml("<tr>");
            builder.addHtml("<td>");

            boolean oldSmall = mySmall;
            mySmall = true;

            float stateAlpha = resolveAlpha(resolver, state.getAlpha()) * alpha;
            ResourceValue resolvedStateResource = resolver.findResValue(state.getValue(), false);
            List<ResourceValue> lookupChain = null;
            if (resolvedStateResource != null) {
              resolvedStateResource = resolveValue(resolver, resolvedStateResource);
              assert resolver.getLookupChain() != null;
              lookupChain = showResolution ? new ArrayList<>(resolver.getLookupChain()) : null;
            }
            if (resolvedStateResource != null) {
              renderColorToHtml(resolver, builder, resolvedStateResource, showResolution, stateAlpha, depth + 1);
            }
            else {
              renderColorToHtml(builder, state.getValue(), stateAlpha);
            }

            mySmall = oldSmall;

            builder.addHtml("</td>");

            builder.addHtml("<td>");
            builder.addHtml(state.getDescription());
            builder.addHtml("</td>");

            if (lookupChain != null) {
              builder.addHtml("<td>");
              ResourceUrl resUrl = ResourceUrl.parse(state.getValue());
              assert resUrl != null;
              displayChain(resUrl, lookupChain, builder, true, false);
              builder.addHtml("</td>");
            }

            builder.addHtml("</tr>");
          }
          builder.addHtml("</table>");
        }
      }
      else {
        renderColorToHtml(builder, resourceValue.getValue(), alpha);
      }
    }

    private void renderColorToHtml(@NotNull HtmlBuilder builder, @Nullable String colorString, float alpha) {
      Color color = IdeResourcesUtil.parseColor(colorString);
      if (color == null) {
        // user error, they have a value that's not a color
        renderError(builder, colorString);
        return;
      }
      int combinedAlpha = (int)(color.getAlpha() * alpha);
      color = ColorUtil.toAlpha(color, IdeResourcesUtil.clamp(combinedAlpha, 0, 255));
      renderColorToHtml(builder, color);
    }

    public void renderColorToHtml(@NotNull HtmlBuilder builder, @NotNull Color color) {
      Color displayColor = color;

      int width = 200;
      int height = 100;
      if (mySmall) {
        int divisor = 3;
        width /= divisor;
        height /= divisor;
      }

      if (color.getAlpha() != 255) {
        // HTMLEditorKit does not support alpha in colors. When we have alpha, we manually do the blending to remove
        // the alpha from the color.
        float alpha = color.getAlpha() / 255f;
        Color backgroundColor = EditorColorsUtil.getGlobalOrDefaultColor(COLOR_KEY);
        if (backgroundColor != null) {
          //noinspection UseJBColor,AssignmentToMethodParameter
          color = new Color(
            (int)(backgroundColor.getRed() * (1f - alpha) + alpha * color.getRed()),
            (int)(backgroundColor.getGreen() * (1f - alpha) + alpha * color.getGreen()),
            (int)(backgroundColor.getBlue() * (1f - alpha) + alpha * color.getBlue())
          );
        }
      }

      String colorString = String.format(Locale.US, "rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
      String foregroundColor = ColorUtil.isDark(color) ? "white" : "black";
      String css = "background-color:" + colorString +
                   ";width:" + width + "px;text-align:center;vertical-align:middle;";
      // Use <table> tag such that we can center the color text (Java's HTML renderer doesn't support
      // vertical-align:middle on divs)
      builder.addHtml("<table style=\"" + css + "\" border=\"0\"><tr height=\"" + height + "\">");
      builder.addHtml("<td align=\"center\" valign=\"middle\" height=\"" + height + "\" style=\"color:" + foregroundColor + "\">");
      builder.addHtml(IdeResourcesUtil.colorToString(displayColor));
      builder.addHtml("</td></tr></table>");
    }
  }

  /**
   * Returns the dimensions of an Image if it can be obtained without fully reading it into memory.
   * This is a copy of the method in {@link com.android.tools.lint.checks.IconDetector}.
   */
  @Nullable
  private static Dimension getSize(@NotNull VirtualFile file) {
    try {
      ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream());
      if (input != null) {
        try {
          Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
          if (readers.hasNext()) {
            ImageReader reader = readers.next();
            try {
              reader.setInput(input);
              return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
              reader.dispose();
            }
          }
        } finally {
          input.close();
        }
      }

      // Fallback: read the image using the normal means
      //BufferedImage image = ImageIO.read(file);
      //if (image != null) {
      //  return new Dimension(image.getWidth(), image.getHeight());
      //} else {
      //  return null;
      //}
      return null;
    } catch (IOException e) {
      // Pass -- we can't handle all image types, warn about those we can
      return null;
    }
  }

  /** Normal display style */
  private static final int MASK_NORMAL = 0;
  /** Display style for flavor folders that are selected */
  private static final int MASK_FLAVOR_SELECTED = 1;
  /** Display style for items that are hidden by later resource folders */
  private static final int MASK_ITEM_HIDDEN = 2;

  /**
   * Information about {@link ResourceItem} instances to be displayed; in addition to the item and the
   * folder name, we can also record the flavor or library name, as well as display attributes indicating
   * whether the item is from a selected flavor, or whether the item is masked by a higher priority repository
   */
  private static class ItemInfo implements Comparable<ItemInfo> {
    @Nullable public final ResourceValue value;
    @NotNull public final FolderConfiguration configuration;
    @Nullable public final String flavor;
    @NotNull public final String folder;
    public final int rank;
    public int displayMask;

    private ItemInfo(@Nullable ResourceValue value, @NotNull FolderConfiguration configuration,
                     @NotNull String folder, @Nullable String flavor, int rank, int initialMask) {
      this.value = value;
      this.configuration = configuration;
      this.flavor = flavor;
      this.folder = folder;
      this.displayMask = initialMask;
      this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ItemInfo itemInfo = (ItemInfo)o;

      if (rank != itemInfo.rank) return false;
      if (!configuration.equals(itemInfo.configuration)) return false;
      if (!Objects.equals(flavor, itemInfo.flavor)) return false;
      if (!folder.equals(itemInfo.folder)) return false;
      return Objects.equals(value, itemInfo.value);
    }

    @Override
    public int hashCode() {
      int result = value != null ? value.hashCode() : 0;
      result = 31 * result + configuration.hashCode();
      result = 31 * result + (flavor != null ? flavor.hashCode() : 0);
      result = 31 * result + folder.hashCode();
      result = 31 * result + rank;
      return result;
    }

    @Override
    public int compareTo(@NotNull ItemInfo other) {
      if (rank != other.rank) {
        return rank - other.rank;
      }

      // Special case density: when we're showing multiple drawables for different densities,
      // sort by density value, not alphabetical name.
      DensityQualifier density1 = configuration.getDensityQualifier();
      DensityQualifier density2 = other.configuration.getDensityQualifier();
      if (density1 != null && density2 != null) {
        // Start with the lowest densities to avoid case where you have a giant asset (say xxxhdpi)
        // and you only see the top left corner in the documentation window.
        Density density1Value = density1.getValue() == null ? Density.MEDIUM : density1.getValue();
        Density density2Value = density2.getValue() == null ? Density.MEDIUM : density2.getValue();
        int delta = density2Value.compareTo(density1Value);
        if (delta != 0) {
          return delta;
        }
        if (density1Value == Density.MEDIUM && density1 != density2) {
          return density2 == density2.getNullQualifier() ? 1 : 0;
        }
      }

      return configuration.compareTo(other.configuration);
    }
  }
}
