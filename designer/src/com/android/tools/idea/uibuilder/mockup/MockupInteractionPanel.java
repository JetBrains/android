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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.pixelprobe.Guide;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel Displaying the mockup inside the ScreenView.
 *
 * It handle the positioning of the mockup inside the selected component and the export of the guidelines
 */
public class MockupInteractionPanel extends JBPanel implements ModelListener, Mockup.MockupModelListener {

  private static final Color COMPONENT_FG_COLOR = JBColor.WHITE;
  private static final Color BACKGROUND_COLOR = JBColor.background();
  private static final Color GUIDE_COLOR = JBColor.GRAY;
  private static final Color GUIDE_SELECTED_COLOR = JBColor.RED;
  private static final Color GUIDE_HOVERED_COLOR = JBColor.RED.darker();
  private static final Color SELECTION_COLOR = JBColor.BLUE;
  private static final float ADJUST_SCALE = 0.95f;

  /**
   * Mockup being edited
   */
  private final Mockup myMockup;

  /**
   * ScreenView containing the mockup's component
   */
  private final ScreenView myScreenView;

  /**
   * Converter from ScreenView Coordinates to this panel coordinate
   */
  private final CoordinateConverter myScreenViewToPanel;

  /**
   * Converter from Image Coordinates to the bounds of the mockup
   */
  private final CoordinateConverter myImageToMockupBounds;

  /**
   * Converter from Image Coordinates to this panel coordinate
   */
  private final CoordinateConverter myImageToPanel;

  private final Rectangle myMockupDrawDestination;

  /**
   * Store the image size
   */
  private final Dimension myImageSize;

  /**
   * Image instance of the mockup
   */
  @Nullable private BufferedImage myImage;

  /**
   * Greyed Image instance of the mockup to show the unselected area
   * during the cropping edition
   */
  @Nullable private Image myGrayedImage;

  /**
   * Bounds of the ScreenView in this panel
   */
  private Rectangle myScreenViewConvertedBounds;

  /**
   * Original ScreenView Size
   */
  private final Dimension myScreenViewSize;

  /**
   * Guide being currently hovered
   */
  @Nullable private Guide myHoveredGuideline;

  /**
   * Flag set if the user clicked on the show guideline checkbox
   */
  private boolean myShowGuideline;

  /**
   * List of unselected guidelines from the mockup
   */
  @NotNull final private List<Guide> myUnselectedGuidelines;

  /**
   * List of selected guidelines from the mockup
   */
  @NotNull final private List<Guide> mySelectedGuidelines;

  /**
   * Flag set if the user clicked on the edit guideline button
   */
  private boolean myEditCropping;

  /**
   * Current selection of the cropped area
   */
  private Rectangle myCroppedSelection;

  private GuidelinesInteraction myGuidelinesInteraction;
  private CroppingInteraction myCroppingInteraction;

  public MockupInteractionPanel(@NotNull ScreenView screenView, Mockup Mockup) {
    myScreenView = screenView;
    myMockup = Mockup;
    myMockupDrawDestination = new Rectangle();
    myScreenViewSize = new Dimension();
    myScreenViewConvertedBounds = new Rectangle();
    myImageSize = new Dimension();

    myImageToMockupBounds = new CoordinateConverter();
    myImageToMockupBounds.setFixedRatio(false);

    myCroppedSelection = new Rectangle();

    myScreenViewToPanel = new CoordinateConverter();
    myScreenViewToPanel.setCenterInDestination();
    myScreenViewToPanel.setFixedRatio(true);

    myImageToPanel = new CoordinateConverter();

    myMockup.getComponent().getModel().addListener(this);
    myMockup.addMockupListener(this);

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
    final Graphics2D g2d = (Graphics2D)g.create();
    Color color = g.getColor();

    updateConvertersAndDimension();

    // Paint
    if (myEditCropping) {
      paintImage(g2d);
      paintCropSelection(g2d);
    }
    else {
      paintScreenView(g2d);
      paintMockup(g2d);
      paintAllComponents(g2d, myMockup.getComponent().getRoot());
      // Only paint guideline if the user as selected the checkbox in MockupEditor
      if (myShowGuideline) {
        paintGuidelines(g2d);
      }
    }
    g.setColor(color);
    g.dispose();
  }

