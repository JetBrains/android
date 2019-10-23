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
package com.android.tools.idea.uibuilder.mockup.editor.creators.forms;

import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.android.tools.idea.uibuilder.mockup.editor.creators.WidgetCreator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Popup allowing the user to import colors as a resource and a set at tag name from an autocomplete field
 * if the {@link AndroidFacet} is provided in {@link #ViewAndColorForm(String, ColorSelectedListener, AndroidFacet)}, or by setting it using
 * {@link AutoCompleteForm#setFacet(AndroidFacet)}
 *
 * @see AutoCompleteForm
 */
public class ViewAndColorForm {

  private static final int MAX_COLOR = 5;
  private static final String DEFAULT_TITLE = "Import color";
  private static final String WAIT_TEXT = "Finding colors in selection...";
  private static final String NO_COLOR_SELECTED = "No color selected";
  private static final Logger LOGGER = Logger.getInstance(ViewAndColorForm.class);

  private JPanel myComponent;
  private JTextField myColorName;
  private JButton myAddButton;
  private JPanel myColor;
  private JButton myDismissButton;
  private JLabel myTitle;
  private JPanel myColorListPanel;
  private JProgressBar myProgressBar;
  private JPanel myBottomPanel;
  private JBLabel myViewTypeLabel;
  private AutoCompleteForm myAutoCompleteForm;
  private JLabel myErrorLabel;

  private final List<Color> myColorList = new ArrayList<>();
  private final WidgetCreator.ColorResourceHolder mySelectedColor = new WidgetCreator.ColorResourceHolder(null, null);
  @Nullable private AndroidFacet myFacet;
  @Nullable private ActionListener myAddListener;

  /**
   * Create a new form with the provided title.
   * The colorListener is called when the add or dismiss button are clicked.
   * The argument passed to the listener will be null if the cause of
   * calling is a dismiss action
   *
   * @param title         Title to display in the popup
   * @param colorListener Listener called when a color is selected
   */
  public ViewAndColorForm(@Nullable String title, @NotNull ColorSelectedListener colorListener) {
    this(title, colorListener, null);
  }

  /**
   * Create a new form with the provided title.
   * The colorListener is called when the add or dismiss button are clicked.
   * The argument passed to the listener will be null if the cause of
   * calling is a dismiss action
   *
   * If the facet is not null, also displays a field to set the tag of the view
   *
   * @param title         Title to display in the popup
   * @param colorListener Listener called when a color is selected
   * @param facet         facet to activate view tag field with autocompletion
   */
  public ViewAndColorForm(@Nullable String title, @NotNull ColorSelectedListener colorListener, @Nullable AndroidFacet facet) {
    myFacet = facet;
    myErrorLabel = new JBLabel("", UIUtil.ComponentStyle.REGULAR);
    myErrorLabel.setForeground(JBColor.RED);
    myErrorLabel.setMaximumSize(new Dimension(150, -1));
    myColorListPanel.add(new JLabel(WAIT_TEXT));
    myTitle.setText(title == null ? DEFAULT_TITLE : title);

    myAddButton.addActionListener(e -> {
      mySelectedColor.name = myColorName.isEnabled() ? myColorName.getText() : "";
      colorListener.colorSelected(mySelectedColor);
    });

    myDismissButton.addActionListener(e -> colorListener.colorSelected(null));
    myProgressBar.setIndeterminate(true);
    myColorName.getDocument().addDocumentListener(createColorNameDocumentListener());
    myColor.setBorder(BorderFactory.createLineBorder(JBColor.border()));

    if (myFacet == null) {
      myAutoCompleteForm.getComponent().setVisible(false);
      myViewTypeLabel.setVisible(false);
    }
    else {
      myAutoCompleteForm.setFacet(myFacet);
    }
  }

  private void createUIComponents() {
    myComponent = new ToolRootPanel();
    myAutoCompleteForm = new AutoCompleteForm(myFacet);
  }

  public void setAddListener(@NotNull ActionListener addListener) {
    if (myAddListener != null) {
      myAddButton.removeActionListener(myAddListener);
    }
    myAddListener = addListener;
    myAddButton.addActionListener(addListener);
  }

  /**
   * @return A new {@link DocumentListener} that will validate the color name and
   * enable the add button is the color value is correct
   */
  @NotNull
  private DocumentListener createColorNameDocumentListener() {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myAddButton.setEnabled(validateColorName(e.getDocument()));
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myAddButton.setEnabled(validateColorName(e.getDocument()));
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    };
  }

  /**
   * Validate the text from the given document as a correcte Color Resource Name
   *
   * @param document the document containing the color name
   * @return true if the name is correct, false otherwise
   */
  private boolean validateColorName(@NotNull Document document) {
    // TODO Check if the color name already exist
    if (mySelectedColor == null
        || mySelectedColor.value == null
        || document.getLength() < 1) {
      myErrorLabel.setText("");
      return true;
    }

    try {
      String text = document.getText(0, document.getLength());
      try {
        ValueResourceNameValidator.validate(text, ResourceType.COLOR, null);
        myErrorLabel.setText("");
        myAddButton.setEnabled(true);
        return true;
      }
      catch (MergingException ex) {
        myErrorLabel.setText
          (String.format("<html> %s </html>",
                         ValueResourceNameValidator.getErrorText(text, ResourceType.COLOR)));
        myAddButton.setEnabled(false);
        return false;
      }
    }
    catch (BadLocationException ex) {
      LOGGER.warn(ex);
      return false;
    }
  }

  /**
   * Set the selected color and the background of the myColor Panel to color
   *
   * @param color the color to set as selected
   */
  private void setSelectedColor(@Nullable WidgetCreator.ColorResourceHolder color) {
    // TODO Check if the selected color value already exist in the resource
    // and prefill the name with the existing value
    mySelectedColor.value = color != null ? color.value : null;
    mySelectedColor.name = color != null ? color.name : null;
    if (color == null) {
      myColor.setBackground(JBColor.background());
      myColorName.setEnabled(false);
      myColorName.setText(NO_COLOR_SELECTED);
    }
    else {
      myColor.setBackground(mySelectedColor.value);
      myColorName.setEnabled(true);
      myColorName.setText("");
    }
  }

  /**
   * @return The root component of this form
   */
  @NotNull
  public JPanel getComponent() {
    return myComponent;
  }

  /**
   * Display the provided color in the popup
   *
   * @param colors
   */
  @SuppressWarnings("UseJBColor")
  public void addColors(@NotNull Collection<ExtractedColor> colors) {
    myBottomPanel.removeAll();
    myErrorLabel.setPreferredSize(myBottomPanel.getSize());
    myErrorLabel.setMaximumSize(myErrorLabel.getPreferredSize());
    myBottomPanel.add(myErrorLabel);
    colors.forEach(color -> myColorList.add(new Color(color.getColor())));
    if (!myColorList.isEmpty()) {

      // Set the color list and the selected color
      mySelectedColor.value = myColorList.get(0);
      myColor.setBackground(myColorList.get(0));
      myColor.setEnabled(true);
      myColor.setOpaque(true);

      // Add Color panel
      ColorPanel colorPanel = new ColorPanel(
        myColorList,
        colorResourceHolder -> setSelectedColor(colorResourceHolder));
      myColorListPanel.removeAll();
      myColorListPanel.add(colorPanel, BorderLayout.CENTER);
      myColorListPanel.doLayout();
      myComponent.getParent().validate();
      colorPanel.fadeIn();
    }
  }

  /**
   * Set the progresse of the progress bar.
   * Used to show the progress of the color extraction
   *
   * @param progress 0 <= progress <= 100
   */
  public void setProgress(int progress) {
    myProgressBar.setIndeterminate(false);
    myProgressBar.setValue(progress);
  }

  @NotNull
  public String getTagName() {
    return myAutoCompleteForm == null ? "" : myAutoCompleteForm.getTagName();
  }

  /**
   * Panel to show the list of color
   */
  static class ColorPanel extends JPanel {

    private static final int COLOR_ICON_SIZE = 20;
    private static final int MARGINS = 2;
    private static final int USED_SPACE = COLOR_ICON_SIZE + MARGINS;
    private static final Dimension DIMENSION = new Dimension(-1, -1);
    private final List<Color> myColorList;
    private final ColorSelectedListener myListener;
    private final MouseAdapter myMouseAdapter;
    private int myHoveredColor;
    private float myAlpha = 0;

    /**
     * Create a new Panel to display the provided list of color. When on of the color is clicked
     * it will call the listener and passe the clicker color as parameter
     *
     * @param colorList The color list to display
     * @param listener  The listener to call when a color is clicked
     */
    public ColorPanel(@NotNull List<Color> colorList, @NotNull ColorSelectedListener listener) {
      super();
      setOpaque(true);
      setMinimumSize(new Dimension(USED_SPACE, USED_SPACE));
      myColorList = colorList;
      myListener = listener;
      myMouseAdapter = createMouseAdapter();
      addMouseListener(myMouseAdapter);
      addMouseMotionListener(myMouseAdapter);
    }

    /**
     * Create the mouse adapter for the panel
     */
    @NotNull
    private MouseAdapter createMouseAdapter() {
      return new MouseAdapter() {

        /**
         * On click, notify the listener with the selected color
         */
        @Override
        public void mouseClicked(MouseEvent e) {
          int colorIndex = getColorIndexAtPosition(e.getX(), e.getY());
          if (colorIndex >= 0 && colorIndex < Math.min(myColorList.size(), MAX_COLOR)) {
            myListener.colorSelected(new WidgetCreator.ColorResourceHolder(myColorList.get(colorIndex), null));
          }
          else if (colorIndex == Math.min(myColorList.size(), MAX_COLOR)) {
            myListener.colorSelected(null);
          }
        }

        /**
         * Set the index of the hovered color
         */
        @Override
        public void mouseMoved(MouseEvent e) {
          int hovered = getColorIndexAtPosition(e.getX(), e.getY());
          if (hovered != myHoveredColor) {
            myHoveredColor = hovered;
            repaint();
          }
        }
      };
    }

    /**
     * Find the index of the color at the position x, y relative to this panel
     *
     * @return the index of clicked color, can return a value out of the bounds of the color list, so the
     * return value shoudld be checked
     */
    private int getColorIndexAtPosition(int x, int y) {
      return (x / USED_SPACE) + (((myColorList.size() + 1) * USED_SPACE) % getWidth()) * (y / USED_SPACE);
    }

    /**
     * Display the colors with a fade in animation
     */
    private void fadeIn() {
      long startTime = System.currentTimeMillis();
      long endTime = startTime + 300;
      Timer timer = new Timer(20, e -> {
        long time = System.currentTimeMillis();
        if (time >= endTime) {
          ((Timer)e.getSource()).stop();
          myAlpha = 1f;
        }
        else {
          myAlpha = (time - startTime) / (float)(endTime - startTime);
        }
        repaint();
      });
      timer.setRepeats(true);
      timer.setCoalesce(true);
      timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
      DIMENSION.setSize(DIMENSION);
      setPreferredSize(DIMENSION);
      super.paintComponent(g);
      Color oldColor = g.getColor();
      drawColorRectangles(g);
      drawNoColorButton(g);
      g.setColor(oldColor);
    }

    /**
     * Draw the list of rectangle filled with the color from color list
     */
    private void drawColorRectangles(@NotNull Graphics g) {
      ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
      for (int i = 0; i < myColorList.size() && i <= MAX_COLOR; i++) {
        g.setColor(myColorList.get(i));
        int width = getWidth();
        int x = i * USED_SPACE % width;
        int y = (i * USED_SPACE) / width;
        g.fillRect(x, y, COLOR_ICON_SIZE, COLOR_ICON_SIZE);
        g.setColor(JBColor.border());
        g.drawRect(x, y, COLOR_ICON_SIZE, COLOR_ICON_SIZE);
        if (i == myHoveredColor) {
          g.setColor(JBColor.border().darker());
          g.drawRect(x, y, COLOR_ICON_SIZE, COLOR_ICON_SIZE);
        }
      }
    }

    /**
     * Draw a rectangle to unselect the color
     */
    private void drawNoColorButton(@NotNull Graphics g) {
      int i = Math.min(MAX_COLOR, myColorList.size());
      int width = getWidth();
      int x = (i * USED_SPACE) % width;
      int y = (i * USED_SPACE) / width;
      g.setColor(JBColor.foreground());
      g.fillRect(x, y, COLOR_ICON_SIZE, COLOR_ICON_SIZE);
      g.setColor(JBColor.background());
      g.drawLine(x, y, x + COLOR_ICON_SIZE, y + COLOR_ICON_SIZE);
      g.drawLine(x, y + COLOR_ICON_SIZE, x + COLOR_ICON_SIZE, y);
      if (i == myHoveredColor) {
        g.setColor(JBColor.border());
        g.drawRect(i * USED_SPACE % width, (i * USED_SPACE) / width, COLOR_ICON_SIZE, COLOR_ICON_SIZE);
      }
    }
  }

  /**
   * Listener used as a callback to notify when a color has been selected.
   * It is also used to communicate the selected color from the {@link ColorPanel}
   */
  public interface ColorSelectedListener {

    /**
     * The argument can be null, or any of its field can be null depending on the information
     * the calling method has provided
     *
     * @param colorResourceHolder The holder with the name and the value of the color
     */
    void colorSelected(@Nullable WidgetCreator.ColorResourceHolder colorResourceHolder);
  }
}
