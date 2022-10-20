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
package com.android.tools.adtui.instructions;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.util.SwingUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A custom panel that renders a list of {@link RenderInstruction} and optionally fades out after a certain time period.
 */
public class InstructionsPanel extends JPanel {
  public enum Mode {
    /**
     * If floating, the instructions will appear in a small, floating panel that's sized to fit
     * tightly around them. The rest of the panel will be transparent.
     *
     * This option is good for showing instructions that temporarily block but still allow the user
     * to see some underlying UI.
     */
    FLOATING,

    /**
     * If filled, the whole panel will be opaque, with instructions centered in the middle of it.
     */
    FILL_PANEL,
  }

  @Nullable private final EaseOutModel myEaseOutModel;
  @Nullable private AspectObserver myObserver;
  @Nullable private Consumer<InstructionsPanel> myEaseOutCompletionCallback;

  private InstructionsPanel(@NotNull Builder builder) {
    // Aim for layout as described in https://jetbrains.github.io/ui/principles/empty_state/
    super(new TabularLayout("*,Fit-,*", "45*,Fit-,55*"));

    if (builder.myMode == Mode.FLOATING) {
      setOpaque(false);
    }
    setBackground(builder.myBackgroundColor);
    setForeground(builder.myForegroundColor);
    InstructionsComponent component = new InstructionsComponent(builder);
    add(component, new TabularLayout.Constraint(1, 1));

    myEaseOutModel = builder.myEaseOutModel;
    myEaseOutCompletionCallback = builder.myEaseOutCompletionCallback;
    if (myEaseOutModel != null) {
      myObserver = new AspectObserver();
      myEaseOutModel.addDependency(myObserver).onChange(EaseOutModel.Aspect.EASING, this::modelChanged);
    }
  }

  private void modelChanged() {
    if (myEaseOutCompletionCallback == null) {
      return;
    }

    assert myEaseOutModel != null;
    if (myEaseOutModel.getPercentageComplete() >= 1) {
      myEaseOutCompletionCallback.accept(this);
      myEaseOutCompletionCallback = null;
    }
  }

  @VisibleForTesting
  @NotNull
  public List<RenderInstruction> getRenderInstructionsForComponent(int component) {
    assert component >= 0 && component < getComponentCount();
    InstructionsComponent instructionsComponent = (InstructionsComponent)getComponent(component);
    return instructionsComponent.getRenderInstructions();
  }

  @VisibleForTesting
  @NotNull
  public InstructionsRenderer getRenderer() {
    InstructionsRenderer renderer = null;
    for (int i = 0; i < getComponentCount(); i++) {
      Component c = getComponent(i);
      if (c instanceof InstructionsComponent) {
        renderer = ((InstructionsComponent)c).myRenderer;
        break;
      }
    }
    // Assert is OK because this is a test-only method. In production, this would only fail if
    // someone externally removed our children.
    assert (renderer != null);
    return renderer;
  }

  private static class InstructionsComponent extends AnimatedComponent {
    private final int myHorizontalPadding;
    private final int myVerticalPadding;
    private final int myArcWidth;
    private final int myArcHeight;
    private float myAlpha;
    @Nullable private EaseOutModel myEaseOutModel;
    @NotNull private final InstructionsRenderer myRenderer;


    /**
     * Allows for custom setting of cursor on the appropriate container.
     * <p>
     * Having a custom cursor setter is useful for situations where the component that causes
     * desirable cursor change is NOT the one in front (z-order). For example, desiring the
     * cursor to change to a pointed hand when hovering over a hyperlink label when there is
     * a transparent component painted in front of it. Swing would not naturally know to
     * change the cursor. That's where this cursor setter come in handy. You can use it to
     * manually set the cursor of the transparent component (or the parent component) when
     * you detect mouse move event over the hyperlink label.
     */
    @Nullable private final BiFunction<Container, Cursor, Container> myCursorSetter;

    /**
     * Caches the result of calling myCursorSetter so that it doesn't need to be computed every
     * time the cursor is set.
     */
    @Nullable private Container myCachedCursorContainer = null;

    /**
     * This will be set to the most recent instruction found under the mouse cursor, or null if
     * the mouse cursor isn't over any of them.
     *
     * @see {@link #delegateMouseEvent(MouseEvent)}
     */
    @Nullable private RenderInstruction myFocusInstruction;

