/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import icons.AndroidIcons;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.InsetsUIResource;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class TextEditorWithAutoCompletion extends TextFieldWithAutoCompletion<String> {
  private final TextAttributes myTextAttributes;
  private final Insets myEditorInsets;
  private final CompletionProvider myCompletionProvider;
  private final PropertyChangeListener myPropertyChangeListener;
  private final List<LookupListener> myLookupListeners;

  public static TextEditorWithAutoCompletion create(@NotNull Project project, @NotNull Insets editorInsets) {
    CompletionProvider completionProvider = new CompletionProvider();
    return new TextEditorWithAutoCompletion(project, completionProvider, editorInsets);
  }

  private TextEditorWithAutoCompletion(@NotNull Project project,
                                       @NotNull CompletionProvider completionProvider,
                                       @NotNull Insets editorInsets) {
    super(project, completionProvider, true, null);
    myCompletionProvider = completionProvider;
    myTextAttributes = new TextAttributes(null, null, null, null, Font.PLAIN);
    myEditorInsets = editorInsets;
    myPropertyChangeListener = new LookupListenerHandler();
    myLookupListeners = new ArrayList<>();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    EditorEx editor = (EditorEx)getEditor();
    assert editor != null;
    editor.getColorsScheme().setAttributes(HighlighterColors.TEXT, myTextAttributes);
    editor.setHighlighter(new EmptyEditorHighlighter(myTextAttributes));
    editor.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, true);
    editor.setBorder(new DarculaEditorTextFieldBorder() {
      @Override
      public Insets getBorderInsets(Component component) {
        return new InsetsUIResource(myEditorInsets.top, myEditorInsets.left, myEditorInsets.bottom, myEditorInsets.right);
      }
    });
    LookupManager.getInstance(getProject()).addPropertyChangeListener(myPropertyChangeListener);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    LookupManager.getInstance(getProject()).removePropertyChangeListener(myPropertyChangeListener);

    // Remove the editor from the component tree.
    // The editor component is added in EditorTextField.addNotify but never removed by EditorTextField.
    // This is causing paint problems when this component is reused in a different panel.
    removeAll();
  }

  public void setTextColor(@NotNull Color color) {
    myTextAttributes.setForegroundColor(color);
    EditorEx editor = (EditorEx)getEditor();
    if (editor != null) {
      editor.getColorsScheme().setAttributes(HighlighterColors.TEXT, myTextAttributes);
      editor.setHighlighter(new EmptyEditorHighlighter(myTextAttributes));
    }
  }

  public void setFontStyle(int style) {
    myTextAttributes.setFontType(style);
    EditorEx editor = (EditorEx)getEditor();
    if (editor != null) {
      editor.getColorsScheme().setAttributes(HighlighterColors.TEXT, myTextAttributes);
      editor.setHighlighter(new EmptyEditorHighlighter(myTextAttributes));
    }
  }

  public void addLookupListener(@NotNull LookupListener listener) {
    myLookupListeners.add(listener);
  }

  public static List<String> loadCompletions(@NotNull AndroidFacet facet,
                                             @NotNull EnumSet<ResourceType> types,
                                             @Nullable NlProperty property) {
    List<String> items = new ArrayList<>();

    if (property != null) {
      AttributeDefinition definition = property.getDefinition();
      if (definition != null && definition.getValues().length > 0) {
        items.addAll(Arrays.asList(definition.getValues()));
      }

      if (types.contains(ResourceType.ID)) {
        types = types.clone();
        types.remove(ResourceType.ID);

        Set<String> ids = ApplicationManager.getApplication().runReadAction(
          (Computable<Set<String>>)() -> ResourceHelper.findIdsInFile(property.getModel().getFile())
        );
        for (String id : ids) {
          items.add("@id/" + id);
        }
      }
    }

    // No point sorting: TextFieldWithAutoCompletionListProvider performs its
    // own sorting afterwards (by calling compare() above)
    if (!types.isEmpty()) {

      // We include mipmap directly in the drawable maps
      if (types.contains(ResourceType.MIPMAP)) {
        types = types.clone();
        types.remove(ResourceType.MIPMAP);
        types.add(ResourceType.DRAWABLE);
      }

      items.addAll(ResourceHelper.getCompletionFromTypes(facet, types, false));
    }

    return items;
  }

  public void updateCompletions(@NotNull List<String> items) {
    myCompletionProvider.setItems(items);
  }

  public boolean editorHasFocus() {
    if (super.hasFocus()) {
      return true;
    }
    Editor editor = getEditor();
    return editor != null && editor.getContentComponent().hasFocus();
  }

  private class LookupListenerHandler implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull PropertyChangeEvent event) {
      Object newValue = event.getNewValue();
      if (newValue instanceof Lookup) {
        Lookup lookup = (Lookup)newValue;
        if (lookup.getTopLevelEditor() == getEditor()) {
          myLookupListeners.forEach(lookup::addLookupListener);
        }
      }
    }
  }

  private static class CompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
    protected CompletionProvider() {
      super(null);
    }

    @Nullable
    @Override
    public PrefixMatcher createPrefixMatcher(@NotNull String prefix) {
      return new CamelHumpMatcher(prefix);
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull String item) {
      return item.startsWith(SdkConstants.ANDROID_PREFIX) ? AndroidIcons.Android : null;
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull String item) {
      return item;
    }

    @Nullable
    @Override
    protected String getTailText(@NotNull String item) {
      return null;
    }

    @Nullable
    @Override
    protected String getTypeText(@NotNull String item) {
      return null;
    }

    @Override
    public int compare(String item1, String item2) {
      return ResourceHelper.compareResourceReferences(item1, item2);
    }
  }
}
