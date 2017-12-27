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
package com.android.tools.idea.uibuilder.actions;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.scout.ScoutDirectConvert;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static java.util.Locale.ROOT;

/**
 * Action which converts a given layout hierarchy to a ConstraintLayout.
 * <p>
 * <ul>TODO:
 * <li>If it's a RelativeLayout *convert* layout constraints to the equivalents?
 * <li>When removing also remove other layout params (those that don't apply to constraint layout's styleable)
 * </ul>
 * </p>
 */
public class ConvertToConstraintLayoutAction extends AnAction {
  public static final String TITLE = "Convert to ConstraintLayout";
  public static final boolean ENABLED = true;

  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH = "layout_conversion_absoluteWidth"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT = "layout_conversion_absoluteHeight"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_WRAP_WIDTH = "layout_conversion_wrapWidth"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT = "layout_conversion_wrapHeight"; //$NON-NLS-1$
  private static final HashSet<String> ourExcludedTags = new HashSet<>(Arrays.asList(TAG_LAYOUT, TAG_DATA, TAG_VARIABLE, TAG_IMPORT));
  private final NlDesignSurface mySurface;

  public ConvertToConstraintLayoutAction(@NotNull NlDesignSurface surface) {
    super(TITLE, TITLE, null);
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    ScreenView screenView = mySurface.getCurrentSceneView();
    NlComponent target = findTarget(screenView);
    if (target != null) {
      String tagName = target.getTagName();
      // Don't show action if it's already a ConstraintLayout
      if (NlComponentHelperKt.isOrHasSuperclass(target, CONSTRAINT_LAYOUT)) {
        presentation.setVisible(false);
        return;
      }
      if (ourExcludedTags.contains(tagName)) { // prevent the user from converting a "<layout>"
        presentation.setVisible(false);
        return;
      }
      presentation.setVisible(true);
      tagName = tagName.substring(tagName.lastIndexOf('.') + 1);
      presentation.setText("Convert " + tagName + " to ConstraintLayout");
      presentation.setEnabled(true);
    }
    else {
      presentation.setText(TITLE);
      presentation.setEnabled(false);
      presentation.setVisible(true);
    }
  }

  @Nullable
  private static NlComponent findTarget(@Nullable ScreenView screenView) {
    if (screenView != null) {
      List<NlComponent> selection = screenView.getSelectionModel().getSelection();
      if (selection.size() == 1) {
        NlComponent selected = selection.get(0);
        while (selected != null && !selected.isRoot() && selected.getChildren().isEmpty()) {
          selected = selected.getParent();
        }

        return selected;
      }
    }

    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ScreenView screenView = mySurface.getCurrentSceneView();
    if (screenView == null) {
      return;
    }
    NlComponent target = findTarget(screenView);
    if (target == null) {
      // Shouldn't happen, enforced by update(AnActionEvent)
      return;
    }

    // Step #1: UI

    Project project = mySurface.getProject();
    ConvertToConstraintLayoutForm dialog = new ConvertToConstraintLayoutForm(project);
    if (!dialog.showAndGet()) {
      return;
    }

    boolean flatten = dialog.getFlattenHierarchy();
    boolean includeIds = dialog.getFlattenReferenced();
    boolean includeCustomViews = dialog.getIncludeCustomViews();


    // Step #2: Ensure ConstraintLayout is available in the project
    GradleDependencyManager manager = GradleDependencyManager.getInstance(project);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":+");
    if (!manager.ensureLibraryIsIncluded(screenView.getModel().getModule(), Collections.singletonList(coordinate), null)) {
      return;
    }

    // Step #3: Migrate

