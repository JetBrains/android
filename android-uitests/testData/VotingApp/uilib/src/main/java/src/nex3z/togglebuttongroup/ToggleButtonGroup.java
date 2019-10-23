package src.nex3z.togglebuttongroup;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;

import src.nex3z.togglebuttongroup.button.OnCheckedChangeListener;
import src.nex3z.togglebuttongroup.button.ToggleButton;

public abstract class ToggleButtonGroup extends FlowLayout {
    private static final String LOG_TAG = ToggleButtonGroup.class.getSimpleName();

    protected int mInitialCheckedId = View.NO_ID;
    private OnCheckedChangeListener mCheckedStateTracker;
    private CompoundButton.OnCheckedChangeListener mCompoundButtonStateTracker;
    private PassThroughHierarchyChangeListener mPassThroughListener;

    protected abstract <T extends View & Checkable>
    void onChildCheckedChange(T child, boolean isChecked);

    public ToggleButtonGroup(Context context) {
        this(context, null);
    }

    public ToggleButtonGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.ToggleButtonGroup, 0, 0);
        try {
            mInitialCheckedId = a.getResourceId(R.styleable.ToggleButtonGroup_tbgCheckedButton,
                    View.NO_ID);
        } finally {
            a.recycle();
        }

        init();
    }

    private void init() {
        mPassThroughListener = new PassThroughHierarchyChangeListener();
        super.setOnHierarchyChangeListener(mPassThroughListener);
    }

    @Override
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        mPassThroughListener.mOnHierarchyChangeListener = listener;
    }

    protected void setCheckedStateForView(int viewId, boolean checked) {
        View target = findViewById(viewId);
        if (target != null && target instanceof Checkable) {
            ((Checkable) target).setChecked(checked);
        }
    }

    protected void toggleCheckedStateForView(int viewId) {
        View target = findViewById(viewId);
        if (target != null && target instanceof Checkable) {
            ((Checkable) target).toggle();
        }
    }

    private class CheckedStateTracker implements OnCheckedChangeListener {
        @Override
        public <T extends View & Checkable> void onCheckedChanged(T view, boolean isChecked) {
            onChildCheckedChange(view, isChecked);
        }
    }

    private class CompoundButtonCheckedStateTracker implements
            CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            onChildCheckedChange(buttonView, isChecked);
        }
    }


    private class PassThroughHierarchyChangeListener implements
            ViewGroup.OnHierarchyChangeListener {
        private ViewGroup.OnHierarchyChangeListener mOnHierarchyChangeListener;

        public void onChildViewAdded(View parent, View child) {
            if (parent == ToggleButtonGroup.this && child instanceof Checkable) {
                if (child.getId() == View.NO_ID) {
                    child.setId(generateIdForView(child));
                }
                if (child instanceof ToggleButton) {
                    setStateTracker((ToggleButton) child);
                } else if (child instanceof CompoundButton) {
                    setStateTracker((CompoundButton) child);
                }
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener.onChildViewAdded(parent, child);
            }
        }

        public void onChildViewRemoved(View parent, View child) {
            if (parent == ToggleButtonGroup.this && child instanceof Checkable) {
                if (child instanceof ToggleButton) {
                    clearStateTracker((ToggleButton) child);
                } else if (child instanceof CompoundButton) {
                    clearStateTracker((CompoundButton) child);
                }
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener.onChildViewRemoved(parent, child);
            }
        }
    }

    private void setStateTracker(ToggleButton view) {
        if (mCheckedStateTracker == null) {
            mCheckedStateTracker = new CheckedStateTracker();
        }
        view.setOnCheckedChangeListener(mCheckedStateTracker);
    }

    private void clearStateTracker(ToggleButton view) {
        view.setOnCheckedChangeListener(null);
    }

    private void setStateTracker(CompoundButton view) {
        if (mCompoundButtonStateTracker == null) {
            mCompoundButtonStateTracker = new CompoundButtonCheckedStateTracker();
        }
        view.setOnCheckedChangeListener(mCompoundButtonStateTracker);
    }

    private void clearStateTracker(CompoundButton view) {
        view.setOnCheckedChangeListener(null);
    }

    protected int generateIdForView(View view) {
        return android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1
                ? view.hashCode()
                : generateViewId();
    }

}