    public InstructionsComponent(@NotNull Builder builder) {
      setBackground(builder.myMode == Mode.FLOATING ? builder.myBackgroundColor : null);

      myEaseOutModel = builder.myEaseOutModel;
      myHorizontalPadding = builder.myHorizontalPadding;
      myVerticalPadding = builder.myVerticalPadding;
      myArcWidth = builder.myArcWidth;
      myArcHeight = builder.myArcHeight;
      myRenderer = new InstructionsRenderer(builder.myInstructions, builder.myAlignment);
      myAlpha = 1f;
      myCursorSetter = builder.myCursorSetter;

      if (myEaseOutModel != null) {
        myEaseOutModel.addDependency(myAspectObserver).onChange(EaseOutModel.Aspect.EASING, this::modelChanged);
      }

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          RenderInstruction instruction = delegateMouseEvent(e);
          setCursor((instruction != null) ? instruction.getCursorIcon() : null);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          delegateMouseEvent(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
          delegateMouseEvent(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          delegateMouseEvent(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          // When the cursor moves off this component, we're 100% sure it won't be over any
          // instruction, so no use calling delegateMouseEvent. Instead, just direct the event
          // to the last instruction we were over.
          if (myFocusInstruction != null) {
            handleMouseEvent(myFocusInstruction, e);
            myFocusInstruction = null;
            setCursor(null); // Reset cursor in case it was set by `mouseMoved` above
          }
        }
      });
    }

    @Override
    public void setCursor(Cursor cursor) {
      if (myCachedCursorContainer != null) {
        myCachedCursorContainer.setCursor(cursor);
      }
      else if (myCursorSetter != null) {
        myCachedCursorContainer = myCursorSetter.apply(this, cursor);
      }
      else {
        super.setCursor(cursor);
      }
    }

    @VisibleForTesting
    @NotNull
    public List<RenderInstruction> getRenderInstructions() {
      return myRenderer.getInstructions();
    }

    /**
     * When a mouse event occurs on this {@link InstructionsComponent} instance, redirects to the correct {@link RenderInstruction}
     * to handle the event.
     *
     * @return the instruction which handled the event, or null if there was no instruction associated with the mouse event.
     */
    @Nullable
    private RenderInstruction delegateMouseEvent(@NotNull MouseEvent event) {
      // Adjusts the event's point based on this renderer's padding
      event.translatePoint(-myHorizontalPadding, -myVerticalPadding);

      // Find the RenderInstruction whose boundaries contain the event's Point based on their rendering positions, then delegate it to the
      // instruction for handling.
      Point position = event.getPoint();
      Point cursor = new Point(myRenderer.getStartX(0), 0);
      RenderInstruction focusInstruction = null;
      for (RenderInstruction instruction : myRenderer.getInstructions()) {
        Rectangle bounds = instruction.getBounds(myRenderer, cursor);
        if (bounds.contains(position)) {
          // Transforms the point into the instruction's frame.
          event.translatePoint(-bounds.x, -bounds.y);
          focusInstruction = instruction;
          break;
        }
        instruction.moveCursor(myRenderer, cursor);
      }

      if (myFocusInstruction != focusInstruction) {
        // Normally, Swing generates EXITED events automatically when you move focus between
        // components. However, we are emulating child components, since really we're just this
        // single large component, so we have to generate this exit event manually.
        if (myFocusInstruction != null) {
          handleMouseEvent(myFocusInstruction, SwingUtil.convertMouseEventID(event, MouseEvent.MOUSE_EXITED));
        }
        myFocusInstruction = focusInstruction;
      }

      if (myFocusInstruction != null) {
        handleMouseEvent(myFocusInstruction, event);
      }

      return myFocusInstruction;
    }

    private void handleMouseEvent(@NotNull RenderInstruction instruction, @NotNull MouseEvent e) {
      // Some instructions change visually after mouse events, so repaint just in case
      instruction.handleMouseEvent(e);
      repaint();
    }

    private void modelChanged() {
      if (myAlpha <= 0) {
        // easing has completed. Should not process further.
        return;
      }

      // this method should only be called if myEaseOutModel is not null.
      assert myEaseOutModel != null;
      myAlpha = 1 - myEaseOutModel.getPercentageComplete();
      opaqueRepaint();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension renderSize = myRenderer.getRenderSize();
      return new Dimension(renderSize.width + 2 * myHorizontalPadding, renderSize.height + 2 * myVerticalPadding);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    protected void draw(Graphics2D g2d, Dimension dim) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));

