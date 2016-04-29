import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.ListView;

public class Class extends ListView {

    public Class(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void doSomething() {
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
		public boolean onPreDraw() {
                setSelectionFromTop(0, 0);
                return true;
            }
	    });
    }
}