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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.ui.resourcechooser.ResourceGroup;
import com.android.tools.idea.ui.resourcechooser.ResourceItem;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.*;

public class ResourceHelper {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.res.ResourceHelper");
  public static final String STATE_NAME_PREFIX = "state_";
  public static final String ALPHA_FLOATING_ERROR_FORMAT = "The alpha attribute in %1$s/%2$s does not resolve to a floating point number";
  public static final String DIMENSION_ERROR_FORMAT = "The specified dimension %1$s does not have a unit";

  private final static Pattern sFloatPattern = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)(.*)");
  private final static float[] sFloatOut = new float[1];

  private final static TypedValue sValue = new TypedValue();

  /**
   * Returns true if the given style represents a project theme
   *
   * @param styleResourceUrl a theme style resource url
   * @return true if the style string represents a project theme, as opposed
   *         to a framework theme
   */
  public static boolean isProjectStyle(@NotNull String styleResourceUrl) {
    return !styleResourceUrl.startsWith(ANDROID_STYLE_RESOURCE_PREFIX);
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
  public static ResourceFolderType getFolderType(@NotNull ResourceFile file) {
    File parent = file.getFile().getParentFile();
    if (parent != null) {
      return ResourceFolderType.getFolderType(parent.getName());
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
   * Package prefixes used in {@link #isViewPackageNeeded(String, int)}
   */
  private static final String[] NO_PREFIX_PACKAGES =
    new String[]{ANDROID_WIDGET_PREFIX, ANDROID_VIEW_PKG, ANDROID_WEBKIT_PKG};

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag. Package prefixes that allow class name to be
   * unqualified are specified in {@link #NO_PREFIX_PACKAGES} and should reflect a list
   * of prefixes from framework's LayoutInflater and PhoneLayoutInflater.
   *
   * @param qualifiedName the fully qualified class name, such as android.widget.Button
   * @param apiLevel The API level for the calling context. This is the max of the
   *                 project's minSdkVersion and the layout file's version qualifier, if any.
   *                 You can pass -1 if this is not known, which will force fully qualified
   *                 names on some packages which recently no longer require it.
   * @return true if the full package path should be included in the layout XML element
   *         tag
   */
  public static boolean isViewPackageNeeded(@NotNull String qualifiedName, int apiLevel) {
    for (String noPrefixPackage : NO_PREFIX_PACKAGES) {
      // We need to check not only if prefix is a "whitelisted" package, but if the class
      // is stored in that package directly, as opposed to be stored in a subpackage.
      // For example, view with FQCN android.view.MyView can be abbreviated to "MyView",
      // but android.view.custom.MyView can not.
      if (isDirectlyInPackage(qualifiedName, noPrefixPackage)) {
        return false;
      }
    }

    if (apiLevel >= 20) {
      // Special case: starting from API level 20, classes from "android.app" also inflated
      // without fully qualified names
      return !isDirectlyInPackage(qualifiedName, ANDROID_APP_PKG);
    }
    return true;
  }

  /**
   * XML tags associated with classes usually can come either with fully-qualified names, which can be shortened
   * in case of common packages, which is handled by various inflaters in Android framework. This method checks
   * whether a class with given qualified name can be shortened to a simple name, or is required to have
   * a package qualifier.
   * <p/>
   * Accesses JavaPsiFacade, and thus should be run inside read action.
   *
   * @see #isViewPackageNeeded(String, int)
   */
  public static boolean isClassPackageNeeded(@NotNull String qualifiedName, @NotNull PsiClass baseClass, int apiLevel) {
    final PsiClass viewClass =
      JavaPsiFacade.getInstance(baseClass.getProject()).findClass(CLASS_VIEW, GlobalSearchScope.allScope(baseClass.getProject()));

    if (viewClass != null && baseClass.isInheritor(viewClass, true)) {
      return isViewPackageNeeded(qualifiedName, apiLevel);
    } else if (CLASS_PREFERENCE.equals(baseClass.getQualifiedName())) {
      // Handled by PreferenceInflater in Android framework
      return !isDirectlyInPackage(qualifiedName, "android.preference.");
    } else {
      // TODO: removing that makes some of unit tests fail, but leaving it as it is can introduce buggy XML validation
      // Issue with further information: http://b.android.com/186559
      return !qualifiedName.startsWith(ANDROID_PKG_PREFIX);
    }
  }

  /**
   * Returns whether a class with given qualified name resides directly in a package with
   * given prefix (as opposed to reside in a subpackage).
   * <p/>
   * For example,
   * <ul>
   *   <li>isDirectlyInPackage("android.view.View", "android.view.") -> true</li>
   *   <li>isDirectlyInPackage("android.view.internal.View", "android.view.") -> false</li>
   * </ul>
   */
  public static boolean isDirectlyInPackage(@NotNull String qualifiedName, @NotNull String packagePrefix) {
    return qualifiedName.startsWith(packagePrefix) && qualifiedName.indexOf('.', packagePrefix.length() + 1) == -1;
  }

  /**
   * Tries to resolve the given resource value to an actual RGB color. For state lists
   * it will pick the simplest/fallback color.
   *
   * @param resources the resource resolver to use to follow color references
   * @param colorValue the color to resolve
   * @param project the current project
   * @return the corresponding {@link Color} color, or null
   */
  @Nullable
  public static Color resolveColor(@NotNull RenderResources resources, @Nullable ResourceValue colorValue, @NotNull Project project) {
    return resolveColor(resources, colorValue, project, 0);
  }

  @Nullable
  private static Color resolveColor(@NotNull RenderResources resources, @Nullable ResourceValue colorValue, @NotNull Project project, int depth) {

    if (depth >= MAX_RESOURCE_INDIRECTION) {
      LOG.warn("too deep " + colorValue);
      return null;
    }

    if (colorValue != null) {
      colorValue = resources.resolveResValue(colorValue);
    }
    if (colorValue == null) {
      return null;
    }

    StateList stateList = resolveStateList(resources, colorValue, project);
    if (stateList != null) {
      List<StateListState> states = stateList.getStates();

      if (states.isEmpty()) {
        // In the case of an empty selector, we don't want to crash.
        return null;
      }

      // Getting the last color of the state list, because it's supposed to be the simplest / fallback one
      StateListState state = states.get(states.size() - 1);

      Color stateColor = parseColor(state.getValue());
      if (stateColor == null) {
        stateColor = resolveColor(resources, resources.findResValue(state.getValue(), false), project, depth + 1);
      }
      if (stateColor == null) {
        return null;
      }
      try {
        return makeColorWithAlpha(resources, stateColor, state.getAlpha());
      }
      catch (NumberFormatException e) {
        // If the alpha value is not valid, Android uses 1.0
        LOG.warn(String.format("The alpha attribute in %s/%s does not resolve to a floating point number", stateList.getDirName(),
                               stateList.getFileName()));
        return stateColor;
      }
    }

    return parseColor(colorValue.getValue());
  }

  /**
   * Tries to resolve colors from given resource value. When state list is encountered all
   * possibilities are explored.
   */
  @NotNull
  public static List<Color> resolveMultipleColors(@NotNull RenderResources resources, @Nullable ResourceValue value,
                                                  @NotNull Project project) {
    return resolveMultipleColors(resources, value, project, 0);
  }

  /**
   * Tries to resolve colors from given resource value. When state list is encountered all
   * possibilities are explored.
   */
  @NotNull
  private static List<Color> resolveMultipleColors(@NotNull RenderResources resources, @Nullable ResourceValue value,
                                                  @NotNull Project project, int depth) {

    if (depth >= MAX_RESOURCE_INDIRECTION) {
      LOG.warn("too deep " + value);
      return Collections.emptyList();
    }

    if (value != null) {
      value = resources.resolveResValue(value);
    }
    if (value == null) {
      return Collections.emptyList();
    }

    final List<Color> result = new ArrayList<Color>();

    StateList stateList = resolveStateList(resources, value, project);
    if (stateList != null) {
      for (StateListState state : stateList.getStates()) {
        List<Color> stateColors;
        ResourceValue resolvedStateResource = resources.findResValue(state.getValue(), false);
        if (resolvedStateResource != null) {
          stateColors = resolveMultipleColors(resources, resolvedStateResource, project, depth + 1);
        }
        else {
          Color color = parseColor(state.getValue());
          stateColors = color == null ? Collections.<Color>emptyList() : ImmutableList.of(color);
        }
        for (Color color : stateColors) {
          try {
            result.add(makeColorWithAlpha(resources, color, state.getAlpha()));
          }
          catch (NumberFormatException e) {
            // If the alpha value is not valid, Android uses 1.0 so nothing more needs to be done, we simply take color as it is
            result.add(color);
            LOG.warn(String.format(ALPHA_FLOATING_ERROR_FORMAT, stateList.getDirName(), stateList.getFileName()));
          }
        }
      }
    }
    else {
      Color color = parseColor(value.getValue());
      if (color != null) {
        result.add(color);
      }
    }
    return result;
  }

  /**
   * Tries to resolve the given resource value to a dimension in pixels. The returned value is
   * function of the configuration's device's density.
   *
   * @param resources     the resource resolver to use to follow references
   * @param value         the dimension to resolve
   * @param configuration the device configuration
   * @return a dimension in pixels, or null
   */
  @Nullable
  @AndroidCoordinate
  public static Integer resolveDimensionPixelSize(@NotNull RenderResources resources, @NotNull String value,
                                                  @NotNull Configuration configuration) {
    String resValue = resolveStringValue(resources, value);
    ResourceHelper.TypedValue out = new ResourceHelper.TypedValue();
    if (parseFloatAttribute(resValue, out, true)) {
      return ResourceHelper.TypedValue.complexToDimensionPixelSize(out.data, configuration);
    }
    return null;
  }

  @NotNull
  public static String resolveStringValue(@NotNull RenderResources resources, @NotNull String value) {
    ResourceValue resValue = resources.findResValue(value, false);
    if (resValue == null) {
      return value;
    }
    ResourceValue finalValue = resources.resolveResValue(resValue);
    if (finalValue == null || finalValue.getValue() == null) {
      return value;
    }
    return finalValue.getValue();
  }

  private static final class UnitEntry {
    String name;
    int type;
    int unit;
    float scale;

    UnitEntry(String name, int type, int unit, float scale) {
      this.name = name;
      this.type = type;
      this.unit = unit;
      this.scale = scale;
    }
  }

  private final static UnitEntry[] sUnitNames = new UnitEntry[] {
    new UnitEntry("px",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PX,  1.0f),
    new UnitEntry("dip", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
    new UnitEntry("dp",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
    new UnitEntry("sp",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_SP,  1.0f),
    new UnitEntry("pt",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PT,  1.0f),
    new UnitEntry("in",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_IN,  1.0f),
    new UnitEntry("mm",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_MM,  1.0f)
  };

  /**
   * Container for a dynamically typed data value. Used to hold resources values.
   */
  public static class TypedValue {
    static final int TYPE_FLOAT = 0x04;
    static final int TYPE_DIMENSION = 0x05;

    static final int COMPLEX_UNIT_SHIFT = 0;
    static final int COMPLEX_UNIT_MASK = 0xf;

    static final int COMPLEX_UNIT_PX = 0;
    static final int COMPLEX_UNIT_DIP = 1;
    static final int COMPLEX_UNIT_SP = 2;
    static final int COMPLEX_UNIT_PT = 3;
    static final int COMPLEX_UNIT_IN = 4;
    static final int COMPLEX_UNIT_MM = 5;

    static final int COMPLEX_RADIX_SHIFT = 4;
    static final int COMPLEX_RADIX_MASK = 0x3;

    static final int COMPLEX_RADIX_23p0 = 0;
    static final int COMPLEX_RADIX_16p7 = 1;
    static final int COMPLEX_RADIX_8p15 = 2;
    static final int COMPLEX_RADIX_0p23 = 3;

    static final int COMPLEX_MANTISSA_SHIFT = 8;
    static final int COMPLEX_MANTISSA_MASK = 0xffffff;

    private static final float MANTISSA_MULT = 1.0f / (1 << COMPLEX_MANTISSA_SHIFT);
    private static final float[] RADIX_MULTS = new float[] {
      1.0f * MANTISSA_MULT, 1.0f / (1 << 7) * MANTISSA_MULT,
      1.0f / (1 << 15) * MANTISSA_MULT, 1.0f / (1 << 23) * MANTISSA_MULT
    };

    public int type;
    public int data;

    /**
     * Converts a complex data value holding a dimension to its final value
     * as an integer pixel size. A size conversion involves rounding the base
     * value, and ensuring that a non-zero base value is at least one pixel
     * in size. The given <var>data</var> must be structured as a
     * {@link #TYPE_DIMENSION}.
     *
     * @param data   A complex data value holding a unit, magnitude, and mantissa.
     * @param config The device configuration
     * @return The number of pixels specified by the data and its desired
     * multiplier and units.
     */
    public static int complexToDimensionPixelSize(int data, Configuration config) {
      final float value = complexToFloat(data);
      //noinspection PointlessBitwiseExpression
      final float f = applyDimension((data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK, value, config);
      final int res = (int)(f+0.5f);
      if (res != 0) return res;
      if (value == 0) return 0;
      if (value > 0) return 1;
      return -1;
    }

    /**
     * Retrieve the base value from a complex data integer.  This uses the
     * {@link #COMPLEX_MANTISSA_MASK} and {@link #COMPLEX_RADIX_MASK} fields of
     * the data to compute a floating point representation of the number they
     * describe.  The units are ignored.
     *
     * @param complex A complex data value.
     * @return A floating point value corresponding to the complex data.
     */
    static float complexToFloat(int complex) {
      return (complex&(COMPLEX_MANTISSA_MASK << COMPLEX_MANTISSA_SHIFT))
             * RADIX_MULTS[(complex>>COMPLEX_RADIX_SHIFT) & COMPLEX_RADIX_MASK];
    }

    /**
     * Converts an unpacked complex data value holding a dimension to its final floating
     * point value. The two parameters <var>unit</var> and <var>value</var>
     * are as in {@link #TYPE_DIMENSION}.
     *
     * @param unit   The unit to convert from.
     * @param value  The value to apply the unit to.
     * @param config The device configuration
     * @return The complex floating point value multiplied by the appropriate
     * metrics depending on its unit.
     */
    static float applyDimension(int unit, float value, Configuration config) {
      Device device = config.getDevice();
      float xdpi = 493.0f; // assume Nexus 6 density
      if (device != null) {
        xdpi = (float) device.getDefaultHardware().getScreen().getXdpi();
      }

      switch (unit) {
        case COMPLEX_UNIT_PX:
          return value;
        case COMPLEX_UNIT_DIP:
          return value * config.getDensity().getDpiValue() / 160.0f;
        case COMPLEX_UNIT_SP:
          return value * config.getDensity().getDpiValue() / 160.0f;
        case COMPLEX_UNIT_PT:
          return value * xdpi * (1.0f / 72.0f);
        case COMPLEX_UNIT_IN:
          return value * xdpi * (1.0f / 72.0f);
        case COMPLEX_UNIT_MM:
          return value * xdpi * (1.0f / 72.0f);
      }
      return 0;
    }
  }

  /**
   * Parse a float attribute and return the parsed value into a given TypedValue.
   *
   * @param value       the string value of the attribute
   * @param outValue    the TypedValue to receive the parsed value
   * @param requireUnit whether the value is expected to contain a unit.
   * @return true if success.
   */
  public static boolean parseFloatAttribute(@NotNull String value, TypedValue outValue, boolean requireUnit) {
    // remove the space before and after
    value = value.trim();
    int len = value.length();

    if (len <= 0) {
      return false;
    }

    // check that there's no non ascii characters.
    char[] buf = value.toCharArray();
    for (int i = 0 ; i < len ; i++) {
      if (buf[i] > 255) {
        return false;
      }
    }

    // check the first character
    if ((buf[0] < '0' || buf[0] > '9') && buf[0] != '.' && buf[0] != '-' && buf[0] != '+') {
      return false;
    }

    // now look for the string that is after the float...
    Matcher m = sFloatPattern.matcher(value);
    if (m.matches()) {
      String f_str = m.group(1);
      String end = m.group(2);

      float f;
      try {
        f = Float.parseFloat(f_str);
      } catch (NumberFormatException e) {
        // this shouldn't happen with the regexp above.
        return false;
      }

      if (end.length() > 0 && end.charAt(0) != ' ') {
        // Might be a unit...
        if (parseUnit(end, outValue, sFloatOut)) {
          computeTypedValue(outValue, f, sFloatOut[0]);
          return true;
        }
        return false;
      }

      // make sure it's only spaces at the end.
      end = end.trim();

      if (end.length() == 0) {
        if (outValue != null) {
          if (!requireUnit) {
            outValue.type = TypedValue.TYPE_FLOAT;
            outValue.data = Float.floatToIntBits(f);
          } else {
            // no unit when required? Use dp and out an error.
            applyUnit(sUnitNames[1], outValue, sFloatOut);
            computeTypedValue(outValue, f, sFloatOut[0]);

            LOG.warn(String.format(DIMENSION_ERROR_FORMAT, value));
          }
          return true;
        }
      }
    }

    return false;
  }

  private static void computeTypedValue(TypedValue outValue, float value, float scale) {
    value *= scale;
    boolean neg = value < 0;
    if (neg) {
      value = -value;
    }
    long bits = (long)(value*(1<<23)+.5f);
    int radix;
    int shift;
    if ((bits&0x7fffff) == 0) {
      // Always use 23p0 if there is no fraction, just to make
      // things easier to read.
      radix = TypedValue.COMPLEX_RADIX_23p0;
      shift = 23;
    } else if ((bits&0xffffffffff800000L) == 0) {
      // Magnitude is zero -- can fit in 0 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_0p23;
      shift = 0;
    } else if ((bits&0xffffffff80000000L) == 0) {
      // Magnitude can fit in 8 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_8p15;
      shift = 8;
    } else if ((bits&0xffffff8000000000L) == 0) {
      // Magnitude can fit in 16 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_16p7;
      shift = 16;
    } else {
      // Magnitude needs entire range, so no fractional part.
      radix = TypedValue.COMPLEX_RADIX_23p0;
      shift = 23;
    }
    int mantissa = (int)(
      (bits>>shift) & TypedValue.COMPLEX_MANTISSA_MASK);
    if (neg) {
      mantissa = (-mantissa) & TypedValue.COMPLEX_MANTISSA_MASK;
    }
    outValue.data |=
      (radix<<TypedValue.COMPLEX_RADIX_SHIFT)
      | (mantissa<<TypedValue.COMPLEX_MANTISSA_SHIFT);
  }

  private static boolean parseUnit(String str, TypedValue outValue, float[] outScale) {
    str = str.trim();

    for (UnitEntry unit : sUnitNames) {
      if (unit.name.equals(str)) {
        applyUnit(unit, outValue, outScale);
        return true;
      }
    }

    return false;
  }

  private static void applyUnit(UnitEntry unit, TypedValue outValue, float[] outScale) {
    outValue.type = unit.type;
    //noinspection PointlessBitwiseExpression
    outValue.data = unit.unit << TypedValue.COMPLEX_UNIT_SHIFT;
    outScale[0] = unit.scale;
  }

  @NotNull
  public static Color makeColorWithAlpha(@NotNull RenderResources resources, @NotNull Color color, @Nullable String alphaValue)
    throws NumberFormatException {
    float alpha = 1.0f;
    if (alphaValue != null) {
      alpha = Float.parseFloat(resolveStringValue(resources, alphaValue));
    }

    int combinedAlpha = (int)(color.getAlpha() * alpha);
    return ColorUtil.toAlpha(color, clamp(combinedAlpha, 0, 255));
  }

  /**
   * Returns a {@link StateList} description of the state list value, or null if value is not a state list.
   */
  @Nullable("if there is no statelist with this name")
  public static StateList resolveStateList(@NotNull RenderResources renderResources,
                                            @NotNull ResourceValue value,
                                            @NotNull Project project) {
    return resolveStateList(renderResources, value, project, 0);
  }

  @Nullable("if there is no statelist with this name")
  private static StateList resolveStateList(@NotNull RenderResources renderResources,
                                           @NotNull ResourceValue resourceValue,
                                           @NotNull Project project, int depth) {
    if (depth >= MAX_RESOURCE_INDIRECTION) {
      LOG.warn("too deep " + resourceValue);
      return null;
    }

    String value = resourceValue.getValue();
    if (value == null) {
      // Not all ResourceValue instances have values (e.g. StyleResourceValue)
      return null;
    }

    if (value.startsWith(PREFIX_RESOURCE_REF)) {
      final ResourceValue resValue = renderResources.findResValue(value, resourceValue.isFramework());
      if (resValue != null) {
        return resolveStateList(renderResources, resValue, project, depth + 1);
      }
    }
    else {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(value);
      if (virtualFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
        if (psiFile instanceof XmlFile) {
          // Parse
          XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
          if (rootTag != null && TAG_SELECTOR.equals(rootTag.getName())) {
            StateList stateList = new StateList(psiFile.getName(), psiFile.getContainingDirectory().getName());
            for (XmlTag subTag : rootTag.findSubTags(TAG_ITEM)) {
              final StateListState stateListState = createStateListState(subTag, resourceValue.isFramework());
              if (stateListState == null) {
                return null;
              }
              stateList.addState(stateListState);
            }
            return stateList;
          }
        }
      }
    }
    return null;
  }

  /**
   * Try to parse a state in the "item" tag. Only handles those items that have
   * either "android:color" or "android:drawable" attributes in "item" tag.
   *
   * @return {@link StateListState} representing the state in tag, null if parse is unsuccessful
   */
  @Nullable
  private static StateListState createStateListState(XmlTag tag, boolean isFramework) {
    String stateValue = null;
    String alphaValue = null;
    Map<String, Boolean> stateAttributes = new HashMap<String, Boolean>();
    XmlAttribute[] attributes = tag.getAttributes();
    for (XmlAttribute attr : attributes) {
      String name = attr.getLocalName();
      String value = attr.getValue();
      if (value == null) {
        continue;
      }
      if (ATTR_COLOR.equals(name) || ATTR_DRAWABLE.equals(name)) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        stateValue = url != null ? url.toString() : value;
      }
      else if ("alpha".equals(name)) {
        ResourceUrl url = ResourceUrl.parse(value, isFramework);
        alphaValue = url != null ? url.toString() : value;
      }
      else if (name.startsWith(STATE_NAME_PREFIX)) {
        stateAttributes.put(name, Boolean.valueOf(value));
      }
    }
    if (stateValue == null) {
      return null;
    }
    return new StateListState(stateValue, stateAttributes, alphaValue);
  }

  /**
   * Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb to a Color
   * http://developer.android.com/guide/topics/resources/more-resources.html#Color
   */
  @SuppressWarnings("UseJBColor")
  @Nullable
  public static Color parseColor(@Nullable String s) {
    s = StringUtil.trim(s);
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
  @NotNull
  public static String colorToString(@NotNull Color color) {
    long longColor = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    if (color.getAlpha() != 0xFF) {
      longColor |= (long)color.getAlpha() << 24;
      return String.format("#%08x", longColor);
    }
    return String.format("#%06x", longColor);
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

    String result = drawable.getValue();

    StateList stateList = resolveStateList(resources, drawable, project);
    if (stateList != null) {
      List<StateListState> states = stateList.getStates();
      if (!states.isEmpty()) {
        StateListState state = states.get(states.size() - 1);
        result = state.getValue();
      }
    }

    if (result == null) {
      return null;
    }

    final File file = new File(result);
    return file.isFile() ? file : null;
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
      if (DataBindingUtil.isBindingExpression(value)) {
        value = DataBindingUtil.getBindingExprDefault(value);
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
        // TODO: b/23032391
        AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
        if (androidModel != null) {
          String resourcePrefix = LintUtils.computeResourcePrefix(androidModel.getAndroidProject());
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

  public static int clamp(int i, int min, int max) {
    return Math.max(min, Math.min(i, max));
  }

  /**
   * Returns the list of all resource names that can be used as a value for one of the {@link ResourceType} in completionTypes
   */
  @NotNull
  public static List<String> getCompletionFromTypes(@NotNull AndroidFacet facet, @NotNull ResourceType[] completionTypes) {
    ImmutableList.Builder<String> resourceNamesList = ImmutableList.builder();
    EnumSet<ResourceType> types = Sets.newEnumSet(Arrays.asList(completionTypes), ResourceType.class);

    boolean completionTypesContainsColor = types.contains(ResourceType.COLOR);
    if (types.contains(ResourceType.DRAWABLE)) {
      // The Drawable type accepts colors as value but not color state lists.
      types.add(ResourceType.COLOR);
    }

    for (ResourceType type : types) {
      // If type == ResourceType.COLOR, we want to include file resources (i.e. color state lists) only in the case where
      // color was present in completionTypes, and not if we added it because of the presence of ResourceType.DRAWABLES.
      // For any other ResourceType, we always include file resources.
      boolean includeFileResources = (type != ResourceType.COLOR) || completionTypesContainsColor;
      ResourceGroup group = new ResourceGroup(ANDROID_NS_NAME, type, facet, ANDROID_NS_NAME, includeFileResources);
      for (ResourceItem item : group.getItems()) {
        resourceNamesList.add(item.getResourceUrl());
      }

      group = new ResourceGroup(ChooseResourceDialog.APP_NAMESPACE_LABEL, type, facet, null, includeFileResources);
      for (ResourceItem item : group.getItems()) {
        resourceNamesList.add(item.getResourceUrl());
      }
    }

    return resourceNamesList.build();
  }

  /**
   * Returns the text content of a given tag
   */
  public static String getTextContent(@NotNull XmlTag tag) {
    // We can't just use tag.getValue().getTrimmedText() here because we need to remove
    // intermediate elements such as <xliff> text:
    // TODO: Make sure I correct handle HTML content for XML items in <string> nodes!
    // For example, for the following string we want to compute "Share with %s":
    // <string name="share">Share with <xliff:g id="application_name" example="Bluetooth">%s</xliff:g></string>
    XmlTag[] subTags = tag.getSubTags();
    XmlText[] textElements = tag.getValue().getTextElements();
    if (subTags.length == 0) {
      if (textElements.length == 1) {
        return getXmlTextValue(textElements[0]);
      } else if (textElements.length == 0) {
        return "";
      }
    }
    StringBuilder sb = new StringBuilder(40);
    appendText(sb, tag);
    return sb.toString();
  }

  private static String getXmlTextValue(XmlText element) {
    PsiElement current = element.getFirstChild();
    if (current != null) {
      if (current.getNextSibling() != null) {
        StringBuilder sb = new StringBuilder();
        for (; current != null; current = current.getNextSibling()) {
          IElementType type = current.getNode().getElementType();
          if (type == XmlElementType.XML_CDATA) {
            PsiElement[] children = current.getChildren();
            if (children.length == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
              assert children[1].getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS;
              sb.append(children[1].getText());
            }
            continue;
          }
          sb.append(current.getText());
        }
        return sb.toString();
      } else if (current.getNode().getElementType() == XmlElementType.XML_CDATA) {
        PsiElement[] children = current.getChildren();
        if (children.length == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
          assert children[1].getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS;
          return children[1].getText();
        }
      }
    }

    return element.getText();
  }

  private static void appendText(@NotNull StringBuilder sb, @NotNull XmlTag tag) {
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof XmlText) {
        XmlText text = (XmlText)child;
        sb.append(getXmlTextValue(text));
      } else if (child instanceof XmlTag) {
        XmlTag childTag = (XmlTag)child;
        // xliff support
        if (XLIFF_G_TAG.equals(childTag.getLocalName()) && childTag.getNamespace().startsWith(XLIFF_NAMESPACE_PREFIX)) {
          String example = childTag.getAttributeValue(ATTR_EXAMPLE);
          if (example != null) {
            // <xliff:g id="number" example="7">%d</xliff:g> minutes => "(7) minutes"
            sb.append('(').append(example).append(')');
            continue;
          } else {
            String id = childTag.getAttributeValue(ATTR_ID);
            if (id != null) {
              // Step <xliff:g id="step_number">%1$d</xliff:g> => Step ${step_number}
              sb.append('$').append('{').append(id).append('}');
              continue;
            }
          }
        }
        appendText(sb, childTag);
      }
    }
  }

  /**
   * Stores the information contained in a resource state list.
   */
  public static class StateList {
    private final String myFileName;
    private final String myDirName;
    private final List<StateListState> myStates;

    public StateList(@NotNull String fileName, @NotNull String dirName) {
      myFileName = fileName;
      myDirName = dirName;
      myStates = new ArrayList<StateListState>();
    }

    @NotNull
    public String getFileName() {
      return myFileName;
    }

    @NotNull
    public String getDirName() {
      return myDirName;
    }

    @NotNull
    public ResourceFolderType getFolderType() {
      return ResourceFolderType.getFolderType(myDirName);
    }

    /**
     * @return the type of statelist, can be {@link ResourceType#COLOR} or {@link ResourceType#DRAWABLE}
     */
    @NotNull
    public ResourceType getType() {
      final ResourceFolderType resFolderType = getFolderType();
      final ResourceType resType = ResourceType.getEnum(resFolderType.getName());
      assert resType != null;
      return resType;
    }

    @NotNull
    public List<StateListState> getStates() {
      return myStates;
    }

    public void addState(@NotNull StateListState state) {
      myStates.add(state);
    }

    /**
     * @return a list of all the states in this state list that have explicitly or implicitly state_enabled = false
     */
    @NotNull
    public ImmutableList<StateListState> getDisabledStates() {
      ImmutableList.Builder<StateListState> disabledStatesBuilder = ImmutableList.builder();
      ImmutableSet<ImmutableMap<String, Boolean>> remainingObjectStates =
        ImmutableSet.of(ImmutableMap.of(StateListState.STATE_ENABLED, true), ImmutableMap.of(StateListState.STATE_ENABLED, false));
      // An object state is a particular assignment of boolean values to all possible state list flags.
      // For example, in a world where there exists only three flags (a, b and c), there are 2^3 = 8 possible object state.
      // {a : true, b : false, c : true} is one such state.
      // {a : true} is not an object state, since it does not have values for b or c.
      // But we can use {a : true} as a representation for the set of all object states that have true assigned to a.
      // Since we do not know a priori how many different flags there are, that is how we are going to represent a set of object states.
      // We are using a set of maps, where each map represents a set of object states, and the overall set is their union.
      // For example, the set S = { {a : true} , {b : true, c : false} } is the union of two sets of object states.
      // The first one, described by {a : true} contains 4 object states, and the second one, described by {b : true, c : false} contains 2.
      // Overall this set S represents: {a : true, b : true, c : true}, {a : true, b : true, c : false}, {a : true, b : false, c : true}
      // {a : true, b : false, c : false} and {a : false, b : true, c : false}.
      // It is only 5 object states since {a : true, b : true, c : false} is described by both maps.

      // remainingObjects is going to represent all the object states that have not been matched in the state list until now.
      // So before we start we want to initialise it to represents all possible object states. One easy way to do so is to pick a flag
      // and make two representations {flag : true} and {flag : false} and take their union. We pick "state_enabled" as that flag but any
      // flag could have been used.

      // We now go through the state list state by state.
      for (StateListState state : myStates) {
        // For each state list state, we ask the question : does there exist an object state that could reach this state list state,
        // and match it, and have "state_enabled = true"? If that object state exists, it has to be represented in remainingObjectStates.
        if (!state.matchesWithEnabledObjectState(remainingObjectStates)) {
          // if there is no such object state, then all the object states that would match this state list state would have
          // "state_enabled = false", so this state list state is considered disabled.
          disabledStatesBuilder.add(state);
        }

        // Before looking at the next state list state, we recompute remainingObjectStates so that it does not represent any more
        // the object states that match this state list state.
        remainingObjectStates = removeState(state, remainingObjectStates);
      }
      return disabledStatesBuilder.build();
    }

    /**
     * Returns a representation of all the object states that were in allowed states but do not match the state list state
     */
    @NotNull
    private static ImmutableSet<ImmutableMap<String, Boolean>> removeState(@NotNull StateListState state,
                                                                           @NotNull ImmutableSet<ImmutableMap<String, Boolean>> allowedStates) {
      ImmutableSet.Builder<ImmutableMap<String, Boolean>> remainingStates = ImmutableSet.builder();
      Map<String, Boolean> stateAttributes = state.getAttributes();
      for (String attribute : stateAttributes.keySet()) {
        for (ImmutableMap<String, Boolean> allowedState : allowedStates) {
          if (!allowedState.containsKey(attribute)) {
            // This allowed state does not have a constraint for attribute. So it represents object states that can take either value
            // for it. We restrict this representation by adding to it explicitly the opposite constraint to the one in the state list state
            // so that we remove from this representation all the object states that match the state list state while keeping all the ones
            // that do not.
            ImmutableMap.Builder<String, Boolean> newAllowedState = ImmutableMap.builder();
            newAllowedState.putAll(allowedState).put(attribute, !stateAttributes.get(attribute));
            remainingStates.add(newAllowedState.build());
          }
          else if (allowedState.get(attribute) != stateAttributes.get(attribute)) {
            // None of the object states represented by allowedState match the state list state. So we keep them all by keeping
            // the same representation.
            remainingStates.add(allowedState);
          }
        }
      }
      return remainingStates.build();
    }
  }

  /**
   * Stores information about a particular state of a resource state list.
   */
  public static class StateListState {
    public static final String STATE_ENABLED = "state_enabled";
    private String myValue;
    private String myAlpha;
    private final Map<String, Boolean> myAttributes;

    public StateListState(@NotNull String value, @NotNull Map<String, Boolean> attributes, @Nullable String alpha) {
      myValue = value;
      myAttributes = attributes;
      myAlpha = alpha;
    }

    public void setValue(@NotNull String value) {
      myValue = value;
    }

    public void setAlpha(@Nullable String alpha) {
      myAlpha = alpha;
    }

    @NotNull
    public String getValue() {
      return myValue;
    }

    @Nullable
    public String getAlpha() {
      return myAlpha;
    }

    @NotNull
    public Map<String, Boolean> getAttributes() {
      return myAttributes;
    }

    /**
     * @return a list of all the attribute names. Names are capitalized is capitalize is true
     */
    @NotNull
    public ImmutableList<String> getAttributesNames(boolean capitalize) {
      Map<String, Boolean> attributes = getAttributes();

      if (attributes.isEmpty()) {
        return ImmutableList.of(capitalize ? "Default" : "default");
      }

      ImmutableList.Builder<String> attributeDescriptions = ImmutableList.builder();
      for (Map.Entry<String, Boolean> attribute : attributes.entrySet()) {
        String description = attribute.getKey().substring(STATE_NAME_PREFIX.length());
        if (!attribute.getValue()) {
          description = "not " + description;
        }
        attributeDescriptions.add(capitalize ? StringUtil.capitalize(description) : description);
      }

      return attributeDescriptions.build();
    }

    /**
     * Checks if there exists an object state that matches this state list state, has state_enabled = true,
     * and is represented in allowedObjectStates.
     * @param allowedObjectStates
     */
    private boolean matchesWithEnabledObjectState(@NotNull ImmutableSet<ImmutableMap<String, Boolean>> allowedObjectStates) {
      if (myAttributes.containsKey(STATE_ENABLED) && !myAttributes.get(STATE_ENABLED)) {
        // This state list state has state_enabled = false, so no object state with state_enabled = true could match it
        return false;
      }
      for (Map<String, Boolean> allowedAttributes : allowedObjectStates) {
        if (allowedAttributes.containsKey(STATE_ENABLED) && !allowedAttributes.get(STATE_ENABLED)) {
          // This allowed object state representation has explicitly state_enabled = false, so it does not represent any object state
          // with state_enabled = true
          continue;
        }
        boolean match = true;
        for (String attribute : myAttributes.keySet()) {
          if (allowedAttributes.containsKey(attribute) && myAttributes.get(attribute) != allowedAttributes.get(attribute)) {
            // This state list state does not match any of the object states represented by allowedAttributes, since they explicitly
            // disagree on one particular flag.
            match = false;
            break;
          }
        }
        if (match) {
          // There is one object state represented in allowedAttributes, that has state_enabled = true, and that matches this
          // state list state.
          return true;
        }
      }
      return false;
    }

    @NotNull
    public String getDescription() {
      return Joiner.on(", ").join(getAttributesNames(true));
    }
  }
}
