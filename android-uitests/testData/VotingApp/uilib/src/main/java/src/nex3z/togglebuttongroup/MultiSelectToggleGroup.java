package src.nex3z.togglebuttongroup;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import src.nex3z.togglebuttongroup.button.ToggleButton;

import java.util.ArrayList;

public class MultiSelectToggleGroup extends ToggleButtonGroup {
    private static final String LOG_TAG = MultiSelectToggleGroup.class.getSimpleName();

    private OnCheckedStateChangeListener mOnCheckedStateChangeListener;

    public MultiSelectToggleGroup(Context context) {
        super(context);
    }

    public MultiSelectToggleGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mInitialCheckedId != NO_ID) {
            setCheckedStateForView(mInitialCheckedId, true);
        }
    }

    @Override
    protected <T extends View & Checkable> void onChildCheckedChange(T child, boolean isChecked) {
        notifyCheckedStateChange(child.getId(), isChecked);
    }

    public void check(int id) {
        setCheckedStateForView(id, true);
    }

    public void check(int id, boolean checked) {
        setCheckedStateForView(id, checked);
    }

    public void clearCheck(int checkedId) {
        //for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(checkedId);
            if (child instanceof ToggleButton) {
                ((ToggleButton) child).setChecked(false);
            }
        //}
    }

    public ArrayList<Integer> getCheckedIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ToggleButton && ((ToggleButton) child).isChecked()) {
                ids.add(child.getId());
            }
        }
        return ids;
    }

    public void toggle(int id) {
        toggleCheckedStateForView(id);
    }

    public void setOnCheckedChangeListener(OnCheckedStateChangeListener listener) {
        mOnCheckedStateChangeListener = listener;
    }

    private void notifyCheckedStateChange(int id, boolean isChecked) {
        if (mOnCheckedStateChangeListener != null) {
            mOnCheckedStateChangeListener.onCheckedStateChanged(this, id, isChecked);
        }
    }

    public interface OnCheckedStateChangeListener {
        void onCheckedStateChanged(MultiSelectToggleGroup group, int checkedId, boolean isChecked);
    }
}
