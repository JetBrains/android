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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.pixelprobe.Guide;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel Displaying the mockup inside the ScreenView.
 *
 * It handle the positioning of the mockup inside the selected component and the export of the guidelines
 */
public class MockupInteractionPanel extends JBPanel implements ModelListener {

  private static final Color COMPONENT_FG_COLOR = JBColor.WHITE;
  private static final Color BACKGROUND_COLOR = JBColor.background();
  private static final Color GUIDE_COLOR = JBColor.GRAY;
  private static final Color GUIDE_SELECTED_COLOR = JBColor.RED;
  private static final Color GUIDE_HOVERED_COLOR = JBColor.RED.darker();
  private static final float ADJUST_SCALE = 0.9f;

  private final Mockup myMockup;
  private final ScreenView myScreenView;
  private final CoordinateConverter myScreenViewToPanel;
  private final CoordinateConverter myImageToMockupDestination;
  private final Rectangle myMockupDrawDestination;
  private final Dimension myImageSize;
  @Nullable private BufferedImage myImage;
  private Dimension myScreenViewSize;

  private Guide myHoveredGuideline;
  private boolean myShowGuideline;
  @NotNull final private List<Guide> myUnselectedGuidelines;
  @NotNull final private List<Guide> mySelectedGuidelines;
  private GuidelinesMouseInteraction myMouseInteraction;

  public MockupInteractionPanel(@NotNull ScreenView screenView, Mockup Mockup) {
    myScreenView = screenView;
    myMockup = Mockup;
    myMockupDrawDestination = new Rectangle();
    myImageSize = new Dimension();
    myImageToMockupDestination = new CoordinateConverter();
    myScreenViewToPanel = new CoordinateConverter();
    myScreenViewToPanel.setCenterInDestination();
    myScreenViewToPanel.setFixedRatio(true);
    myMockup.getComponent().getModel().addListener(this);

    // Creating two list of guidelines to store the selected and unselected guidelines
    // The two list should not contain the same element at the same time
    final List<Guide> guidelines = myMockup.getGuidelines();
    myUnselectedGuidelines = guidelines == null ? new ArrayList<>(0) : new ArrayList<>(guidelines);
    mySelectedGuidelines = new ArrayList<>();

    setImage(myMockup.getImage());
    setBackground(BACKGROUND_COLOR);
  }

  @Override
  public void paint(Graphics g) {
    // Initialization
    super.paint(g);
    final Graphics2D g2d = (Graphics2D)g;
    Color color = g.getColor();

    // Update to latest dimension
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);
    myScreenViewToPanel.setDimensions(getSize(), myScreenViewSize, ADJUST_SCALE);
    updateMockupDrawDestination();
    final Rectangle destination = myMockup.getBounds(myScreenView, myMockupDrawDestination);
    destination.x = myScreenViewToPanel.x(destination.x);
    destination.y = myScreenViewToPanel.y(destination.y);
    destination.width = myScreenViewToPanel.dX(destination.width);
    destination.height = myScreenViewToPanel.dY(destination.height);

    // Paint
    paintScreenView(g2d);
    paintMockup(g2d, destination);
    paintAllComponents(g2d, myMockup.getComponent().getRoot());

