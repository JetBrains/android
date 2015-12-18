<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">import android.content.Context;</error>
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