  /**
   * Update all the used {@link CoordinateConverter} instances and Dimension field
   * to the latest values
   */
  private void updateConvertersAndDimension() {
    myScreenView.getSize(myScreenViewSize);
    myScreenViewToPanel.setDimensions(getSize(), myScreenViewSize, ADJUST_SCALE);
    myScreenViewToPanel.setSourcePosition(myScreenView.getX(), myScreenView.getY());
    myScreenViewConvertedBounds.setSize(myScreenViewToPanel.dX(myScreenViewSize.getWidth()),
                                        myScreenViewToPanel.dY(myScreenViewSize.getHeight()));
    myScreenViewConvertedBounds.setLocation(myScreenViewToPanel.x(0), myScreenViewToPanel.y(0));

    myImageToPanel.setDimensions(getSize(), myImageSize, ADJUST_SCALE);
    myImageToPanel.setCenterInDestination();

    final Rectangle bounds = myMockup.getScreenBounds(myScreenView);
    Rectangle mockupSwingBounds = new Rectangle(
      myScreenViewToPanel.x(bounds.x),
      myScreenViewToPanel.y(bounds.y),
      myScreenViewToPanel.dX(bounds.width),
      myScreenViewToPanel.dY(bounds.height)
    );

    myImageToMockupBounds.setDimensions(mockupSwingBounds.getSize(), myImageSize);
    myImageToMockupBounds.setDestinationPosition(mockupSwingBounds.x, mockupSwingBounds.y);
  }

  private void paintCropSelection(Graphics2D g2d) {
    g2d.setColor(SELECTION_COLOR);
    g2d.draw(myCroppedSelection);
  }