    // Only paint guideline if the user as selected the checkbox in MockupEditor
    if (myShowGuideline) {
      paintGuidelines(g2d, destination);
    }
    g.setColor(color);
  }

  /**
   * Paint the selected, unselected and hovered guidelines in the destination coordinate system
   * @param g2d The graphic context
   * @param destination The Rectangle representing the origin and size of the destination coordinates system
   */
  private void paintGuidelines(Graphics2D g2d, Rectangle destination) {
    if (myUnselectedGuidelines.isEmpty() && mySelectedGuidelines.isEmpty() && myHoveredGuideline == null) {
      return;
    }

    // Update the CoordinatesConverter to the destination and image dimension ans position
    myImageToMockupDestination.setDimensions(destination.getSize(), myImageSize);
    myImageToMockupDestination.setDestinationPosition(destination.x, destination.y);

    // Paint unselected guidelines
    g2d.setColor(GUIDE_COLOR);
    for (int i = 0; i < myUnselectedGuidelines.size(); i++) {
      final Guide guide = myUnselectedGuidelines.get(i);
      paintGuideline(g2d, guide);
    }

    // Paint selected guidelines
    g2d.setColor(GUIDE_SELECTED_COLOR);
    final Stroke stroke = g2d.getStroke();
    g2d.setStroke(new BasicStroke(2));
    for (int i = 0; i < mySelectedGuidelines.size(); i++) {
      final Guide guide = mySelectedGuidelines.get(i);
      paintGuideline(g2d, guide);
    }

    // Paint the hovered guideline
    if (myHoveredGuideline != null) {
      g2d.setColor(GUIDE_HOVERED_COLOR);
      paintGuideline(g2d, myHoveredGuideline);
    }
    g2d.setStroke(stroke);
  }

  /**
   * Paint the guide guideline using myImageToMockupDestination {@link CoordinateConverter}
   * @param g2d
   * @param guide
   */
  private void paintGuideline(Graphics2D g2d, Guide guide) {
    int x1;
    int x2;
    int y1;
    int y2;
    switch (guide.getOrientation()) {
      case VERTICAL:
        x1 = x2 = myImageToMockupDestination.x(Math.round(guide.getPosition()));
        y1 = 0;
        y2 = getHeight();
        break;
      case HORIZONTAL:
      default:
        x1 = 0;
        x2 = getWidth();
        y1 = y2 = myImageToMockupDestination.y(Math.round(guide.getPosition()));
    }
    g2d.drawLine(x1, y1, x2, y2);
  }

  /**
   * Paint the cropped area of the mockup specified by {@link Mockup#getCropping()} inside destination rectangle.
   * The mockup will be stretched if needed.
   * @param g The graphic context
   * @param destination The destination rectangle where the mockup will be paint.
   */
  private void paintMockup(Graphics2D g, Rectangle destination) {
    if(myImage == null ) {
      return;
    }
    // Source coordinates
    int sx = myMockup.getCropping().x;
    int sy = myMockup.getCropping().y;
    int sw = myMockup.getCropping().width;
    int sh = myMockup.getCropping().height;

    sw = sw <= 0 ? myImage.getWidth() : sw;
    sh = sh <= 0 ? myImage.getHeight() : sh;
    final Composite composite = g.getComposite();

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myMockup.getAlpha()));
    g.drawImage(myImage,
                destination.x,
                destination.y,
                destination.x + destination.width,
                destination.y + destination.height,
                sx, sy, sx + sw, sy + sh,
                null);
    g.setComposite(composite);
  }

  /**
   * Update myMockupDrawDestination to match the latest coordinates of the mockup and the screen view
   */
  private void updateMockupDrawDestination() {
    // Coordinates of the component in the ScreenView system
    final int componentSwingX = Coordinates.getSwingX(myScreenView, myMockup.getComponent().x);
    final int componentSwingY = Coordinates.getSwingY(myScreenView, myMockup.getComponent().y);
    final int componentSwingW = Coordinates.getSwingDimension(myScreenView, myMockup.getComponent().w);
    final int componentSwingH = Coordinates.getSwingDimension(myScreenView, myMockup.getComponent().h);

    myMockupDrawDestination.setBounds(
      componentSwingX,
      componentSwingY,
      componentSwingW,
      componentSwingH);
  }

  private void paintScreenView(@NotNull Graphics2D g) {
    g.setColor(NlConstants.BLUEPRINT_BG_COLOR);
    g.fillRect(
      myScreenViewToPanel.x(0),
      myScreenViewToPanel.y(0),
      myScreenViewToPanel.dX(myScreenViewSize.width),
      myScreenViewToPanel.dY(myScreenViewSize.height)
    );
  }

  /**
   * Recursively draw all child components
   *
   * @param g         the {@link Graphics2D} to draw on
   * @param component a node in the component tree.
   */
  private void paintAllComponents(Graphics2D g, NlComponent component) {
    g.setColor(COMPONENT_FG_COLOR);
    paintComponent(g, component);
    for (int i = 0; i < component.getChildCount(); i++) {
      paintAllComponents(g, component.getChild(i));
    }
  }

  /**
   * Draw one component.
   *
   * @param gc        the {@link Graphics2D} to draw on
   * @param component a component in the {@link ScreenView}
   */
  private void paintComponent(Graphics2D gc, NlComponent component) {
    final int x = myScreenViewToPanel.x(Coordinates.getSwingX(myScreenView, component.x));
    final int y = myScreenViewToPanel.y(Coordinates.getSwingY(myScreenView, component.y));
    final int w = myScreenViewToPanel.dX(Coordinates.getSwingDimension(myScreenView, component.w));
    final int h = myScreenViewToPanel.dY(Coordinates.getSwingDimension(myScreenView, component.h));
    gc.drawRect(x, y, w, h);
  }

  /**
   * If true how the guidelines and and attach mouse listeners to interact with the guideline.
   * If false, remove the previously set mouse listeners.
   * @param showGuideline
   */
  public void setShowGuideline(boolean showGuideline) {
    myShowGuideline = showGuideline;
    if (myShowGuideline) {
      myMouseInteraction = new GuidelinesMouseInteraction();
      addMouseMotionListener(myMouseInteraction);
      addMouseListener(myMouseInteraction);
    }
    else {
      removeMouseListener(myMouseInteraction);
      removeMouseMotionListener(myMouseInteraction);
    }
    repaint();
  }

  /**
   * Set myImage and update the myImageSize
   * @param image
   */
  private void setImage(BufferedImage image) {
    myImage = image;
    if(image == null) {
      return;
    }
    myImageSize.setSize(myImage.getWidth(), myImage.getHeight());
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
    setImage(myMockup.getImage());
    repaint();
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  /**
   * Create a list of {@link MockupGuide} from the selected guidelines with the correct Android Dp coordinates.
   * The list is recreated each time the method is called.
   * @return the newly created list.
   */
  @NotNull
  public List<MockupGuide> getSelectedGuidelines() {

    CoordinateConverter mockupToScreenView = new CoordinateConverter();
    final Rectangle mockupPosition = myMockupDrawDestination;
    mockupToScreenView.setDimensions(myScreenView.getSize(), mockupPosition.getSize());
    mockupToScreenView.setSourcePosition(mockupPosition.x, mockupPosition.y);

    final List<MockupGuide> mockupGuides = new ArrayList<>(mySelectedGuidelines.size());
    for (int i = 0; i < mySelectedGuidelines.size(); i++) {
      final Guide guide = mySelectedGuidelines.get(i);
      if (guide.getOrientation().equals(Guide.Orientation.HORIZONTAL)) {
        final int mockupCoordinate = myImageToMockupDestination.y(guide.getPosition());
        final int screenViewPosition = mockupToScreenView.y(mockupCoordinate);
        final int androidDpPosition = Coordinates.getAndroidYDip(myScreenView, screenViewPosition);
        mockupGuides.add(new MockupGuide(androidDpPosition, MockupGuide.Orientation.HORIZONTAL));
      }
      else {
        final int mockupCoordinate = myImageToMockupDestination.x(guide.getPosition());
        final int screenViewPosition = mockupToScreenView.x(mockupCoordinate);
        final int androidDpPosition = Coordinates.getAndroidXDip(myScreenView, screenViewPosition);
        mockupGuides.add(new MockupGuide(androidDpPosition, MockupGuide.Orientation.VERTICAL));
      }
    }
    return mockupGuides;
  }

  /**
   * Handle the mouse interaction with the guidelines
   */
  private class GuidelinesMouseInteraction extends MouseAdapter {

    public static final int CLICKABLE_AREA = 5;
    private boolean myMouseMoved;

    /**
     * We save a reference to the last clicked guideline in order to
     * show the change of state (selected/unselected) of the guideline if the user clicks
     * multiple times on the guideline without firing a MouseMoved event
     */
    private Guide myLastClickedGuide;

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      final int x = e.getX();
      final int y = e.getY();
      myMouseMoved = true;

      Guide guide;
      // Check all the unselected guidelines if they are hovered
      for (int i = 0; i < myUnselectedGuidelines.size(); i++) {
        guide = myUnselectedGuidelines.get(i);
        if (isHovering(x, y, guide)) {
          myHoveredGuideline = guide;
          return;
        }
      }
      // Check all the selected guidelines if they are hovered
      for (int i = 0; i < mySelectedGuidelines.size(); i++) {
        guide = mySelectedGuidelines.get(i);
        if (isHovering(x, y, guide)) {
          myHoveredGuideline = guide;
          return;
        }
      }
      myHoveredGuideline = null;
      repaint();
    }

    /**
     * Check if x,y is hover the the guide
     * @param x x position of the mouse
     * @param y y position of the mouse
     * @param guide the guide to check the intersection with
     * @return true if the mouse is within CLICKABLE_AREA pixels of the guide
     */
    private boolean isHovering(int x, int y, Guide guide) {
      switch (guide.getOrientation()) {
        case HORIZONTAL:
          if (Math.abs(y - myImageToMockupDestination.y(guide.getPosition())) < CLICKABLE_AREA) {
            myHoveredGuideline = guide;
            repaint();
            return true;
          }
          break;
        case VERTICAL:
          if (Math.abs(x - myImageToMockupDestination.x(guide.getPosition())) < 5) {
            myHoveredGuideline = guide;
            repaint();
            return true;
          }
          break;
      }
      return false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!myMouseMoved) {
        // If a click occurs but the mouse has not been moved since the last click event
        // myHoveredGuideline will be null and nothing will happen, so we set myHoveredGuideline to the
        // last clicked guide in order to re-select/unselect it
        myHoveredGuideline = myLastClickedGuide;
      }
      if (myHoveredGuideline != null) {
        // If move the guide from the unselected to selected guides lists or the
        // other way around if already selected
        if (myUnselectedGuidelines.contains(myHoveredGuideline)) {
          mySelectedGuidelines.add(myHoveredGuideline);
          myUnselectedGuidelines.remove(myHoveredGuideline);
        }
        else {
          mySelectedGuidelines.remove(myHoveredGuideline);
          myUnselectedGuidelines.add(myHoveredGuideline);
        }

        // Set the hovered guide to null so we can show the change of state of the drawable
        myLastClickedGuide = myHoveredGuideline;
        myHoveredGuideline = null;
        myMouseMoved = false;
        repaint();
      }
    }
  }
}
