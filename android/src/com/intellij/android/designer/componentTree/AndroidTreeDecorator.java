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
package com.intellij.android.designer.componentTree;

import com.android.SdkConstants;
import com.android.tools.idea.rendering.IncludeReference;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.*;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.IncludeReference.ATTR_RENDER_IN;
import static com.intellij.android.designer.model.RadModelBuilder.ROOT_NODE_TAG;

/**
 * Tree decorator for the component tree in Android.
 * <p>
 * It displays the id (if any) in bold, the tag name (unless implied by the id),
 * and the key attributes of the view in gray. It also inlines {@code <view>} tags.
 * Finally, it uses the palette icons, and overlays warning and error icons
 * if the corresponding tag has lint warnings.
 */
public final class AndroidTreeDecorator implements TreeComponentDecorator {
  @Nullable private final Project myProject;

  public AndroidTreeDecorator(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public void decorate(RadComponent component, SimpleColoredComponent renderer, AttributeWrapper wrapper, boolean full) {
    MetaModel metaModel = component.getMetaModel();

    // Special case: for the <view> tag, show the referenced
    // class instead
    String tag = metaModel.getTag();
    if (VIEW_TAG.equals(tag) && component instanceof RadViewComponent) {
      // We have to use the XmlTag to look up the class attribute since the
      // component.getPropertyValue(ATTR_CLASS) call does not return it
      RadViewComponent rvc = (RadViewComponent)component;
      XmlAttribute attribute = rvc.getTag().getAttribute(ATTR_CLASS);
      if (attribute != null) {
        String cls = attribute.getValue();
        if (!StringUtil.isEmpty(cls)) {
          if (myProject != null) {
            MetaManager metaManager = ViewsMetaManager.getInstance(myProject);
            MetaModel classModel = metaManager.getModelByTarget(cls);
            if (classModel != null) {
              metaModel = classModel;
            }
          }
        }
      }
    }
    decorate(component, metaModel, renderer, wrapper, full);
  }

  private void decorate(RadComponent component,
                               MetaModel metaModel,
                               SimpleColoredComponent renderer,
                               AttributeWrapper wrapper,
                               boolean full) {
    String id = component.getPropertyValue(ATTR_ID);
    id = LintUtils.stripIdPrefix(id);
    id = StringUtil.nullize(id);

    PaletteItem item = metaModel.getPaletteItem();
    String type = null;
    String tagName = metaModel.getTag();
    if (item != null) {
      type = item.getTitle();

      // Don't display <Fragment> etc for special XML tags like <requestFocus>
      if (tagName.equals(VIEW_INCLUDE) ||
          tagName.equals(VIEW_MERGE) ||
          tagName.equals(VIEW_FRAGMENT) ||
          tagName.equals(REQUEST_FOCUS)) {
        type = null;
      }
    }

    if (id != null) {
      SimpleTextAttributes idStyle = wrapper.getAttribute(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      renderer.append(id, idStyle);
    }

    if (id == null && type == null)  {
      type = tagName;
    }

    // For the root node, show the including layout when rendering in included contexts
    if (ROOT_NODE_TAG.equals(tagName)) {
      IncludeReference includeContext = component.getClientProperty(ATTR_RENDER_IN);
      if (includeContext != null && includeContext != IncludeReference.NONE) {
        type = "Shown in " + includeContext.getFromResourceUrl();
      }
    }

    // Don't display the type if it's obvious from the id (e.g.
    // if the id is button1, don't display (Button) as the type)
    if (type != null && (id == null || !StringUtil.startsWithIgnoreCase(id, type))) {
      SimpleTextAttributes typeStyle = wrapper.getAttribute(SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append(id != null ? String.format(" (%1$s)", type) : type, typeStyle);
    }

    // Display typical arguments
    StringBuilder fullTitle = new StringBuilder();
    String title = metaModel.getTitle();
    if (title != null) {
      int start = title.indexOf('%');
      if (start != -1) {
        int end = title.indexOf('%', start + 1);
        if (end != -1) {
          String variable = title.substring(start + 1, end);

          String value = component.getPropertyValue(variable);
          if (!StringUtil.isEmpty(value)) {
            value = StringUtil.shortenTextWithEllipsis(value, 30, 5);
          }

          if (!StringUtil.isEmpty(value)) {
            String prefix = title.substring(0, start);
            String suffix = title.substring(end + 1);
            if ((value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || value.startsWith(SdkConstants.PREFIX_THEME_REF))
                && prefix.length() > 0 && suffix.length() > 0 &&
                prefix.charAt(prefix.length() - 1) == '"' &&
                suffix.charAt(0) == '"') {
              // If the value is a resource, don't surround it with quotes
              prefix = prefix.substring(0, prefix.length() - 1);
              suffix = suffix.substring(1);
            }
            fullTitle.append(prefix).append(value).append(suffix);
          }
        }
      }
    }

    if (fullTitle.length() > 0) {
      SimpleTextAttributes valueStyle = wrapper.getAttribute(SimpleTextAttributes.GRAY_ATTRIBUTES);
      renderer.append(fullTitle.toString(), valueStyle);
    }

    if (full) {
      Icon icon = metaModel.getIcon();

      // Annotate icons with lint warnings or errors, if applicable
      HighlightDisplayLevel displayLevel = null;
      if (myProject != null) {
        SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(myProject);
        for (ErrorInfo errorInfo : RadComponent.getError(component)) {
          if (displayLevel == null || severityRegistrar.compare(errorInfo.getLevel().getSeverity(), displayLevel.getSeverity()) > 0) {
            displayLevel = errorInfo.getLevel();
          }
        }
        if (displayLevel == HighlightDisplayLevel.ERROR) {
          LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(icon, 0);
          layeredIcon.setIcon(AndroidIcons.ErrorBadge, 1, 10, 10);
          icon = layeredIcon;
        } else if (displayLevel == HighlightDisplayLevel.WARNING || displayLevel == HighlightDisplayLevel.WEAK_WARNING) {
          LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(icon, 0);
          layeredIcon.setIcon(AndroidIcons.WarningBadge, 1, 10, 10);
          icon = layeredIcon;
        }
      }

      renderer.setIcon(icon);

      if (component instanceof IComponentDecorator) {
        ((IComponentDecorator)component).decorateTree(renderer, wrapper);
      }
    }
  }
}