      // Draw the background round rectangle for the instruction panel.
      Color background = isBackgroundSet() ? getBackground() : null;
      if (background != null) {
        g2d.setColor(background);
        Dimension size = getPreferredSize();
        g2d.fillRoundRect(0, 0, size.width, size.height, myArcWidth, myArcHeight);
      }

      // Draw the render instructions
      g2d.translate(myHorizontalPadding, myVerticalPadding);
      myRenderer.draw(this, g2d);
      g2d.translate(-myHorizontalPadding, -myVerticalPadding);
    }
  }

  public static final class Builder {
    private Mode myMode = Mode.FLOATING;

    /**
     * The overlay instruction background is slightly transparent to not block the data being rendered below.
     */
    private static final Color INSTRUCTIONS_BACKGROUND = new JBColor(new Color(0xD8464646, true), new Color(0xD8E6E6E6, true));
    private static final Color INSTRUCTIONS_FOREGROUND = new JBColor(new Color(0xFFFFFF), new Color(0x000000));
    public static final int DEFAULT_PADDING_PX = JBUI.scale(12);

    private int myArcWidth;
    private int myArcHeight;
    private int myHorizontalPadding = DEFAULT_PADDING_PX;
    private int myVerticalPadding = DEFAULT_PADDING_PX;
    @Nullable private Color myBackgroundColor = INSTRUCTIONS_BACKGROUND;
    @NotNull private Color myForegroundColor = INSTRUCTIONS_FOREGROUND;
    private InstructionsRenderer.HorizontalAlignment myAlignment = InstructionsRenderer.HorizontalAlignment.CENTER;
    @Nullable private EaseOutModel myEaseOutModel;
    @Nullable private Consumer<InstructionsPanel> myEaseOutCompletionCallback;

    @NotNull private final List<RenderInstruction> myInstructions = new ArrayList<>();

    @Nullable private BiFunction<Container, Cursor, Container> myCursorSetter;

    public Builder() {
    }

    public Builder(@NotNull RenderInstruction... instructions) {
      myInstructions.addAll(Arrays.asList(instructions));
    }

    public Builder addInstruction(@NotNull RenderInstruction instruction) {
      myInstructions.add(instruction);
      return this;
    }

    @NotNull
    public Builder setMode(Mode mode) {
      myMode = mode;
      return this;
    }

    /**
     * @param foregroundColor color to be used for instructions rendering texts.
     * @param backgroundColor color to be used for the rectangle wrapping the instructions. By default, the instructions are wrapped with a
     *                        rounded rectangle with a bg {@link #INSTRUCTIONS_BACKGROUND}. Set this to null if the wrapping rectangle is
     *                        unnecessary/undesirable, or if you want to default to the default system color.
     */
    @NotNull
    public Builder setColors(@NotNull Color foregroundColor, @Nullable Color backgroundColor) {
      myForegroundColor = foregroundColor;
      myBackgroundColor = backgroundColor;
      return this;
    }

    @NotNull
    public Builder setAlignment(@NotNull InstructionsRenderer.HorizontalAlignment alignment) {
      myAlignment = alignment;
      return this;
    }

    /**
     * If a background color is specified on the panel, these parameters are used for rendering a rectangle around the instructions.
     */
    @NotNull
    public Builder setPaddings(int horizontalPadding, int verticalPadding) {
      myHorizontalPadding = horizontalPadding;
      myVerticalPadding = verticalPadding;
      return this;
    }

    /**
     * If a background color is specified on the panel, these parameters are used for rendering a rectangle around the instructions.
     */
    @NotNull
    public Builder setBackgroundCornerRadius(int arcWidth, int arcHeight) {
      myArcWidth = arcWidth;
      myArcHeight = arcHeight;
      return this;
    }

    /**
     * @param easeOutModel              Used for fading out the instructions.
     * @param easeOutCompletionCallback If not null, the consumer instance will be called when the fade out is completed.
     *                                  For example, this allows the owner of the {@link InstructionsPanel} to remove it from the UI
     *                                  hierarchy after fade out.
     */
    @NotNull
    public Builder setEaseOut(@NotNull EaseOutModel easeOutModel, @Nullable Consumer<InstructionsPanel> easeOutCompletionCallback) {
      myEaseOutModel = easeOutModel;
      myEaseOutCompletionCallback = easeOutCompletionCallback;
      return this;
    }

    @NotNull
    public Builder setCursorSetter(@NotNull BiFunction<Container, Cursor, Container> cursorSetter) {
      myCursorSetter = cursorSetter;
      return this;
    }

    @NotNull
    public InstructionsPanel build() {
      return new InstructionsPanel(this);
    }
  }
}