    @SuppressWarnings("ConstantConditions")
    ConstraintLayoutConverter converter = new ConstraintLayoutConverter(screenView, target, flatten, includeIds, includeCustomViews);
    converter.execute();
  }

  private static void inferConstraints(@NotNull NlComponent target) {
    try {
      Scout.inferConstraintsFromConvert(target);
      ArrayList<NlComponent> list = new ArrayList<>(target.getChildren());
      list.add(0, target);
      for (NlComponent component : list) {
        AttributesTransaction transaction = component.startAttributeTransaction();
        transaction.commit();
      }
      removeAbsolutePositionAndSizes(target);
    }
    catch (Throwable t) {
      Logger.getInstance(ConvertToConstraintLayoutAction.class).warn(t);
    }
  }

  /**
   * Removes absolute x/y/width/height conversion attributes
   */
  private static void removeAbsolutePositionAndSizes(NlComponent component) {
    // Work bottom up to ensure the children aren't invalidated when processing the parent
    for (NlComponent child : component.getChildren()) {
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_WIDTH, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT, null);


      removeAbsolutePositionAndSizes(child);
    }
  }

  private static class ConstraintLayoutConverter extends WriteCommandAction {
    private static final boolean DIRECT_INFERENCE = true;
    private final ScreenView myScreenView;
    private final boolean myFlatten;
    private final boolean myIncludeIds;
    private final boolean myIncludeCustomViews;
    private ViewEditorImpl myEditor;
    private List<NlComponent> myToBeFlattened;
    private NlComponent myRoot;
    private NlComponent myLayout;

    public ConstraintLayoutConverter(@NotNull ScreenView screenView,
                                     @NotNull NlComponent target,
                                     boolean flatten,
                                     boolean includeIds,
                                     boolean includeCustomViews) {
      super(screenView.getSurface().getProject(), TITLE, screenView.getModel().getFile());
      myScreenView = screenView;
      myFlatten = flatten;
      myIncludeIds = includeIds;
      myIncludeCustomViews = includeCustomViews;
      myLayout = target;
      myRoot = myScreenView.getModel().getComponents().get(0);
      myEditor = new ViewEditorImpl(myScreenView);
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      if (myLayout == null) {
        return;
      }
      NlComponentHelperKt.ensureId(myLayout);

      boolean directConvert = true;
      if (myFlatten) {
        for (NlComponent child : myLayout.getChildren()) {
          if (isLayout(child)) {
            directConvert = false;
          }
        }
      }
      if (directConvert && ScoutDirectConvert.directProcess(myLayout)) {
        return;
      }

      myToBeFlattened = Lists.newArrayList();
      processComponent(myLayout);

      flatten();
      PsiElement tag = myLayout.getTag().setName(CLASS_CONSTRAINT_LAYOUT);
      //((NlComponentMixin)myLayout.getMixin()).getData$production_sources_for_module_designer().

      NlModel model = myLayout.getModel();
      XmlTag layoutTag = myLayout.getTag();
      XmlTag rootTag = myRoot.getTag();
      myScreenView.getSurface().getSceneManager().layout(false);

      // syncWithPsi (called by layout()) can cause the components to be recreated, so update our root and layout.
      myRoot = model.findViewByTag(rootTag);
      myLayout = model.findViewByTag(layoutTag);

      tag = CodeStyleManager.getInstance(getProject()).reformat(tag);
      myLayout.getModel().syncWithPsi((XmlTag)tag, Collections.emptyList());

      if (DIRECT_INFERENCE) { // Let's run Scout after the flattening. Ideally we should run this after the model got a chance to update.
        final String id = myLayout.getId();
        myScreenView.getModel().addListener(new ModelListener() {
          @Override
          public void modelRendered(@NotNull NlModel model) {
            assert id != null;
            NlComponent layout = myScreenView.getModel().find(id);

            Runnable action = new NlWriteCommandAction(Collections.singletonList(layout), "Infer Constraints", () -> {
              if (layout != null) {
                Map<NlComponent, Dimension> sizes = myEditor.measureChildren(layout, null);
                assert sizes != null;

                for (NlComponent component : sizes.keySet()) {
                  Dimension d = sizes.get(component);
                  component.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_WIDTH, Integer.toString(d.width));
                  component.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT, Integer.toString(d.height));
                }

                inferConstraints(layout);
                myScreenView.getModel().removeListener(this);
              }
            });

            ApplicationManager.getApplication().invokeLater(action);
          }

          @Override
          public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {

          }
        });


        //});
        //t.setRepeats(false);
        // t.start();
      }
    }

    /**
     * Add bounds to components and record components to be flattened into {@link #myToBeFlattened}
     */
    private void processComponent(NlComponent component) {
      // Work bottom up to ensure the children aren't invalidated when processing the parent
      for (NlComponent child : component.getChildren()) {
        int dpx = myEditor.pxToDp(NlComponentHelperKt.getX(child) - NlComponentHelperKt.getX(myRoot));
        int dpy = myEditor.pxToDp(NlComponentHelperKt.getY(child) - NlComponentHelperKt.getY(myRoot));
        int dpw = myEditor.pxToDp(NlComponentHelperKt.getW(child));
        int dph = myEditor.pxToDp(NlComponentHelperKt.getH(child));
        AttributesTransaction transaction = child.startAttributeTransaction();
        // Record the bounds for use by Scout
        transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH, String.format(ROOT, VALUE_N_DP, dpw));
        transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT, String.format(ROOT, VALUE_N_DP, dph));
        // position in absolute coordinates
        transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, String.format(ROOT, VALUE_N_DP, dpx));
        transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, String.format(ROOT, VALUE_N_DP, dpy));
        //* Set to wrap Scout can use
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        transaction.commit();
        // First gather attributes to delete; can delete during iteration (concurrent modification exceptions will ensure)
        List<String> toDelete = null;
        for (AttributeSnapshot attribute : child.getAttributes()) {
          String name = attribute.name;
          if (!name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) ||
              !ANDROID_URI.equals(attribute.namespace) ||
              name.equals(ATTR_LAYOUT_WIDTH) ||
              name.equals(ATTR_LAYOUT_HEIGHT)) {
            continue;
          }
          if (toDelete == null) {
            toDelete = Lists.newArrayList();
          }
          toDelete.add(name);
        }
        if (toDelete != null) {
          for (String name : toDelete) {
            child.setAttribute(ANDROID_URI, name, null);
          }
        }

        if (isLayout(child)) {
          if (myFlatten) {
            if (shouldFlatten(child)) {
              myToBeFlattened.add(child);
            }
            else {
              continue;
            }
          }
          else {
            continue;
          }
        }
        processComponent(child);
      }
    }

    /**
     * Flatten layouts listed in {@link #myToBeFlattened} in order.
     * These should already be in bottom up order
     */
    private void flatten() {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
      Document document = documentManager.getDocument(myScreenView.getModel().getFile());
      if (document == null) {
        return;
      }
      documentManager.doPostponedOperationsAndUnblockDocument(document);

      List<TextRange> ranges = Lists.newArrayList();
      for (NlComponent component : myToBeFlattened) {
        XmlTag tag = component.getTag();
        PsiElement openStart = null;
        PsiElement openEnd = null;
        PsiElement closeStart = null;
        PsiElement closeEnd = null;
        PsiElement curr = tag.getFirstChild();
        while (curr != null) {
          IElementType elementType = curr.getNode().getElementType();
          if (elementType == XmlTokenType.XML_START_TAG_START) {
            openStart = curr;
          }
          else if (elementType == XmlTokenType.XML_TAG_END) {
            if (closeStart == null) {
              openEnd = curr;
            }
            else {
              closeEnd = curr;
              break;
            }
          }
          else if (elementType == XmlTokenType.XML_END_TAG_START) {
            closeStart = curr;
          }
          else if (elementType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
            openEnd = curr;
            break;
          }
          curr = curr.getNextSibling();
        }

        if (openStart != null && openEnd != null && closeStart != null && closeEnd != null) {
          ranges.add(new TextRange(openStart.getTextOffset(), openEnd.getTextOffset() + openEnd.getTextLength()));
          ranges.add(new TextRange(closeStart.getTextOffset(), closeEnd.getTextOffset() + closeEnd.getTextLength()));
        }
      }

      ranges.sort((o1, o2) -> {
        // There should be no overlaps
        return o2.getStartOffset() - o1.getStartOffset();
      });

      for (TextRange range : ranges) {
        document.deleteString(range.getStartOffset(), range.getEndOffset());
      }

      documentManager.commitDocument(document);
    }

    private boolean isLayout(@NotNull NlComponent component) {
      if (!myIncludeCustomViews && isCustomView(component)) {
        return false;
      }

      List<NlComponent> children = component.getChildren();
      if (children.size() > 1) {
        return true;
      }
      else if (children.size() == 1) {
        NlComponent child = children.get(0);
        if (!REQUEST_FOCUS.equals(child.getTagName())) {
          // Some child *other* than <requestFocus> - must be a layout
          return true;
        }
        // If the child is a <requestFocus> we don't know
      }

      ViewInfo info = NlComponentHelperKt.getViewInfo(component);
      if (info != null) {
        Object viewObject = info.getViewObject();
        if (viewObject != null) {
          Class<?> cls = viewObject.getClass();
          while (cls != null) {
            String fqcn = cls.getName();
            if (FQCN_ADAPTER_VIEW.equals(fqcn)) {
              // ListView etc - a ViewGroup but NOT considered a layout
              return false;
            }
            if (fqcn.startsWith(ANDROID_WEBKIT_PKG) && fqcn.endsWith(WEB_VIEW)) {
              // WebView: an AbsoluteLayout child class but NOT a "layout"
              return false;
            }
            if (CLASS_VIEWGROUP.equals(fqcn)) {
              return true;
            }
            cls = cls.getSuperclass();
          }
        }
      }

      return false;
    }

    private static boolean isCustomView(NlComponent component) {
      String tag = component.getTagName();
      return tag.indexOf('.') != -1;
    }

    private boolean shouldFlatten(@NotNull NlComponent component) {
      if (!myIncludeCustomViews && isCustomView((component))) {
        return false;
      }

      // See if the component seems to have a visual purpose - e.g. sets background or other styles
      if (component.getAttribute(ANDROID_URI, ATTR_BACKGROUND) != null
          || component.getAttribute(ANDROID_URI, ATTR_FOREGROUND) != null  ) {// such as ?android:selectableItemBackgroun
        return false;
      }

      String id = component.getAttribute(ANDROID_URI, ATTR_ID);
      if (id == null) {
        return true;
      }

      // If it defines an ID, see if the ID is used anywhere
      if (!myIncludeIds) {
        XmlAttribute attribute = component.getTag().getAttribute(ATTR_ID, ANDROID_URI);
        if (attribute != null) {
          XmlAttributeValue valueElement = attribute.getValueElement();
          if (valueElement != null && valueElement.isValid()) {
            // Exact replace only, no comment/text occurrence changes since it is non-interactive
            RenameProcessor processor = new RenameProcessor(myScreenView.getSurface().getProject(), valueElement, "NONEXISTENT_ID12345",
                                                            false /*comments*/, false /*text*/);
            processor.setPreviewUsages(false);
            XmlFile layoutFile = myScreenView.getModel().getFile();

            // Do a quick usage search to see if we need to ask about renaming
            UsageInfo[] usages = processor.findUsages();
            for (UsageInfo info : usages) {
              PsiFile file = info.getFile();
              if (!layoutFile.equals(file)) {
                // Referenced from outside of this layout file!
                return false;
              }
            }
          }
        }
      }

      return true;
    }
  }
}
