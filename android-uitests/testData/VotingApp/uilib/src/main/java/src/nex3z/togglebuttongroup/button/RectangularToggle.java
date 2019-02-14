package src.nex3z.togglebuttongroup.button;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import src.nex3z.togglebuttongroup.R;

public class RectangularToggle extends MarkerButton {
    private static final String LOG_TAG = RectangularToggle.class.getSimpleName();

    private static final int DEFAULT_ANIMATION_DURATION = 150;

    private long mAnimationDuration = DEFAULT_ANIMATION_DURATION;
    private Animation mCheckAnimation;
    private Animation mUncheckAnimation;
    private ValueAnimator mTextColorAnimator;
    private GradientDrawable mRadioButtonUuchecked, mRedGradient, mGreenGradient;
    public RectangularToggle(Context context) {
        this(context, null);
    }

    public RectangularToggle(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        initBackground();
        initAnimation();
    }

    private void initBackground() {
        GradientDrawable checked = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), R.drawable.bg_rectangle);
        checked.setColor(mMarkerColor);

        mRadioButtonUuchecked = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), R.drawable.bg_rectangle_unchecked);
        mRadioButtonUuchecked.setColor(Color.parseColor("#fafafa"));

        mTvText.setBackgroundDrawable(mRadioButtonUuchecked);
    }

    private void initAnimation() {
        final int defaultTextColor = getDefaultTextColor();
        final int checkedTextColor = getCheckedTextColor();

        mTextColorAnimator = ValueAnimator.ofObject(
                new ArgbEvaluator(), defaultTextColor, checkedTextColor);
        mTextColorAnimator.setDuration(mAnimationDuration);
        mTextColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mTvText.setTextColor((Integer)valueAnimator.getAnimatedValue());
            }
        });

        mCheckAnimation = new ScaleAnimation(0, 1, 0, 1,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mCheckAnimation.setDuration(mAnimationDuration);
        mCheckAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                mTvText.setTextColor(checkedTextColor);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        mUncheckAnimation = new ScaleAnimation(1, 0, 1, 0,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mUncheckAnimation.setDuration(mAnimationDuration);
        mUncheckAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                mIvBg.setVisibility(INVISIBLE);
                mTvText.setTextColor(defaultTextColor);}

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }


    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (checked) {
            mIvBg.setVisibility(VISIBLE);
            mTvText.setBackgroundDrawable(null);
            mIvBg.startAnimation(mCheckAnimation);
            mTextColorAnimator.start();
        } else {
            //mIvBg.setVisibility(INVISIBLE);
            mTvText.setBackgroundDrawable(mRadioButtonUuchecked);
            mIvBg.startAnimation(mUncheckAnimation);
            mTextColorAnimator.reverse();
        }
    }

}
