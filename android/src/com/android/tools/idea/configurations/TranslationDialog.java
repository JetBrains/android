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
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.android.utils.Pair;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.ATTR_NAME;

// TODO: How do we deal with translations for string arrays, plurals, etc?
// TODO: We're currently editing the flattened strings (e.g. the ones prepared for
//       text by the same code as the layout editor. Should we let users edit markup like \n and " and \u0041?
public class TranslationDialog extends DialogWrapper {
  private final AndroidFacet myFacet;
  private Locale myLocale;
  private TranslationModel myModel;
  private boolean myCreate;

  TranslationDialog(@NotNull AndroidFacet facet, @NotNull Locale locale, boolean create) {
    super(facet.getModule().getProject());
    myFacet = facet;
    myLocale = locale;
    myCreate = create;

    String localeLabel = LocaleMenuAction.getLocaleLabel(myLocale, false);
    setTitle(String.format((create ? "Add" : "Edit") + " Translation for %1$s", localeLabel));
    init();
    setOKActionEnabled(false);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myModel = new TranslationModel();
    final JBTable table = new JBTable(myModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setStriped(true);

    JTextField textField = new JTextField();
    DefaultCellEditor cellEditor = new DefaultCellEditor(textField);
    cellEditor.setClickCountToStart(1);
    table.setDefaultEditor(String.class, cellEditor);

    // Ensure we don't crop text when editing with the cell editor
    table.setRowHeight(textField.getPreferredSize().height);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table).disableAddAction().disableRemoveAction();
    JPanel panel = decorator.createPanel();
    panel.setPreferredSize(new Dimension(800, 800));

    if (myModel.getRowCount() > 0) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          table.requestFocus();
          table.editCellAt(0, NEW_TRANSLATION_COLUMN);
        }
      });
    }

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

    public TranslationModel() {
      LocalResourceRepository resources = ProjectResourceRepository.getProjectResources(myFacet, true);
      // Nonexistent language qualifier: trick it to fall back to the default locale
      myFolderConfig.setLanguageQualifier(new LanguageQualifier("xx"));
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

      if (!myCreate && myLocale.hasLanguage()) {
        FolderConfiguration config = new FolderConfiguration();
        config.setLanguageQualifier(myLocale.language);
        if (myLocale.hasRegion()) {
          config.setRegionQualifier(myLocale.region);
        }

        for (String key : myKeys) {
          List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, key);
          if (items == null) {
            continue;
          }
          ResourceItem match = (ResourceItem) config.findMatchingConfigurable(items);
          if (match != null) {
            LanguageQualifier languageQualifier = match.getConfiguration().getEffectiveLanguage();
            if (!myLocale.language.equals(languageQualifier)) {
              // This configured value is not in the right language; that means this string
              // was not translated to this locale
              continue;
            }

            ResourceValue value = match.getResourceValue(false);
            if (value != null) {
              myTranslations.put(key, value.getValue());
            }
          }
        }
      }
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
      String string = aValue.toString();
      if (!string.isEmpty() && !isOKActionEnabled()) {
        setOKActionEnabled(true);
      }
      myTranslations.put(myKeys[row], string);
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
          // First update existing translations
          if (myCreate) {
            return createTranslationFile();
          }
          else {
            return updateTranslations();
          }
        }
      });

      String error = result.getFirst();
      VirtualFile newFile = result.getSecond();
      Project project = myFacet.getModule().getProject();
      if (error != null) {
        Messages.showErrorDialog(project, error, "Create Translation");
        return false;
      }
      else if (newFile != null) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newFile, -1);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      }
      return true;
    }

    private Pair<String, VirtualFile> createTranslationFile() {
      FolderConfiguration folderConfig = new FolderConfiguration();
      folderConfig.setLanguageQualifier(myLocale.language);
      if (myLocale.hasRegion()) {
        folderConfig.setRegionQualifier(myLocale.region);
      }
      String folderName = folderConfig.getFolderName(ResourceFolderType.VALUES);
      String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING);
      assert fileName != null;
      try {
        VirtualFile res = myFacet.getPrimaryResourceDir();
        assert res != null;
        VirtualFile newParentFolder = res.findChild(folderName);
        if (newParentFolder == null) {
          newParentFolder = res.createChildDirectory(this, folderName);
          if (newParentFolder == null) {
            return Pair.of(String.format("Could not create folder %1$s in %2$s", folderName, res.getPath()), null);
          }
        }

        final VirtualFile existing = newParentFolder.findChild(fileName);
        String text = createTranslationXml(true);
        if (existing != null && existing.exists()) {
          return Pair.of(String.format("File 'res/%1$s/%2$s' already exists!", folderName, fileName), null);
        }

        VirtualFile newFile = newParentFolder.createChildData(this, fileName);
        VfsUtil.saveText(newFile, text);
        return Pair.of(null, newFile);
      }
      catch (IOException e2) {
        return Pair.of(String.format("Failed to create File 'res/%1$s/%2$s' : %3$s", folderName, fileName, e2.getMessage()), null);
      }
    }

    private Pair<String, VirtualFile> updateTranslations() {
      VirtualFile firstFile = null;
      PsiManager manager = PsiManager.getInstance(myFacet.getModule().getProject());
      FolderConfiguration config = new FolderConfiguration();
      config.setLanguageQualifier(myLocale.language);
      if (myLocale.hasRegion()) {
        config.setRegionQualifier(myLocale.region);
      }
      LocalResourceRepository resources = ProjectResourceRepository.getProjectResources(myFacet, true);
      Map<String, ResourceValue> existing = resources.getConfiguredResources(ResourceType.STRING, config);
      Set<String> handled = Sets.newHashSet();
      for (String key : myKeys) {
        ResourceValue value = existing.get(key);
        if (value != null) {
          if (value.getValue().equals(myTranslations.get(key))) {
            handled.add(key);
          }
        }
      }

      for (String key : myKeys) {
        List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, key);
        if (items == null) {
          continue;
        }
        ResourceItem match = (ResourceItem) config.findMatchingConfigurable(items);
        if (match != null) {
          LanguageQualifier languageQualifier = match.getConfiguration().getEffectiveLanguage();
          if (!myLocale.language.equals(languageQualifier)) {
            // This configured value is not in the right language; that means this string
            // was not translated to this locale
            continue;
          }
          ResourceFile source = match.getSource();
          if (source != null) {
            VirtualFile definedIn = LocalFileSystem.getInstance().findFileByIoFile(source.getFile());
            if (definedIn == null) {
              continue;
            }
            if (firstFile == null) {
              firstFile = definedIn;
            }
            if (handled.contains(key)) {
              continue;
            }
            PsiFile file = manager.findFile(definedIn);
            if (file == null || !(file instanceof XmlFile)) {
              continue;
            }
            XmlFile resourceFile = (XmlFile)file;
            XmlTag rootTag = resourceFile.getRootTag();
            if (rootTag == null) {
              continue;
            }
            for (XmlTag item : rootTag.getSubTags()) {
              XmlAttribute name = item.getAttribute(ATTR_NAME);
              if (name != null && key.equals(name.getValue())) {
                String translation = myTranslations.get(key);
                if (translation == null || translation.isEmpty()) {
                  item.delete();
                } else {
                  String escaped = ValueXmlHelper.escapeResourceString(translation);
                  XmlTagValue itemValue = item.getValue();
                  itemValue.setText(escaped);
                }
                firstFile = definedIn;
                handled.add(key);

                break;
              }
            }
          }
        }
      }

      String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING);
      assert fileName != null;
      String folderName = ResourceFolderType.VALUES.getName() + '-' + myLocale.language.getValue();
      boolean format = false;
      List<String> folders = Collections.singletonList(folderName);
      for (String key : myKeys) {
        if (!handled.contains(key)) {
          String value = myTranslations.get(key);
          if (value != null && !value.trim().isEmpty()) {
            Module module = myFacet.getModule();
            AndroidResourceUtil.createValueResource(module, key, ResourceType.STRING, fileName, folders, value);
            format = true;
          }
        }
      }
      if (format) {
        // This is what AndroidResourceUtil uses (see AndroidResourceUtil#findOrCreateResourceFile)
        final VirtualFile resDir = myFacet.getPrimaryResourceDir();
        if (resDir != null) {
          VirtualFile file = resDir.findFileByRelativePath(folderName + '/' + fileName);// deliberately system independent path
          if (file != null) {
            firstFile = file;

            // Format file
            PsiFile psiFile = manager.findFile(file);
            if (psiFile != null) {
              CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
              codeStyleManager.reformat(psiFile);
            }
          }
        }

      }

      // All translations were handled as edits to existing XML definitions; no need
      // to create new file
      return Pair.of(null, firstFile);
    }

    private String createTranslationXml(boolean includeRoot) {
      StringBuilder sb = new StringBuilder(myKeys.length * 120);
      if (includeRoot) {
        sb.append("<resources>\n\n");
      }
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
      if (includeRoot) {
        sb.append("\n</resources>");
      }

      return sb.toString();
    }
  }
}
