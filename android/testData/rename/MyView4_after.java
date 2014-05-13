package p1.p2;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.Button;

@SuppressWarnings("UnusedDeclaration")
public class MyView extends Button {
    public MyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        int attribute = R.attr.answer;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NewName);
        int answer = a.getInt(R.styleable.NewName_answer, 0);
        a.recycle();
    }
}
