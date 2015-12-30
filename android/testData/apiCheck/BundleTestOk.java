import android.app.Fragment;
import android.os.Bundle;

public class Class extends Fragment {
    private String mCurrentPhotoPath = "";
    @Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentPhotoPath = savedInstanceState.getString("mCurrentPhotoPath", "");
        }
    }
}