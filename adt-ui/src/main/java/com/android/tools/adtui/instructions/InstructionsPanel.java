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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A custom panel that renders a list of {@link RenderInstruction} and optionally fades out after a certain time period.
 */
public class InstructionsPanel extends JPanel {
  /**
   * The overlay instruction background is slightly transparent to not block the data being rendered below.
   */
  private static final Color INSTRUCTIONS_BACKGROUND = new JBColor(new Color(0xD8464646, true), new Color(0xD8E6E6E6, true));
  private static final Color INSTRUCTIONS_FOREGROUND = new JBColor(new Color(0xFFFFFF), new Color(0x000000));

  @Nullable private final EaseOutModel myEaseOutModel;
  @Nullable private AspectObserver myObserver;
  @Nullable private Consumer<InstructionsPanel> myEaseOutCompletionCallback;

  private InstructionsPanel(@NotNull Builder builder) {
    super(new TabularLayout("*,Fit,*", "*,Fit,*"));

    setOpaque(false);
    setBackground(INSTRUCTIONS_BACKGROUND);
    setForeground(INSTRUCTIONS_FOREGROUND);
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

  private static class InstructionsComponent extends AnimatedComponent {
    private final int myHorizontalPadding;
    private final int myVerticalPadding;
    private final int myArcWidth;
    private final int myArcHeight;
    private float myAlpha;
    @Nullable private EaseOutModel myEaseOutModel;
    @NotNull private final InstructionsRenderer myRenderer;

    public InstructionsComponent(@NotNull Builder builder) {
      myEaseOutModel = builder.myEaseOutModel;
      myHorizontalPadding = builder.myHorizontalPadding;
      myVerticalPadding = builder.myVerticalPadding;
      myArcWidth = builder.myArcWidth;
      myArcHeight = builder.myArcHeight;
      myRenderer = new InstructionsRenderer(builder.myInstructions, builder.myAlignment);
      myAlpha = 1f;

      if (myEaseOutModel != null) {
        myEaseOutModel.addDependency(myAspectObserver).onChange(EaseOutModel.Aspect.EASING, this::modelChanged);
      }
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
      Color background = getBackground();
      if (background != null) {
        g2d.setColor(getBackground());
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
    public static final int DEFAULT_PADDING_PX = JBUI.scale(12);

    private int myArcWidth;
    private int myArcHeight;
    private int myHorizontalPadding = DEFAULT_PADDING_PX;
    private int myVerticalPadding = DEFAULT_PADDING_PX;
    private InstructionsRenderer.HorizontalAlignment myAlignment = InstructionsRenderer.HorizontalAlignment.CENTER;
    @Nullable private EaseOutModel myEaseOutModel;
    @Nullable private Consumer<InstructionsPanel> myEaseOutCompletionCallback;

    @NotNull private final List<RenderInstruction> myInstructions;

    public Builder(@NotNull RenderInstruction... instructions) {
      myInstructions = Arrays.asList(instructions);
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
     * @return
     */
    @NotNull
    public Builder setEaseOut(@NotNull EaseOutModel easeOutModel, @Nullable Consumer<InstructionsPanel> easeOutCompletionCallback) {
      myEaseOutModel = easeOutModel;
      myEaseOutCompletionCallback = easeOutCompletionCallback;
      return this;
    }

    @NotNull
    public InstructionsPanel build() {
      return new InstructionsPanel(this);
    }
  }
}
