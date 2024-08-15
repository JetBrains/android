/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;

import com.android.SdkConstants;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Compatibility class for {@link DefaultActivityLocator} and {@link ActivityLocatorUtils}. */
public class DefaultActivityLocatorCompat {
  private static final Logger LOG = Logger.getInstance(DefaultActivityLocatorCompat.class);

  @Nullable
  public static String computeDefaultActivity(@NotNull List<ActivityWrapper> activities) {
    List<ActivityWrapper> launchableActivities = getLaunchableActivities(activities);
    if (launchableActivities.isEmpty()) {
      return null;
    } else if (launchableActivities.size() == 1) {
      return launchableActivities.get(0).getQualifiedName();
    }

    // Prefer the launcher which has the CATEGORY_DEFAULT intent filter.
    // There is no such rule, but since Context.startActivity() prefers such activities, we do the
    // same.
    // https://code.google.com/p/android/issues/detail?id=67068
    ActivityWrapper defaultLauncher = findDefaultLauncher(launchableActivities);
    if (defaultLauncher != null) {
      return defaultLauncher.getQualifiedName();
    }

    // Just return the first one we find
    return launchableActivities.isEmpty() ? null : launchableActivities.get(0).getQualifiedName();
  }

  @Nullable
  private static DefaultActivityLocatorCompat.ActivityWrapper findDefaultLauncher(
      @NotNull List<ActivityWrapper> launcherActivities) {
    for (ActivityWrapper activity : launcherActivities) {
      if (activity.hasCategory(AndroidUtils.DEFAULT_CATEGORY_NAME)) {
        return activity;
      }
    }

    return null;
  }

  @NotNull
  private static List<ActivityWrapper> getLaunchableActivities(
      @NotNull List<ActivityWrapper> allActivities) {
    List<ActivityWrapper> launchableActivities =
        allActivities.stream()
            .filter(activity -> containsLauncherIntent(activity) && activity.isEnabled())
            .collect(Collectors.toList());

    if (launchableActivities.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("No launchable activities found, total # of activities: " + allActivities.size());
      allActivities.forEach(
          wrapper ->
              LOG.debug(
                  String.format(
                      "activity: %1$s, isEnabled: %2$s, containsLauncherIntent: %3$s",
                      wrapper.getQualifiedName(),
                      wrapper.isEnabled(),
                      containsLauncherIntent(wrapper))));
    }

    return launchableActivities;
  }

  /**
   * {@link ActivityWrapper} is a simple wrapper class around an {@link Activity} or an {@link
   * ActivityAlias}.
   */
  public abstract static class ActivityWrapper {

    public abstract boolean hasCategory(@NotNull String name);

    public abstract boolean hasAction(@NotNull String name);

    public abstract boolean isEnabled();

    /**
     * The value of android:exported attribute for the activity, null if not specified. Note that
     * when the attribute is not explicitly set, it is considered exported if it has an intent
     * filter.
     */
    @Nullable
    public abstract Boolean getExported();

    @Nullable
    public abstract String getQualifiedName();

    public static List<ActivityWrapper> get(
        @NotNull List<Element> activities, @NotNull List<Element> aliases) {
      List<ActivityWrapper> list =
          Lists.newArrayListWithCapacity(activities.size() + aliases.size());
      for (Element element : activities) {
        list.add(new ElementActivityWrapper(element));
      }
      for (Element element : aliases) {
        list.add(new ElementActivityWrapper(element));
      }
      return list;
    }
  }

  private static class ElementActivityWrapper extends ActivityWrapper {

    private final Element myActivity;

    public ElementActivityWrapper(Element activity) {
      myActivity = activity;
    }

    @Override
    public boolean hasCategory(@NotNull String name) {
      Node node = myActivity.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
          Element filter = (Element) node;
          if (containsCategory(filter, name)) {
            return true;
          }
        }
        node = node.getNextSibling();
      }

