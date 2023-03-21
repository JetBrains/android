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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.model.ActivityAttributesSnapshot;
import com.android.tools.idea.rendering.parsers.RenderXmlFile;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_SPLIT_ACTION_BAR_WHEN_NARROW;

/**
 * A callback to provide information related to the Action Bar as required by the
 * {@link LayoutLibrary}
 */
public class ActionBarHandler extends ActionBarCallback {
  private static final String ON_CREATE_OPTIONS_MENU = "onCreateOptionsMenu";                   //$NON-NLS-1$
  private static final Pattern MENU_FIELD_PATTERN = Pattern.compile("R\\.menu\\.([a-z0-9_]+)"); //$NON-NLS-1$

  @Nullable private final Object myCredential;
  @NotNull private final RenderTask myRenderTask;

  public ResourceRepositoryManager getResourceRepositoryManager() {
    return myResourceRepositoryManager;
  }

  @NotNull private final ResourceRepositoryManager myResourceRepositoryManager;
  @Nullable private ImmutableList<ResourceReference> myMenus;

  ActionBarHandler(
    @NotNull RenderTask renderTask,
    @Nullable Object credential) {
    myRenderTask = renderTask;
    myCredential = credential;
    myResourceRepositoryManager = renderTask.getContext().getModule().getResourceRepositoryManager();
  }

  @Override
  public boolean getSplitActionBarWhenNarrow() {
    ActivityAttributesSnapshot attributes = getActivityAttributes();
    if (attributes != null) {
      return VALUE_SPLIT_ACTION_BAR_WHEN_NARROW.equals(attributes.getUiOptions());
    }
    return false;
  }

  @Override
  public boolean isOverflowPopupNeeded() {
    return myRenderTask.getContext().getFolderType() == ResourceFolderType.MENU;
  }

  private void updateMenusInBackground(@NotNull Project project, @NotNull String fqn, @NotNull ResourceNamespace namespace) {
    Callable<Collection<ResourceReference>> calculateMenus = () -> {
      // Glance at the onCreateOptionsMenu of the associated context and use any menus found there.
      // This is just a simple textual search; we need to replace this with a proper model lookup.
      PsiClass clz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
      if (clz != null) {
        for (PsiMethod method : clz.findMethodsByName(ON_CREATE_OPTIONS_MENU, true)) {
          if (method instanceof PsiCompiledElement) {
            continue;
          }
          // TODO: This should instead try to use the GotoRelated implementation's notion
          // of associated activities; see what is done in
          // AndroidMissingOnClickHandlerInspection. However, the AndroidGotoRelatedProvider
          // will first need to properly handle menus.
          String matchText = method.getText();
          Matcher matcher = MENU_FIELD_PATTERN.matcher(matchText);
          Set<ResourceReference> menus = new TreeSet<>(Comparator.comparing(ResourceReference::getName));
          int index = 0;
          while (true) {
            if (matcher.find(index)) {
              String name = matcher.group(1);
              menus.add(new ResourceReference(namespace, ResourceType.MENU, name));
              index = matcher.end();
            }
            else {
              break;
            }
          }
          if (!menus.isEmpty()) {
            return menus;
          }
        }
      }

      return ImmutableList.of();
    };

    ReadAction.nonBlocking(calculateMenus)
      .inSmartMode(project)
      .coalesceBy(project, this, fqn)
      .finishOnUiThread(ModalityState.defaultModalityState(), menus -> {
        if (!menus.isEmpty()) {
          myMenus = ImmutableList.copyOf(menus);
        }
      }).submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  @NotNull
  public List<ResourceReference> getMenuIds() {
    if (myMenus != null) {
      return myMenus;
    }

    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      ResourceNamespace namespace = myResourceRepositoryManager.getNamespace();
      RenderXmlFile xmlFile = myRenderTask.getXmlFile();
      String commaSeparatedMenus = xmlFile == null ? null : xmlFile.getRootTagAttribute(ATTR_MENU, TOOLS_URI);
      if (commaSeparatedMenus != null) {
        List<String> names = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(commaSeparatedMenus);
        myMenus = names.stream()
          .map(name -> new ResourceReference(namespace, ResourceType.MENU, name))
          .collect(ImmutableList.toImmutableList());
      } else {
        String fqn = xmlFile == null ? null : AndroidXmlFiles.getDeclaredContextFqcn(myRenderTask.getContext().getModule().getResourcePackage(), xmlFile);
        if (fqn != null) {
          updateMenusInBackground(xmlFile.getProject(), fqn, namespace);
        }
       }

      if (myMenus == null) {
        myMenus = ImmutableList.of();
      }
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }

    return myMenus;
  }

  @Override
  public HomeButtonStyle getHomeButtonStyle() {
    ActivityAttributesSnapshot attributes = getActivityAttributes();
    if (attributes != null && attributes.getParentActivity() != null) {
      return HomeButtonStyle.SHOW_HOME_AS_UP;
    }
    return HomeButtonStyle.NONE;
  }

  @Override
  public int getNavigationMode() {
    RenderXmlFile xmlFile = myRenderTask.getXmlFile();
    String navMode =
        StringUtil.notNullize(xmlFile == null ? null : xmlFile.getRootTagAttribute(ATTR_NAV_MODE, TOOLS_URI)).trim();
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_TABS)) {
      return NAVIGATION_MODE_TABS;
    }
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_LIST)) {
      return NAVIGATION_MODE_LIST;
    }
    return NAVIGATION_MODE_STANDARD;
  }

  /**
   * If set to null, this searches for the associated menu using tools:context and tools:menu attributes.
   * To set no menu, pass an empty list.
   */
  public void setMenuIds(@Nullable List<ResourceReference> menus) {
    myMenus = menus != null ? ImmutableList.copyOf(menus) : ImmutableList.of();
  }

  @Nullable
  private ActivityAttributesSnapshot getActivityAttributes() {
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      RenderModelManifest manifest = myRenderTask.getContext().getModule().getManifest();
      String activity = StringUtil.notNullize(myRenderTask.getContext().getConfiguration().getActivity());
      return manifest != null ? manifest.getActivityAttributes(activity) : null;
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }
}