  /**
   * Paint the selected, unselected and hovered guidelines in the destination coordinate system
   *
   * @param g2d The graphic context
   */
  private void paintGuidelines(Graphics2D g2d) {
    if (myUnselectedGuidelines.isEmpty() && mySelectedGuidelines.isEmpty() && myHoveredGuideline == null) {
      return;
    }

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
   * Paint the guide guideline using myImageToMockupBounds {@link CoordinateConverter}
   *
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
        x1 = x2 = myImageToMockupBounds.x(Math.round(guide.getPosition()));
        y1 = 0;
        y2 = getHeight();
        break;
      case HORIZONTAL:
      default:
        x1 = 0;
        x2 = getWidth();
        y1 = y2 = myImageToMockupBounds.y(Math.round(guide.getPosition()));
    }
    g2d.drawLine(x1, y1, x2, y2);
  }

  /**
   * Paint the cropped area of the mockup specified by {@link Mockup#getCropping()} inside destination rectangle.
   * The mockup will be stretched if needed.
   *
   * @param g The graphic context
   */
  private void paintMockup(Graphics2D g) {
    if (myImage == null) {
      return;
    }

    Rectangle dest;
    dest = myMockup.getScreenBounds(myScreenView);
    dest.x = myScreenViewToPanel.x(dest.x);
    dest.y = myScreenViewToPanel.y(dest.y);
    dest.width = myScreenViewToPanel.dX(dest.width);
    dest.height = myScreenViewToPanel.dY(dest.height);

    // Source coordinates
    final Rectangle cropping = myMockup.getCropping();
    int sx = cropping.x;
    int sy = cropping.y;
    int sw = cropping.width;
    int sh = cropping.height;

    final Composite composite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myMockup.getAlpha()));
    g.drawImage(myImage,
                dest.x,
                dest.y,
                dest.x + dest.width,
                dest.y + dest.height,
                sx, sy, sx + sw, sy + sh,
                null);
    g.setComposite(composite);
  }

  /**
   * Paint the whole image if no selection has been made or paint a gray scale version of the image
   * if a cropping selection is made then fill the the cropping selection with a colored version
   * of the corresponding area of the image.
   *
   * @param g The graphic context
   */
  private void paintImage(Graphics2D g) {
    if (myImage == null) {
      return;
    }

    final Rectangle dest = new Rectangle();
    dest.x = myImageToPanel.x(0);
    dest.y = myImageToPanel.y(0);
    dest.width = myImageToPanel.dX(myImageSize.width);
    dest.height = myImageToPanel.dY(myImageSize.height);

    if (!myCroppedSelection.isEmpty()) {
      if (myGrayedImage == null) {
        // Create a gray scale version of the image
        myGrayedImage = GrayFilter.createDisabledImage(myImage);
      }

      // Find the cropped area of the image to pain in the selection area
      final Rectangle src = new Rectangle();
      src.x = myImageToPanel.inverseX(myCroppedSelection.x);
      src.y = myImageToPanel.inverseY(myCroppedSelection.y);
      src.width = myImageToPanel.inverseDX(myCroppedSelection.width);
      src.height = myImageToPanel.inverseDY(myCroppedSelection.height);

      g.drawImage(myGrayedImage, dest.x, dest.y, dest.width, dest.height, null);
      g.setClip(myCroppedSelection);
      g.drawImage(myImage, dest.x, dest.y, dest.width, dest.height, null);
      g.setClip(null);
    }
    else {
      // No selection has been made, we just draw the image
      g.drawImage(myImage, dest.x, dest.y, dest.width, dest.height, null);

    }
  }

  private void paintScreenView(@NotNull Graphics2D g) {
    g.setColor(NlConstants.BLUEPRINT_BG_COLOR);
    g.fillRect(
      myScreenViewToPanel.x(myScreenView.getX()),
      myScreenViewToPanel.y(myScreenView.getY()),
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
   *
   * @param showGuideline
   */
  public void setShowGuideline(boolean showGuideline) {
    myShowGuideline = showGuideline;
    if (myShowGuideline) {
      myGuidelinesInteraction = new GuidelinesInteraction();
      addMouseMotionListener(myGuidelinesInteraction);
      addMouseListener(myGuidelinesInteraction);
    }
    else {
      removeMouseListener(myGuidelinesInteraction);
      removeMouseMotionListener(myGuidelinesInteraction);
    }
    repaint();
  }

  /**
   * Set myImage and update the myImageSize
   *
   * @param image
   */
  private void setImage(@Nullable BufferedImage image) {
    if (image == myImage) {
      return;
    }
    myImage = image;
    myGrayedImage = null;
    if (image == null) {
      return;
    }
    myImageSize.setSize(myImage.getWidth(), myImage.getHeight());
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
    setImage(myMockup.getImage());
    updateCroppedSelection();
    repaint();
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  /**
   * Create a list of {@link MockupGuide} from the selected guidelines with the correct Android Dp coordinates.
   * The list is recreated each time the method is called.
   *
   * @return the newly created list.
   */
  @NotNull
  public void exportSelectedGuidelines() {

      NlComponent constraintLayout = getParentConstraintLayout();
      if (constraintLayout == null) {
        return; // TODO Display keylines directly on the DesignSurface
      }

      final List<MockupGuide> mockupGuides = new ArrayList<>(mySelectedGuidelines.size());
      for (int i = 0; i < mySelectedGuidelines.size(); i++) {
        final Guide guide = mySelectedGuidelines.get(i);
        mockupGuides.add(getMockupGuide(constraintLayout, guide));
      }

      writeGuidesToFile(constraintLayout, mockupGuides);
  }

  private void writeGuidesToFile(final NlComponent constraintLayout, final List<MockupGuide> mockupGuides) {
    final NlModel model = constraintLayout.getModel();
    final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Create Guidelines", model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {

        // Create all corresponding NlComponent Guidelines from the selected guidelines list
        for (int i = 0; i < mockupGuides.size(); i++) {
          mockupGuides.get(i).createConstraintGuideline(myScreenView, model, constraintLayout);
        }
      }

    };
    action.execute();
  }

  /**
   * Construct a MockupGuide given the given Guide.
   *
   * The MockupGuide will have the correct coordinates in DP relative to the ConstraintLayout.
   *
   * @param constraintLayout The constraintLayout that will hold the guidelines.
   *                         Used to compute the relative position of the guideline to it.
   * @param guide            The Guide extracted from the designer file.
   * @return A new MockupGuide with the same Orientation as guide and with the position in dip
   * relative to constraint layout
   */
  @NotNull
  private MockupGuide getMockupGuide(NlComponent constraintLayout, Guide guide) {
    final int containerSize;                 // Height or width of the Mockup container (or Screen if the mockup is full screen)
    final int inMockupPos;                   // Position of the guide relative the mockup in PX
    final int inConstrainLayoutPos;          // Position of the guide relative to the constrain layout in PX
    final MockupGuide exportableGuideline;   // The MockupGuidline that will be returned

    if (guide.getOrientation().equals(Guide.Orientation.HORIZONTAL)) {
      if (myMockup.isFullScreen()) {
        containerSize = (int)Math.round(myScreenView.getSize().getHeight() / myScreenView.getScale());
        inMockupPos = (int)Math.round((guide.getPosition() / myImageSize.getHeight()) * containerSize);
        inConstrainLayoutPos = inMockupPos - constraintLayout.y;
      }
      else {
        containerSize = myMockup.getComponent().h;
        inMockupPos = (int)Math.round((guide.getPosition() / myImageSize.getHeight()) * containerSize);
        inConstrainLayoutPos = inMockupPos + myMockup.getComponent().y - constraintLayout.y;
      }
      final int androidDpPosition = Coordinates.pxToDp(myScreenView, inConstrainLayoutPos);
      exportableGuideline = new MockupGuide(androidDpPosition, MockupGuide.Orientation.HORIZONTAL);
    }
    else {
      // Find the position relative to the constraintLayout even if the mockup is full screen
      if (myMockup.isFullScreen()) {
        containerSize = (int)Math.round(myScreenView.getSize().getWidth() / myScreenView.getScale());
        inMockupPos = (int)Math.round((guide.getPosition() / myImageSize.getWidth()) * containerSize);
        inConstrainLayoutPos = inMockupPos - constraintLayout.x;
      }
      else {
        containerSize = myMockup.getComponent().w;
        inMockupPos = (int)Math.round((guide.getPosition() / myImageSize.getWidth()) * containerSize);
        inConstrainLayoutPos = inMockupPos + myMockup.getComponent().x - constraintLayout.x;
      }
      final int androidDpPosition = Coordinates.pxToDp(myScreenView, inConstrainLayoutPos);
      exportableGuideline = new MockupGuide(androidDpPosition, MockupGuide.Orientation.VERTICAL);
    }
    return exportableGuideline;
  }

  /**
   * Find the closest parent of myMockup which is a constraint layout
   *
   * @return the closest parent of myMockup which is a constraint layout
   * or {@link Mockup#getComponent()} if myComponent is already a ConstraintLayout
   * Returns null if neither myMockup or none of its parent is a ConstraintLayout
   */
  @Nullable
  private NlComponent getParentConstraintLayout() {
    NlComponent currentLayout = myMockup.getComponent();
    while (currentLayout != null && !currentLayout.getTagName().equals(SdkConstants.CONSTRAINT_LAYOUT)
           && !currentLayout.isRoot()) {
      currentLayout = currentLayout.getParent();
    }
    if (currentLayout == null
        || !currentLayout.getTagName().equals(SdkConstants.CONSTRAINT_LAYOUT)) {
      return null;
    }
    return currentLayout;
  }

  /**
   * @return true if the user wants to resize the cropped area
   */
  public boolean isEditCropping() {
    return myEditCropping;
  }

  /**
   * Set EditCropping to activate the edition of the cropped area.
   *
   * @param editCropping true to activate the selection of the cropped area
   */
  public void setEditCropping(boolean editCropping) {
    if (editCropping != myEditCropping) {
      myEditCropping = editCropping;
      repaint();
      if (myEditCropping) {
        myCroppingInteraction = new CroppingInteraction();
        addMouseMotionListener(myCroppingInteraction);
        addMouseListener(myCroppingInteraction);
      }
      else {
        removeMouseListener(myCroppingInteraction);
        removeMouseMotionListener(myCroppingInteraction);
      }
    }
  }

  /**
   * Set myCroppedSelection to the values of {@link Mockup#getCropping()} converted in this panel coordinates
   */
  private void updateCroppedSelection() {
    final Rectangle cropping = myMockup.getCropping();
    myCroppedSelection.setBounds(
      myImageToPanel.x(cropping.x),
      myImageToPanel.y(cropping.y),
      myImageToPanel.dX(cropping.width),
      myImageToPanel.dY(cropping.height)
    );
  }

  @Override
  public void mockupChanged(Mockup mockup) {
    updateCroppedSelection();
    repaint();
  }

  /**
   * Handle the mouse interaction with the guidelines
   */
  private class GuidelinesInteraction extends MouseAdapter {

    private static final int CLICKABLE_AREA = 5;
    private boolean myMouseMoved;

    /**
     * We save a reference to the last clicked guideline in order to
     * show the change of state (selected/unselected) of the guideline if the user clicks
     * multiple times on the guideline without firing a MouseMoved event
     */
    private Guide myLastClickedGuide;

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
     *
     * @param x     x position of the mouse
     * @param y     y position of the mouse
     * @param guide the guide to check the intersection with
     * @return true if the mouse is within CLICKABLE_AREA pixels of the guide
     */
    private boolean isHovering(int x, int y, Guide guide) {
      switch (guide.getOrientation()) {
        case HORIZONTAL:
          if (Math.abs(y - myImageToMockupBounds.y(guide.getPosition())) < CLICKABLE_AREA) {
            myHoveredGuideline = guide;
            repaint();
            return true;
          }
          break;
        case VERTICAL:
          if (Math.abs(x - myImageToMockupBounds.x(guide.getPosition())) < 5) {
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
      if (myShowGuideline) {
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

  /**
   * Handles the mouses events for the cropping: drawing the region where the
   */
  private class CroppingInteraction extends MouseAdapter {
    private static final int MIN_SELECTION_SIZE = 2;
    private final Point myOrigin = new Point();

    /**
     * Bounds of the image in this panel coordinate system
     */
    final private Rectangle myImageConvertedBounds = new Rectangle();

    @Override
    public void mouseDragged(MouseEvent e) {
      if (myEditCropping) {
        int width = e.getX() - myOrigin.x;
        int height = e.getY() - myOrigin.y;
        int x = myOrigin.x;
        int y = myOrigin.y;

        // If the current mouse position is less than the origin,
        // we reposition the rectangle such as the current position becomes the new origin
        // and the width and height match the old origin.
        // This allow the user to do the selection in any direction
        if (e.getX() < x) {
          x = e.getX();
          width = -width;
        }
        if (e.getY() < y) {
          y = e.getY();
          height = -height;
        }

        myCroppedSelection.setBounds(x, y, width - 1, height - 1);
        // We ensure that the selection is not out of the image
        Rectangle2D.intersect(myCroppedSelection, myImageConvertedBounds, myCroppedSelection);
        repaint();
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (myEditCropping) {
        myOrigin.setLocation(e.getPoint());
        myCroppedSelection.setLocation(myOrigin);

        // Clear the old selection
        myCroppedSelection.setSize(0, 0);

        // Used to find the intersection between the selection and the image
        myImageConvertedBounds.setBounds(
          myImageToPanel.x(0),
          myImageToPanel.y(0),
          myImageToPanel.dX(myImageSize.width),
          myImageToPanel.dY(myImageSize.height)
        );
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

      // If the selection is too small, we assume that nothing is selected
      // The cropping is thus reset to the size of the image
      final Dimension size = myCroppedSelection.getSize();
      if (size.width < MIN_SELECTION_SIZE && size.height < MIN_SELECTION_SIZE) {
        myMockup.setCropping(0, 0, -1, -1);
        myCroppedSelection.setBounds(0, 0, 0, 0);
      }
      else {
        // Convert the selected area to the cropping bounds of the mockup
        myMockup.setCropping(
          myImageToPanel.inverseX(myCroppedSelection.x),
          myImageToPanel.inverseY(myCroppedSelection.y),
          myImageToPanel.inverseDX(myCroppedSelection.width),
          myImageToPanel.inverseDY(myCroppedSelection.height)
        );
      }
      MockupFileHelper.writePositionToXML(myMockup);
    }

    @Override
    public void mouseMoved(MouseEvent e) {

      // Display a cross cursor when the cursor is over the image
      if (myImageConvertedBounds.contains(e.getPoint())) {
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      }
      else {
        setCursor(Cursor.getDefaultCursor());
      }
    }
  }
}
