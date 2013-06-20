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
package com.android.tools.idea.configurations;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.utils.Pair;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class TranslationDialog extends DialogWrapper {
  private final Module myModule;
  private Locale myLocale;
  private ProjectResources myProjectResources;
  private TranslationModel myModel;

  TranslationDialog(@NotNull Module module, @NotNull ProjectResources resources, @NotNull Locale locale) {
    super(module.getProject());
    myModule = module;
    myProjectResources = resources;
    myLocale = locale;

    String localeLabel = LocaleMenuAction.getLocaleLabel(myLocale, false);
    setTitle(String.format("Add Translation for %1$s", localeLabel));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myModel = new TranslationModel(myProjectResources);
    JBTable table = new JBTable(myModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JTextField textField = new JTextField();
    DefaultCellEditor cellEditor = new DefaultCellEditor(textField);
    cellEditor.setClickCountToStart(1);
    table.setDefaultEditor(String.class, cellEditor);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table).disableAddAction().disableRemoveAction();
    JPanel panel = decorator.createPanel();
    panel.setPreferredSize(new Dimension(800, 800));
    return panel;
  }

  public boolean createTranslation() {
    return myModel.createTranslation();
  }

  private static final int KEY_COLUMN = 0;
  private static final int DEFAULT_TRANSLATION_COLUMN = 1;
  private static final int NEW_TRANSLATION_COLUMN = 2;

  private class TranslationModel extends AbstractTableModel implements EditableModel {
    private Map<String, String> myTranslations;
    private Map<String, ResourceValue> myValues;
    private String[] myKeys;
    private final FolderConfiguration myFolderConfig = new FolderConfiguration();

    public TranslationModel(@NotNull ProjectResources resources) {
      myValues = resources.getConfiguredResources(ResourceType.STRING, myFolderConfig);

      myKeys = myValues.keySet().toArray(new String[myValues.size()]);
      Arrays.sort(myKeys);

      // TODO: Read in the actual XML files providing the default keys here
      // (they can be obtained via ResourceItem.getSourceFileList())
      // such that we can read all the attributes associated with each
      // item, and if it defines translatable=false, or the filename is
      // donottranslate.xml, we can ignore it, and in other cases just
      // duplicate all the attributes (such as "formatted=true", or other
      // local conventions such as "product=tablet", or "msgid="123123123",
      // etc.)

      myTranslations = Maps.newHashMapWithExpectedSize(myKeys.length);
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return myKeys.length;
    }

    @Override
    public Object getValueAt(int row, int col) {
      String key = myKeys[row];
      switch (col) {
        case KEY_COLUMN :
          return key;

        case DEFAULT_TRANSLATION_COLUMN : {
          ResourceValue value = myValues.get(key);
          if (value != null) {
            return value.getValue();
          }

          return "";
        }
        case NEW_TRANSLATION_COLUMN :
        default:
          String translation = myTranslations.get(key);
          if (translation != null) {
            return translation;
          }

          return "";
      }
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case KEY_COLUMN :
          return "Key";
        case DEFAULT_TRANSLATION_COLUMN :
          return "Default";
        case NEW_TRANSLATION_COLUMN : {
          return LocaleMenuAction.getLocaleLabel(myLocale, false);
        }
        default:
          assert false : column;
          return "";
      }
    }

    @Override
    public Class getColumnClass(int c) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col == NEW_TRANSLATION_COLUMN;
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
      myTranslations.put(myKeys[row], aValue.toString());
    }

    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeRow(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      String temp = myKeys[oldIndex];
      myKeys[oldIndex] = myKeys[newIndex];
      myKeys[newIndex] = temp;
    }

    /** Actually create the new translation file and write it to disk */
    public boolean createTranslation() {
      Pair<String, VirtualFile> result = ApplicationManager.getApplication().runWriteAction(new Computable<Pair<String, VirtualFile>>() {
        @Nullable
        @Override
        public Pair<String, VirtualFile> compute() {
          FolderConfiguration folderConfig = new FolderConfiguration();
          folderConfig.setLanguageQualifier(myLocale.language);
          if (myLocale.hasRegion()) {
            folderConfig.setRegionQualifier(myLocale.region);
          }
          String folderName = folderConfig.getFolderName(ResourceFolderType.VALUES);
          String fileName = "strings.xml";
          try {
            AndroidFacet facet = AndroidFacet.getInstance(myModule);
            if (facet == null) {
              return Pair.of("Not an Android project", null);
            }
            VirtualFile res = facet.getPrimaryResourceDir();
            assert res != null;
            VirtualFile newParentFolder = res.findChild(folderName);
            if (newParentFolder == null) {
              newParentFolder = res.createChildDirectory(this, folderName);
              if (newParentFolder == null) {
                return Pair.of(String.format("Could not create folder %1$s in %2$s", folderName, res.getPath()), null);
              }
            }

            final VirtualFile existing = newParentFolder.findChild(fileName);
            if (existing != null && existing.exists()) {
              return Pair.of(String.format("File 'res/%1$s/%2$s' already exists!", folderName, fileName), null);
            }

            String text = createTranslationXml();
            VirtualFile newFile = newParentFolder.createChildData(this, fileName);
            VfsUtil.saveText(newFile, text);
            PsiFile file = PsiManager.getInstance(myModule.getProject()).findFile(newFile);
            assert file instanceof XmlFile : file;
            return Pair.of(null, newFile);
          }
          catch (IOException e2) {
            return Pair.of(String.format("Failed to create File 'res/%1$s/%2$s' : %3$s", folderName, fileName, e2.getMessage()), null);
          }
        }
      });

      String error = result.getFirst();
      VirtualFile newFile = result.getSecond();
      Project project = myModule.getProject();
      if (error != null) {
        Messages.showErrorDialog(project, error, "Create Translation");
        return false;
      }
      else {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newFile, -1);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
        return true;
      }
    }

    private String createTranslationXml() {
      StringBuilder sb = new StringBuilder(myKeys.length * 120);
      sb.append("<resources>\n\n");
      for (String key : myKeys) {
        String value = myTranslations.get(key);
        if (value == null || value.trim().isEmpty()) {
          continue;
        }
        sb.append("    <string name=\"");
        sb.append(key);
        sb.append("\">");
        sb.append(ValueXmlHelper.escapeResourceString(value));
        sb.append("</string>\n");
      }
      sb.append("\n</resources>");

      return sb.toString();
    }
  }
}
