package p1.p2;

import android.content.Context;
import android.widget.TextView;

public class CustomView extends <error descr="This custom view should extend `android.support.v7.widget.AppCompatTextView` instead">Text<caret>View</error> {
    public CustomView(Context context) {
        super(context);
    }
}