      return false;
    }

    @Override
    public boolean hasAction(@NotNull String name) {
      Node node = myActivity.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
          Element filter = (Element) node;
          if (containsAction(filter, name)) {
            return true;
          }
        }
        node = node.getNextSibling();
      }

      return false;
    }

    @Override
    public boolean isEnabled() {
      String enabledAttr =
          myActivity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ENABLED);
      return StringUtil.isEmpty(enabledAttr) // true if not specified
          || Boolean.parseBoolean(enabledAttr)
          || enabledAttr.startsWith(PREFIX_RESOURCE_REF);
    }

    @Nullable
    @Override
    public Boolean getExported() {
      String exportedAttr =
          myActivity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED);
      return StringUtil.isEmpty(exportedAttr) ? null : Boolean.parseBoolean(exportedAttr);
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return DefaultActivityLocatorCompat.getQualifiedName(myActivity);
    }
  }

  public static boolean containsAction(@NotNull Element filter, @NotNull String name) {
    Node action = filter.getFirstChild();
    while (action != null) {
      if (action.getNodeType() == Node.ELEMENT_NODE && NODE_ACTION.equals(action.getNodeName())) {
        if (name.equals(((Element) action).getAttributeNS(ANDROID_URI, ATTR_NAME))) {
          return true;
        }
      }
      action = action.getNextSibling();
    }
    return false;
  }

  public static boolean containsCategory(@NotNull Element filter, @NotNull String name) {
    Node action = filter.getFirstChild();
    while (action != null) {
      if (action.getNodeType() == Node.ELEMENT_NODE && NODE_CATEGORY.equals(action.getNodeName())) {
        if (name.equals(((Element) action).getAttributeNS(ANDROID_URI, ATTR_NAME))) {
          return true;
        }
      }
      action = action.getNextSibling();
    }
    return false;
  }

  public static boolean containsLauncherIntent(
      @NotNull DefaultActivityLocatorCompat.ActivityWrapper activity) {
    return activity.hasAction(AndroidUtils.LAUNCH_ACTION_NAME)
        && (activity.hasCategory(AndroidUtils.LAUNCH_CATEGORY_NAME)
            || activity.hasCategory(AndroidUtils.LEANBACK_LAUNCH_CATEGORY_NAME));
  }

  @Nullable
  public static String getQualifiedName(@NotNull Element component) {
    Attr nameNode = component.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
    if (nameNode == null) {
      return null;
    }
    String name = nameNode.getValue();

    int dotIndex = name.indexOf('.');
    if (dotIndex > 0) { // fully qualified
      return name;
    }

    // attempt to retrieve the package name from the manifest in which this alias was defined
    Element root = component.getOwnerDocument().getDocumentElement();
    Attr pkgNode = root.getAttributeNode(ATTR_PACKAGE);
    if (pkgNode != null) {
      // if we have a package name, prepend that to the activity alias
      String pkg = pkgNode.getValue();
      return pkg + (dotIndex == -1 ? "." : "") + name;
    }

    return name;
  }

  @Nullable
  public static String getQualifiedName(@NotNull ActivityAlias alias) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    String name = alias.getName().getStringValue();
    if (name == null) {
      return null;
    }

    int dotIndex = name.indexOf('.');
    if (dotIndex > 0) { // fully qualified
      return name;
    }

    // attempt to retrieve the package name from the manifest in which this alias was defined
    String pkg = null;
    DomElement parent = alias.getParent();
    if (parent instanceof Application) {
      parent = parent.getParent();
      if (parent instanceof Manifest) {
        Manifest manifest = (Manifest) parent;
        pkg = manifest.getPackage().getStringValue();
      }
    }

    // if we have a package name, prepend that to the activity alias
    return pkg == null ? name : pkg + (dotIndex == -1 ? "." : "") + name;
  }

  @Nullable
  public static String getQualifiedName(@NotNull Activity activity) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiClass psiClass = activity.getActivityClass().getValue();
    if (psiClass == null) {
      Module module = activity.getModule();
      if (module != null && ApkFacet.getInstance(module) != null) {
        // In APK project we doesn't necessarily have the source/class file of the activity.
        return activity.getActivityClass().getStringValue();
      }
      return null;
    }

    return getQualifiedActivityName(psiClass);
  }

  /**
   * Returns a fully qualified activity name as accepted by "am start" command: In particular,
   * rather than return "com.foo.Bar.Inner", this will return "com.foo.Bar$Inner" for inner classes.
   */
  @Nullable
  public static String getQualifiedActivityName(@NotNull PsiClass c) {
    return PackageClassConverter.getQualifiedName(c);
  }
}
