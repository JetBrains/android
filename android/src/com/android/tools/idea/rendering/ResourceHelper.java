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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.editors.theme.StateListPicker;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.lang.databinding.DbUtil;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;

public class ResourceHelper {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ResourceHelper");
  public static final String STATE_NAME_PREFIX = "state_";

  /**
   * Returns true if the given style represents a project theme
   *
   * @param style a theme style string
   * @return true if the style string represents a project theme, as opposed
   *         to a framework theme
   */
  public static boolean isProjectStyle(@NotNull String style) {
    assert style.startsWith(STYLE_RESOURCE_PREFIX) || style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) : style;

    return style.startsWith(STYLE_RESOURCE_PREFIX);
  }

  /**
   * Returns the theme name to be shown for theme styles, e.g. for "@style/Theme" it
   * returns "Theme"
   *
   * @param style a theme style string
   * @return the user visible theme name
   */
  @NotNull
  public static String styleToTheme(@NotNull String style) {
    if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
      style = style.substring(STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      style = style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(PREFIX_RESOURCE_REF)) {
      // @package:style/foo
      int index = style.indexOf('/');
      if (index != -1) {
        style = style.substring(index + 1);
      }
    }
    return style;
  }

  /**
   * Is this a resource that can be defined in any file within the "values" folder?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well
   * as defined within a value XML file. This method will return true for these types
   * as well. In other words, a ResourceType can return true for both
   * {@link #isValueBasedResourceType} and {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type can be represented as a value under the
   *         values/ folder
   */
  public static boolean isValueBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType == ResourceFolderType.VALUES) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull VirtualFile file) {
    // Note that we use getBaseName here rather than {@link VirtualFile#getNameWithoutExtension}
    // because that method uses lastIndexOf('.') rather than indexOf('.') -- which means that
    // for a nine patch drawable it would include ".9" in the resource name
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull PsiFile file) {
    // See getResourceName(VirtualFile)
    // We're replicating that code here rather than just calling
    // getResourceName(file.getVirtualFile());
    // since file.getVirtualFile can return null
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource URL of the given file. The file <b>must</b> be a valid resource
   * file, meaning that it is in a proper resource folder, and it <b>must</b> be a
   * file-based resource (e.g. layout, drawable, menu, etc) -- not a values file.
   * <p>
   * For example, {@code getResourceUrl(</res/layout-land/foo.xml, false) = "@layout/foo"}.
   *
   * @param file the file to compute a resource url for
   * @return the resource url
   */
  @NotNull
  public static String getResourceUrl(@NotNull VirtualFile file) {
    ResourceFolderType type = ResourceFolderType.getFolderType(file.getParent().getName());
    assert type != null && type != ResourceFolderType.VALUES;
    return PREFIX_RESOURCE_REF + type.getName() + '/' + getResourceName(file);
  }

  /**
   * Is this a resource that is defined in a file named by the resource plus the XML
   * extension?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well as
   * defined within a value XML file along with other properties. This method will
   * return true for these resource types as well. In other words, a ResourceType can
   * return true for both {@link #isValueBasedResourceType} and
   * {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type is stored in a file named by the resource
   */
  public static boolean isFileBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType != ResourceFolderType.VALUES) {

        if (type == ResourceType.ID) {
          // The folder types for ID is not only VALUES but also
          // LAYOUT and MENU. However, unlike resources, they are only defined
          // inline there so for the purposes of isFileBasedResourceType
          // (where the intent is to figure out files that are uniquely identified
          // by a resource's name) this method should return false anyway.
          return false;
        }

        return true;
      }
    }

    return false;
  }

  @Nullable
  public static ResourceFolderType getFolderType(@Nullable final PsiFile file) {
    if (file != null) {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(new Computable<ResourceFolderType>() {
          @Nullable
          @Override
          public ResourceFolderType compute() {
            return getFolderType(file);
          }
        });
      }
      if (!file.isValid()) {
        return getFolderType(file.getVirtualFile());
      }
      PsiDirectory parent = file.getParent();
      if (parent != null) {
        return ResourceFolderType.getFolderType(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static ResourceFolderType getFolderType(@Nullable VirtualFile file) {
    if (file != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        return ResourceFolderType.getFolderType(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static FolderConfiguration getFolderConfiguration(@Nullable final PsiFile file) {
    if (file != null) {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(new Computable<FolderConfiguration>() {
          @Nullable
          @Override
          public FolderConfiguration compute() {
            return getFolderConfiguration(file);
          }
        });
      }
      if (!file.isValid()) {
        return getFolderConfiguration(file.getVirtualFile());
      }
      PsiDirectory parent = file.getParent();
      if (parent != null) {
        return FolderConfiguration.getConfigForFolder(parent.getName());
      }
    }

    return null;
  }

  @Nullable
  public static FolderConfiguration getFolderConfiguration(@Nullable VirtualFile file) {
    if (file != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        return FolderConfiguration.getConfigForFolder(parent.getName());
      }
    }

    return null;
  }

  /**
   * Returns all resource variations for the given file
   *
   * @param file resource file, which should be an XML file in one of the
   *            various resource folders, e.g. res/layout, res/values-xlarge, etc.
   * @param includeSelf if true, include the file itself in the list,
   *            otherwise exclude it
   * @return a list of all the resource variations
   */
  public static List<VirtualFile> getResourceVariations(@Nullable VirtualFile file, boolean includeSelf) {
    if (file == null) {
      return Collections.emptyList();
    }

    // Compute the set of layout files defining this layout resource
    List<VirtualFile> variations = new ArrayList<VirtualFile>();
    String name = file.getName();
    VirtualFile parent = file.getParent();
    if (parent != null) {
      VirtualFile resFolder = parent.getParent();
      if (resFolder != null) {
        String parentName = parent.getName();
        String prefix = parentName;
        int qualifiers = prefix.indexOf('-');

        if (qualifiers != -1) {
          parentName = prefix.substring(0, qualifiers);
          prefix = prefix.substring(0, qualifiers + 1);
        } else {
          prefix += '-';
        }
        for (VirtualFile resource : resFolder.getChildren()) {
          String n = resource.getName();
          if ((n.startsWith(prefix) || n.equals(parentName))
              && resource.isDirectory()) {
            VirtualFile variation = resource.findChild(name);
            if (variation != null) {
              if (!includeSelf && file.equals(variation)) {
                continue;
              }
              variations.add(variation);
            }
          }
        }
      }
    }

    return variations;
  }

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag
   *
   * @param fqcn the fully qualified class name, such as android.widget.Button
   * @return true if the full package path should be included in the layout XML element
   *         tag
   */
  public static boolean viewNeedsPackage(String fqcn) {
    return !(fqcn.startsWith(ANDROID_WIDGET_PREFIX) || fqcn.startsWith(ANDROID_VIEW_PKG) || fqcn.startsWith(ANDROID_WEBKIT_PKG));
  }

  /**
   * Tries to resolve the given resource value to an actual RGB color. For state lists
   * it will pick the simplest/fallback color.
   *
   * @param resources the resource resolver to use to follow color references
   * @param color the color to resolve
   * @param project the current project
   * @return the corresponding {@link Color} color, or null
   */
  @Nullable
  public static Color resolveColor(@NotNull RenderResources resources, @Nullable ResourceValue color, @NotNull Project project) {
    if (color != null) {
      color = resources.resolveResValue(color);
    }
    if (color == null) {
      return null;
    }

    final Iterator<String> iterator = new StateListIterable(resources, color, project, MAX_RESOURCE_INDIRECTION,
                                                            ATTR_COLOR, false).iterator();
    if (iterator.hasNext()) {
      return parseColor(iterator.next());
    } else {
      return null;
    }
  }

  /**
   * Configurable breadth-first traversal of resources through state-lists
   * (can be configured to traverse either all options or only default one)
   */
  private static class StateListIterable implements Iterable<String> {
    private final RenderResources myRenderResources;
    private final ResourceValue myStartValue;
    private final Project myProject;
    private final int myMaximumSteps;
    private final String myAttributeType;
    private final boolean myTraverseAllOptions;

    private StateListIterable(RenderResources renderResources,
                              ResourceValue startValue,
                              Project project,
                              int maximumSteps,
                              String attributeType,
                              boolean traverseAllOptions) {
      myRenderResources = renderResources;
      myAttributeType = attributeType;
      myStartValue = startValue;
      myMaximumSteps = maximumSteps;
      myTraverseAllOptions = traverseAllOptions;
      myProject = project;
    }

    @Override
    public Iterator<String> iterator() {
      return new StateListIterator(myStartValue);
    }

    private class StateListIterator implements Iterator<String> {
      private int myStepsMade = 0;

      // The queue contains items to be traversed. First component of a pair contains
      // resource (which is just a string w/ filepath, color or @-resource url).
      // Second element is a boolean flag whether it's a framework resource or not.
      // It's needed to call RenderResources.findResValue correctly
      private final Queue<Pair<String, Boolean>> myQueue = new LinkedList<Pair<String, Boolean>>();

      private String myLastValue = null;

      public StateListIterator(final ResourceValue value) {
        if (value == null) {
          return;
        }

        myQueue.add(Pair.create(value.getValue(), value.isFramework()));
        myLastValue = getNext();
      }

      private String getNext() {
        while (myStepsMade < myMaximumSteps && !myQueue.isEmpty()) {
          myStepsMade++;

          final Pair<String, Boolean> pair = myQueue.poll();
          final String value = pair.getFirst();
          if (value.startsWith(PREFIX_RESOURCE_REF)) {
            final ResourceUrl url = ResourceUrl.parse(value);
            if (url != null) {
              boolean isFramework = pair.getSecond() || url.framework;
              final ResourceValue resValue = myRenderResources.findResValue(value, isFramework);
              if (resValue != null) {
                myQueue.add(Pair.create(resValue.getValue(), isFramework));
                continue;
              }
            }
          } else {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(value);
            if (virtualFile != null) {
              PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myProject, virtualFile);
              if (psiFile instanceof XmlFile) {
                // Parse
                try {
                  XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
                  if (rootTag != null) {
                    final Boolean isFramework = pair.getSecond();
                    if (myTraverseAllOptions) {
                      for (final String nextValue : getAllFromStateList(rootTag, myAttributeType)) {
                        myQueue.add(Pair.create(nextValue, isFramework));
                      }
                    }
                    else {
                      final String nextValue = findInStateList(rootTag, myAttributeType);
                      if (!Strings.isNullOrEmpty(nextValue)) {
                        myQueue.add(Pair.create(nextValue, isFramework));
                      }
                    }
                    continue;
                  }
                }
                catch (Exception e) {
                  LOG.warn(String.format("Failed parsing state list file %1$s", virtualFile.getName()), e);
                }
              }
            }
          }

          // If we got here, then we have a value that we've failed to handle
          // Thus, we want to return this value to a user such that they can decide
          // what to do with it
          return value;
        }

        // for-loop is over, we either have exhausted queue or ran out of available steps
        return null;
      }

      @Override
      public boolean hasNext() {
        return myLastValue != null;
      }

      @Override
      public String next() {
        if (myLastValue == null) {
          throw new NoSuchElementException();
        }

        final String result = myLastValue;
        myLastValue = getNext();
        return result;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    }
  }

  /**
   * Tries to resolve colors from given resource value. When state list is encountered all
   * possibilities are explored.
   */
  @NotNull
  public static List<Color> resolveMultipleColors(@NotNull RenderResources resources, @Nullable ResourceValue color,
                                                  @NotNull Project project) {
    if (color != null) {
      color = resources.resolveResValue(color);
    }
    if (color == null) {
      return Collections.emptyList();
    }

    final List<Color> result = new ArrayList<Color>();
    for (final String maybeColor : new StateListIterable(resources, color, project, MAX_RESOURCE_INDIRECTION, ATTR_COLOR, true)) {
      if (maybeColor.startsWith("#")) {
        final Color parsedColor = parseColor(maybeColor);
        if (parsedColor != null) {
          result.add(parsedColor);
        }
      }
    }
    return result;
  }

  /**
   * Returns StateListPicker.StateList description of the statelist value, or null if value is not a statelist.
   */
  @Nullable
  public static StateListPicker.StateList resolveStateList(@NotNull RenderResources renderResources,
                                                           @NotNull ResourceValue value,
                                                           @NotNull Project project) {
    if (value.getValue().startsWith(PREFIX_RESOURCE_REF)) {
      final ResourceUrl url = ResourceUrl.parse(value.getValue());
      if (url != null) {
        final ResourceValue resValue = renderResources.findResValue(value.getValue(), value.isFramework());
        if (resValue != null) {
          return resolveStateList(renderResources, resValue, project);
        }
      }
    }
    else {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(value.getValue());
      if (virtualFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
        if (psiFile instanceof XmlFile) {
          // Parse
          try {
            ResourceFolderType folderType = ResourceFolderType.getFolderType(psiFile.getContainingDirectory().getName());
            XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
            if (rootTag != null && TAG_SELECTOR.equals(rootTag.getName())) {
              StateListPicker.StateList stateList = new StateListPicker.StateList(folderType);
              for (XmlTag subTag : rootTag.findSubTags(TAG_ITEM)) {
                stateList.addState(createStateListState(subTag, value.isFramework()));
              }
              return stateList;
            }
          }
          catch (IllegalArgumentException e) {
            LOG.error(String.format("%1$s is not a valid state list file", virtualFile.getName()));
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a StateListState representing the state in tag.
   */
  @NotNull
  private static StateListPicker.StateListState createStateListState(XmlTag tag, boolean isFramework) {
    String stateValue = null;
    String alphaValue = null;
    Map<String, Boolean> stateAttributes = new HashMap<String, Boolean>();
    XmlAttribute[] attributes = tag.getAttributes();
    for (XmlAttribute attr : attributes) {
      String name = attr.getLocalName();
      String value = attr.getValue();
      if (value != null && (SdkConstants.ATTR_COLOR.equals(name) || SdkConstants.ATTR_DRAWABLE.equals(name))) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        if (url != null) {
          stateValue = url.toString();
        }
        else {
          stateValue = value;
        }
      }
      else if (value != null && "alpha".equals(name)) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        alphaValue = url != null ? url.toString() : value;
      }
      else if (name.startsWith(STATE_NAME_PREFIX)) {
        stateAttributes.put(name, Boolean.valueOf(value));
      }
    }
    if (stateValue == null) {
      throw new IllegalArgumentException("Not a valid item");
    }
    return new StateListPicker.StateListState(stateValue, stateAttributes, alphaValue);
  }

  /**
   * Searches a color XML file for the color definition element that does not
   * have an associated state and returns its color
   */
  @Nullable
  private static String findInStateList(@NotNull XmlTag rootTag, String attributeName) {
    String color = null;
    for (XmlTag subTag : rootTag.findSubTags(TAG_ITEM)) {
      // Find non-state color definition
      String newColor = subTag.getAttributeValue(attributeName, ANDROID_URI);
      if (newColor == null) {
        continue;
      }
      else {
        color = newColor;
      }
      boolean hasState = false;
      XmlAttribute[] attributes = subTag.getAttributes();
      for (XmlAttribute attr : attributes) {
        if (attr.getName().startsWith(STATE_NAME_PREFIX)) {
          hasState = true;
          break;
        }
      }

      if (!hasState) {
        return color;
      }
    }

    // If no match, return the last mentioned item
    return color;
  }

  @NotNull
  private static List<String> getAllFromStateList(@NotNull XmlTag rootTag, final String attributeName) {
    XmlTag[] subTags = rootTag.findSubTags(TAG_ITEM);
    if (subTags.length == 0) {
      return Collections.emptyList();
    }

    final List<String> result = new ArrayList<String>();

    for (XmlTag tag : subTags) {
      final String value = tag.getAttributeValue(attributeName, ANDROID_URI);
      if (!Strings.isNullOrEmpty(value)) {
        result.add(value);
      }
    }

    return result;
  }

  /**
   * Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb to a Color
   * http://developer.android.com/guide/topics/resources/more-resources.html#Color
   */
  @Nullable
  public static Color parseColor(String s) {
    if (StringUtil.isEmpty(s)) {
      return null;
    }

    if (s.charAt(0) == '#') {
      long longColor;
      try {
        longColor = Long.parseLong(s.substring(1), 16);
      }
      catch (NumberFormatException e) {
        return null;
      }

      if (s.length() == 4 || s.length() == 5) {
        long a = s.length() == 4 ? 0xff : extend((longColor & 0xf000) >> 12);
        long r = extend((longColor & 0xf00) >> 8);
        long g = extend((longColor & 0x0f0) >> 4);
        long b = extend((longColor & 0x00f));
        longColor = (a << 24) | (r << 16) | (g << 8) | b;
        return new Color((int)longColor, true);
      }

      if (s.length() == 7) {
        longColor |= 0x00000000ff000000;
      }
      else if (s.length() != 9) {
        return null;
      }
      return new Color((int)longColor, true);
    }

    return null;
  }

  /**
   * Converts a color to hex-string representation, including alpha channel.
   * If alpha is FF then the output is #RRGGBB with no alpha component.
   */
  public static String colorToString(Color color) {
    long longColor = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    if (color.getAlpha() != 0xFF) {
      longColor |= (long)color.getAlpha() << 24;
      return String.format("#%08x", longColor);
    }
    else {
      return String.format("#%06x", longColor);
    }
  }

  private static long extend(long nibble) {
    return nibble | nibble << 4;
  }

  /**
   * Tries to resolve the given resource value to an actual drawable bitmap file. For state lists
   * it will pick the simplest/fallback drawable.
   *
   * @param resources the resource resolver to use to follow drawable references
   * @param drawable the drawable to resolve
   * @param project the current project
   * @return the corresponding {@link File}, or null
   */
  @Nullable
  public static File resolveDrawable(@NotNull RenderResources resources, @Nullable ResourceValue drawable, @NotNull Project project) {
    if (drawable != null) {
      drawable = resources.resolveResValue(drawable);
    }
    if (drawable == null) {
      return null;
    }

    final Iterator<String> iterator = new StateListIterable(resources, drawable, project, MAX_RESOURCE_INDIRECTION,
                                                            ATTR_DRAWABLE, false).iterator();
    if (iterator.hasNext()) {
      final String result = iterator.next();
      final File file = new File(result);
      return file.exists() ? file : null;
    } else {
      return null;
    }
  }

  /**
   * Tries to resolve the given resource value to an actual layout file.
   *
   * @param resources the resource resolver to use to follow layout references
   * @param layout the layout to resolve
   * @return the corresponding {@link File}, or null
   */
  @Nullable
  public static File resolveLayout(@NotNull RenderResources resources, @Nullable ResourceValue layout) {
    if (layout != null) {
      layout = resources.resolveResValue(layout);
    }
    if (layout == null) {
      return null;
    }
    String value = layout.getValue();

    int depth = 0;
    while (value != null && depth < MAX_RESOURCE_INDIRECTION) {
      if (value.startsWith(PREFIX_BINDING_EXPR)) {
        value = DbUtil.getBindingExprDefault(value);
        if (value == null) {
          return null;
        }
      }
      if (value.startsWith(PREFIX_RESOURCE_REF)) {
        boolean isFramework = layout.isFramework();
        layout = resources.findResValue(value, isFramework);
        if (layout != null) {
          value = layout.getValue();
        } else {
          break;
        }
      } else {
        File file = new File(value);
        if (file.exists()) {
          return file;
        } else {
          return null;
        }
      }

      depth++;
    }

    return null;
  }

  /**
   * Returns the given resource name, and possibly prepends a project-configured prefix to the name
   * if set on the Gradle module (but only if it does not already start with the prefix).
   *
   * @param module the corresponding module
   * @param name the resource name
   * @return the resource name, possibly with a new prefix at the beginning of it
   */
  @Contract("_, !null -> !null")
  @Nullable
  public static String prependResourcePrefix(@Nullable Module module, @Nullable String name) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        IdeaAndroidProject p = facet.getIdeaAndroidProject();
        if (p != null) {
          String resourcePrefix = LintUtils.computeResourcePrefix(p.getDelegate());
          if (resourcePrefix != null) {
            if (name != null) {
              return name.startsWith(resourcePrefix) ? name : LintUtils.computeResourceName(resourcePrefix, name);
            } else {
              return resourcePrefix;
            }
          }
        }
      }
    }

    return name;
  }
}
