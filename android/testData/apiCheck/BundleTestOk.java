<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">import android.app.Fragment;</error>
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