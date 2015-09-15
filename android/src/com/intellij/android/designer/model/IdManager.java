/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model;

import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * The ID manager is responsible for assigning and reassigning id's for layout widgets
 */
public class IdManager {
  /** Returns an ID manager */
  @NotNull
  public static IdManager get() {
    return new IdManager();
  }

  /** Looks up the id base name from the given id attribute value, e.g. for {@code @+id/foo} this returns {@code foo} */
  @Nullable
  public static String getIdName(@Nullable String idValue) {
    if (idValue != null) {
      if (idValue.startsWith(NEW_ID_PREFIX)) {
        return idValue.substring(NEW_ID_PREFIX.length());
      }
      else if (idValue.startsWith(ID_PREFIX)) {
        return idValue.substring(ID_PREFIX.length());
      }
    }

    return null;
  }

  /** Looks up the existing set of id's reachable from the component's context */
  private static Collection<String> getIds(RadViewComponent component) {
    XmlTag tag = component.getTag();
    Module module = AndroidPsiUtils.getModuleSafely(tag);
    return getIds(module, component.getRoot());
  }

  /**
   * Looks up the existing set of id's reachable from the given module, and if provided,
   * also includes any id's <b>just</b> added to to the component tree hierarchy (which may not
   * yet have been included)
   */
  private static Collection<String> getIds(@Nullable Module module, @Nullable RadComponent root) {
    Set<String> ids = Sets.newHashSet();

    if (root != null) {
      addIdsFromChildren(root, ids);
    }

    if (module != null) {
      AppResourceRepository resources = AppResourceRepository.getAppResources(module, true);
      if (resources != null) {
        ids.addAll(resources.getItemsOfType(ResourceType.ID));
      }
    }

    return ids;
  }

  private static void addIdsFromChildren(@NotNull RadComponent root, final Set<String> ids) {
    root.accept(new RadComponentVisitor() {
      @Override
      public void endVisit(RadComponent component) {
        if (component instanceof RadViewComponent) {
          RadViewComponent viewComponent = (RadViewComponent)component;
          String id = getIdName(viewComponent.getId());
          if (id != null) {
            ids.add(id);
          }
        }
      }
    }, true);
  }

  /**
   * Assign a suitable new and unique id to the given component.
   */
  @NotNull
  public String assignId(RadViewComponent component) {
    XmlTag tag = component.getTag();
    Collection<String> idList = getIds(AndroidPsiUtils.getModuleSafely(tag), component.getRoot());
    return assignId(component, idList);
  }

  /**
   * Assign a suitable new and unique id to the given component. The set of
   * existing id's is provided in the given list.
   */
  @NotNull
  public String assignId(RadViewComponent component, Collection<String> idList) {
    String idValue = StringUtil.decapitalize(component.getMetaModel().getTag());

    XmlTag tag = component.getTag();
    Module module = AndroidPsiUtils.getModuleSafely(tag);
    if (module != null) {
      idValue = ResourceHelper.prependResourcePrefix(module, idValue);
    }

    String nextIdValue = idValue;
    int index = 0;

    // Ensure that we don't create something like "switch" as an id, which won't compile when used
    // in the R class
    NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(JavaLanguage.INSTANCE);

    Project project = tag.getProject();
    while (idList.contains(nextIdValue) || validator != null && validator.isKeyword(nextIdValue, project)) {
      ++index;
      if (index == 1 && (validator == null || !validator.isKeyword(nextIdValue, project))) {
        nextIdValue = idValue;
      } else {
        nextIdValue = idValue + Integer.toString(index);
      }
    }

    String newId = NEW_ID_PREFIX + idValue + (index == 0 ? "" : Integer.toString(index));
    tag.setAttribute(ATTR_ID, ANDROID_URI, newId);
    return newId;
  }

  /**
   * Determines whether the given new component should have an id attribute.
   * This is generally false for layouts, and generally true for other views,
   * not including the {@code <include>} and {@code <merge>} tags. Note that
   * {@code <fragment>} tags <b>should</b> specify an id.
   *
   * @param component the new component to check
   * @return true if the component should have a default id
   */
  public boolean needsDefaultId(RadViewComponent component) {
    if (component instanceof RadViewContainer) {
      return false;
    }
    String tag = component.getTag().getName();
    if (tag.equals(VIEW_INCLUDE) || tag.equals(VIEW_MERGE) || tag.equals(SPACE) || tag.equals(REQUEST_FOCUS) ||
        // Handle <Space> in the compatibility library package
        (tag.endsWith(SPACE) && tag.length() > SPACE.length() && tag.charAt(tag.length() - SPACE.length()) == '.')) {
      return false;
    }

    return true;
  }

  /**
   * Ensure that the given component <b>hierarchy</b> has unique id's and that
   * widgets which need an id have been assigned one.
   * <p>
   * This is most important after copy/paste. If you copy a component hierarchy,
   * and then paste a second copy, all the ids must be changed to be unique, and
   * more importantly, all the <b>references</b> to these components must be updated
   * as well!
   *
   * @param container the root container to recursively update
   */
  public void ensureIds(final RadViewComponent container) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final List<Pair<Pair<String, String>, String>> replaceList = new ArrayList<Pair<Pair<String, String>, String>>();
        final List<String> idList = Lists.newArrayList(getIds(container));

        container.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            RadViewComponent viewComponent = (RadViewComponent)component;
            String id = viewComponent.getId();
            String idName = getIdName(id);
            if (component == container) {
              if (idName != null || needsDefaultId(viewComponent)) {
                id = assignId(viewComponent, idList);
                idList.add(getIdName(id));
              }
            }
            else if (idName != null && idList.contains(idName)) {
              id = assignId(viewComponent, idList);
              idList.add(getIdName(id));
              // Rename all @id/ and @+id/ references from the old name to the new name
              replaceList.add(Pair.create(Pair.create(ID_PREFIX + idName, NEW_ID_PREFIX + idName), id));
            }
          }
        }, true);

        if (!replaceList.isEmpty()) {
          replaceIds(container, replaceList);
        }
      }
    });
  }

  /** For strings A, B and C in {@code Pair<Pair<A,B>,C>} this will replace occurrences of A or B with C */
  private static void replaceIds(RadViewComponent container, final List<Pair<Pair<String, String>, String>> replaceList) {
    container.accept(new RadComponentVisitor() {
      @Override
      public void endVisit(RadComponent component) {
        XmlTag tag = ((RadViewComponent)component).getTag();
        for (XmlAttribute attribute : tag.getAttributes()) {
          String value = attribute.getValue();

          for (Pair<Pair<String, String>, String> replace : replaceList) {
            if (replace.first.first.equals(value) || replace.first.second.equals(value)) {
              attribute.setValue(replace.second);
              break;
            }
          }
        }
      }
    }, true);
  }
}
