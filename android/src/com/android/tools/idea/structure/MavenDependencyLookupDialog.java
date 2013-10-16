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
package com.android.tools.idea.structure;

import com.android.SdkConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PairProcessor;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MavenDependencyLookupDialog extends DialogWrapper {
  private JBLabel myInfoLabel;
  private final Project myProject;
  private AsyncProcessIcon myProgressIcon;
  private ComboboxWithBrowseButton myComboComponent;
  private JPanel myPanel;
  private JBLabel myCaptionLabel;
  private final THashMap<String, Pair<MavenArtifactInfo, MavenRepositoryInfo>> myCoordinates
    = new THashMap<String, Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
  private final Map<String, MavenRepositoryInfo> myRepositories = new TreeMap<String, MavenRepositoryInfo>();
  private final ArrayList<String> myShownItems = new ArrayList<String>();
  private final JComboBox myCombobox;

  private String myFilterString;
  private boolean myInUpdate;

  public MavenDependencyLookupDialog(Project project, final @Nullable String initialSearch) {
    super(project, true);
    myProject = project;
    myProgressIcon.suspend();
    myCaptionLabel.setText(
      XmlStringUtil.wrapInHtml(StringUtil.escapeXml("enter keyword, pattern or class name to search by or Maven coordinates, " +
                                                    "i.e. 'guava', 'com.google.code.gson:gson:2.2.4':")
      ));
    myInfoLabel.setPreferredSize(
      new Dimension(myInfoLabel.getFontMetrics(myInfoLabel.getFont()).stringWidth("Showing: 1000"), myInfoLabel.getPreferredSize().height));

    myComboComponent.setButtonIcon(AllIcons.Actions.Menu_find);
    myComboComponent.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performSearch();
      }
    });
    myCombobox = myComboComponent.getComboBox();
    myCombobox.setModel(new CollectionComboBoxModel(myShownItems, null));
    myCombobox.setEditable(true);
    final JTextField textField = (JTextField)myCombobox.getEditor().getEditorComponent();
    textField.setColumns(20);
    if (initialSearch != null) {
      textField.setText(initialSearch);
    }
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProgressIcon.isDisposed()) {
              return;
            }
            updateComboboxSelection(null, false);
          }
        });
      }
    });
    myCombobox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean popupVisible = myCombobox.isPopupVisible();
        if (!myInUpdate && (!popupVisible || myCoordinates.isEmpty())) {
          performSearch();
        } else {
          final String item = (String)myCombobox.getSelectedItem();
          if (StringUtil.isNotEmpty(item)) {
            ((JTextField)myCombobox.getEditor().getEditorComponent()).setText(item);
          }
        }
      }
    });
    updateInfoLabel();
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombobox;
  }

  private void updateComboboxSelection(final String searchText, boolean force) {
    final String prevFilter = myFilterString;
    final JTextComponent field = (JTextComponent)myCombobox.getEditor().getEditorComponent();
    final int caret = field.getCaretPosition();
    myFilterString = field.getText();

    if (!force && Comparing.equal(myFilterString, prevFilter)) {
      return;
    }
    int prevSize = myShownItems.size();
    myShownItems.clear();

    myInUpdate = true;
    final boolean itemSelected = myCoordinates.containsKey(myFilterString) &&
                                 Comparing.strEqual((String)myCombobox.getSelectedItem(), myFilterString, false);
    final boolean filtered;
    if (itemSelected) {
      myShownItems.addAll(myCoordinates.keySet());
      filtered = false;
    } else {
      final String[] parts = myFilterString.split(" ");
      main:
      for (String coordinate : myCoordinates.keySet()) {
        for (String part : parts) {
          if (!StringUtil.containsIgnoreCase(coordinate, part)) {
            continue main;
          }
        }
        myShownItems.add(coordinate);
      }
      filtered = !myShownItems.isEmpty();
      if (!filtered) {
        myShownItems.addAll(myCoordinates.keySet());
      }
      myCombobox.setSelectedItem(null);
    }

    Collections.sort(myShownItems, new Comparator<String>() {
      @Override
      public int compare(String s1, String s2) {
        MavenArtifactInfo mai1 = myCoordinates.get(s1).first;
        MavenArtifactInfo mai2 = myCoordinates.get(s2).first;
        int score = calculateSearchScore(searchText, mai2) - calculateSearchScore(searchText, mai1);
        if (score != 0) {
          return score;
        } else {
          return mai2.getVersion().compareTo(mai1.getVersion());
        }
      }

      private int calculateSearchScore(String searchText, MavenArtifactInfo mai) {
        if (searchText == null) {
          return 0;
        }
        int score = 0;
        if (mai.getArtifactId().equals(searchText)) {
          score++;
        }
        if (mai.getArtifactId().contains(searchText)) {
          score++;
        }
        if (mai.getGroupId().contains(searchText)) {
          score++;
        }
        return score;
      }
    });

    ((CollectionComboBoxModel)myCombobox.getModel()).update();
    myInUpdate = false;
    field.setText(myFilterString);
    field.setCaretPosition(caret);
    updateInfoLabel();
    if (filtered) {
      if (prevSize < 10 && myShownItems.size() > prevSize && myCombobox.isPopupVisible()) {
        myCombobox.setPopupVisible(false);
      }
      if (!myCombobox.isPopupVisible()) {
        myCombobox.setPopupVisible(filtered);
      }
    }
  }

  private boolean performSearch() {
    final String text = getCoordinateText();
    if (myCoordinates.contains(text)) {
      return false;
    }
    if (myProgressIcon.isRunning()) {
      return false;
    }
    myProgressIcon.resume();
    searchArtifacts(myProject, text, new PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean>() {
        @Override
        public boolean process(Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>> artifacts, Boolean tooMany) {
          if (myProgressIcon.isDisposed()) {
            return false;
          }
          if (tooMany != null) {
            myProgressIcon.suspend();
          }
          final int prevSize = myCoordinates.size();
          for (Pair<MavenArtifactInfo, MavenRepositoryInfo> each : artifacts) {
            myCoordinates.put(each.first.getGroupId() + ":" + each.first.getArtifactId() + ":" + each.first.getVersion() + "@" +
                              each.first.getPackaging(), each);
            String url = each.second != null? each.second.getUrl() : null;
            if (StringUtil.isNotEmpty(url) && !myRepositories.containsKey(url)) {
              myRepositories.put(url, each.second);
            }
          }
          String title = getTitle();
          String tooManyMessage = ": too many results found";
          if (tooMany != null) {
            boolean alreadyThere = title.endsWith(tooManyMessage);
            if (tooMany.booleanValue() && !alreadyThere) {
              setTitle(title + tooManyMessage);
            } else if (!tooMany.booleanValue() && alreadyThere) {
              setTitle(title.substring(0, title.length() - tooManyMessage.length()));
            }
          }
          updateComboboxSelection(text, prevSize != myCoordinates.size());
          return true;
        }
      });
    return true;
  }

  public static void searchArtifacts(final Project project, String coord,
                                     final PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean>
                                       resultProcessor) {
    if (coord == null || coord.length() == 0) {
      return;
    }
    final MavenArtifactInfo template;
    if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
      template = new MavenArtifactInfo(null, null, null, null, null, coord, null);
    } else {
      template = new MavenArtifactInfo(RepositoryAttachHandler.getMavenId(coord), null, null);
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven lookup", false) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String[] urls = MavenRepositoryServicesManager.getServiceUrls();
        boolean tooManyResults = false;
        final AtomicBoolean proceedFlag = new AtomicBoolean(true);

        for (int i = 0, length = urls.length; i < length; i++) {
          if (!proceedFlag.get()) {
            break;
          }
          final List<Pair<MavenArtifactInfo, MavenRepositoryInfo>> resultList = new ArrayList<Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
          try {
            String serviceUrl = urls[i];
            final List<MavenArtifactInfo> artifacts;
            artifacts = MavenRepositoryServicesManager.findArtifacts(template, serviceUrl);
            if (!artifacts.isEmpty()) {
              if (!proceedFlag.get()) {
                break;
              }
              final List<MavenRepositoryInfo> repositories = MavenRepositoryServicesManager.getRepositories(serviceUrl);
              final HashMap<String, MavenRepositoryInfo> map = new HashMap<String, MavenRepositoryInfo>();
              for (MavenRepositoryInfo repository : repositories) {
                map.put(repository.getId(), repository);
              }
              for (MavenArtifactInfo artifact : artifacts) {
                if (artifact == null) {
                  tooManyResults = true;
                } else if (!SdkConstants.EXT_JAR.equals(artifact.getPackaging()) && !SdkConstants.EXT_AAR.equals(artifact.getPackaging())) {
                  continue;
                } else {
                  resultList.add(Pair.create(artifact, map.get(artifact.getRepositoryId())));
                }
              }
            }
          } catch (Exception e) {
            MavenLog.LOG.error(e);
          } finally {
            if (!proceedFlag.get()) {
              break;
            }
            final Boolean aBoolean = i == length - 1 ? tooManyResults : null;
            ApplicationManager.getApplication().invokeLater(
              new Runnable() {
                public void run() {
                  proceedFlag.set(resultProcessor.process(resultList, aBoolean));
                }
              }, new Condition() {
                @Override
                public boolean value(Object o) {
                  return !proceedFlag.get();
                }
              });
          }
        }
      }
    });
  }

  private void updateInfoLabel() {
    myInfoLabel.setText("<html>Found: " + myCoordinates.size() + "<br>Showing: " + myCombobox.getModel().getSize() + "</html>");
  }

  @Override
  protected ValidationInfo doValidate() {
    if (!isValidCoordinateSelected()) {
      return new ValidationInfo("Please enter valid coordinate, discover it or select one from the list", myCombobox);
    }
    return super.doValidate();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    return myPanel;
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return MavenDependencyLookupDialog.class.getName();
  }

  private boolean isValidCoordinateSelected() {
    final String text = getCoordinateText();
    return text.split(":").length == 3;
  }

  public String getCoordinateText() {
    final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
    return field.getText();
  }

  private void createUIComponents() {
    myProgressIcon = new AsyncProcessIcon("Progress");
  }
}
