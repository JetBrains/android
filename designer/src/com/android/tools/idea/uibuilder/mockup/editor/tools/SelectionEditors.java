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
package com.android.tools.idea.uibuilder.mockup.editor.tools;

import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.mockup.editor.SelectionLayer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Display the text fields to edit the selection of the {@link MockupViewPanel}.
 */
public class SelectionEditors extends JPanel implements MockupViewPanel.SelectionListener {

  /**
   * Index to retrieve the document of the {@link JTextField} for
   * width, height, x, y in {@link #myBoundsDocuments}
   */
  private static int W = 0, H = 1, X = 2, Y = 3;

  /**
   * The documents associated with the created {@link JTextField}
   */
  private final Document[] myBoundsDocuments = new Document[4];

  /**
   * The {@link MockupViewPanel} containing the {@link SelectionLayer} to edit
   */
  private final MockupViewPanel myMockupViewPanel;
  private boolean myBoundsUpdating;
  private final DocumentListener myDocumentListener;

  /**
   * Create a new JPanel containing text field to edit and get the selection in the
   * provided {@link MockupViewPanel}
   *
   * @param mockupViewPanel where the selection will be edited and retrieved
   */
  public SelectionEditors(@NotNull MockupViewPanel mockupViewPanel) {
    myMockupViewPanel = mockupViewPanel;
    myDocumentListener = createDocumentListener();
    mockupViewPanel.addSelectionListener(this);
    createPositionText();
  }

  /**
   * Create a document listener that will update the {@link MockupViewPanel} selection
   * when the attached document is updated.
   *
   * @return the new document listener
   */
  private DocumentListener createDocumentListener() {
    return new DocumentListener() {

      private void processChange(@NotNull DocumentEvent e) {
        if (myBoundsUpdating) {
          return;
        }
        try {
          Document document = e.getDocument();
          int value;
          if (document.getLength() <= 0) {
            value = 0;
          }
          else {
            value = Integer.parseInt(document.getText(0, document.getLength()));
          }

          SelectionLayer selectionLayer = myMockupViewPanel.getSelectionLayer();
          Rectangle selection = selectionLayer.getSelection();
          if (document == myBoundsDocuments[W]) {
            selectionLayer.setSelection(selection.x, selection.y, value, selection.height);
          }
          else if (document == myBoundsDocuments[H]) {
            selectionLayer.setSelection(selection.x, selection.y, selection.width, value);
          }
          else if (document == myBoundsDocuments[X]) {
            selectionLayer.setSelection(value, selection.y, selection.width, selection.height);
          }
          else if (document == myBoundsDocuments[Y]) {
            selectionLayer.setSelection(selection.x, value, selection.width, selection.height);
          }
        }
        catch (BadLocationException | NumberFormatException ex) {
          // Do nothing
        }
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        processChange(e);
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        processChange(e);
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    };
  }

  /**
   * Update the content of {@link #myBoundsDocuments} using the bounds
   * of the selection.
   *
   * @param selection
   */
  private void updateDocuments(@NotNull Rectangle selection) {
    try {
      myBoundsUpdating = true;
      myBoundsDocuments[W].remove(0, myBoundsDocuments[W].getLength());
      myBoundsDocuments[H].remove(0, myBoundsDocuments[H].getLength());
      myBoundsDocuments[X].remove(0, myBoundsDocuments[X].getLength());
      myBoundsDocuments[Y].remove(0, myBoundsDocuments[Y].getLength());

      myBoundsDocuments[W].insertString(0, String.valueOf(selection.width), null);
      myBoundsDocuments[H].insertString(0, String.valueOf(selection.height), null);
      myBoundsDocuments[X].insertString(0, String.valueOf(selection.x), null);
      myBoundsDocuments[Y].insertString(0, String.valueOf(selection.y), null);
    }
    catch (BadLocationException e) {
      // Do nothing
    }
    finally {
      myBoundsUpdating = false;
    }
  }

  /**
   * Create and add the text field and associated label to edit the selection bounds
   */
  private void createPositionText() {
    createLabelAndField("w", myBoundsDocuments, W);
    createLabelAndField("h", myBoundsDocuments, H);
    createLabelAndField("x", myBoundsDocuments, X);
    createLabelAndField("y", myBoundsDocuments, Y);
  }

  /**
   * Create a {@link JLabel} and a {@link JFormattedTextField} accepting only integers.
   *
   * @param label         The content and name of the label
   * @param documents     The array of documents to that will be filled
   *                      with the {@link JFormattedTextField}'s document at the provided index
   * @param documentIndex The index in document where the {@link JFormattedTextField}'s document will be inserted.
   *                      If the index is out of the bounds of documents, the array won't be filled
   * @return The newly created text field
   */
  @NotNull
  private JTextField createLabelAndField(@NotNull String label,@NotNull Document[] documents, int documentIndex) {

    JLabel jLabel = new JLabel(label);
    jLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
    JFormattedTextField jTextField = new JFormattedTextField(NumberFormat.getIntegerInstance(Locale.US));
    jTextField.setName(label);
    jTextField.setHorizontalAlignment(SwingConstants.CENTER);
    jTextField.setColumns(4);
    jTextField.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL));
    jTextField.setPreferredSize(new Dimension(5, jTextField.getPreferredSize().height));
    jLabel.setLabelFor(jTextField);
    if (documentIndex >= 0 && documentIndex < documents.length) {
      documents[documentIndex] = jTextField.getDocument();
      documents[documentIndex].addDocumentListener(myDocumentListener);
    }
    add(jLabel);
    add(jTextField);
    return jTextField;
  }

  public void setSelection(@NotNull Rectangle selection) {
    updateDocuments(selection);
  }

  @Override
  public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {

  }

  @Override
  public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
    updateDocuments(selection);
    setVisible(!selection.isEmpty());
  }
}
