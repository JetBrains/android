package src.adux.votingapplib.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerFeedback;
import src.adux.votingapplib.models.Question;
import com.google.gson.Gson;

import java.io.IOException;

import httplib.HttpPostAnswer;
import src.adux.votingapplib.models.Session;
import src.nex3z.togglebuttongroup.PollManager;

public class FragmentNumericFeedback extends Fragment {
    private static final String LOG_TAG = FragmentWrittenFeedback.class.getSimpleName();
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";
    private FragmentActivity mContext;
    private Button mButtonContinue;
    private TextView mTextViewTitle, mTextViewSubtitle;
    private EditText mEditTextAnswer;

    private AnswerFeedback mAnswerFeedback;
    private String mQid;
    private String mUid;
    private String mType = "";
    private Session mSession;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                src.adux.votingapplib.R.layout.fragment_feedback, container, false);

        mButtonContinue = (Button) rootView.findViewById(src.adux.votingapplib.R.id.button_continue);
        mTextViewTitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q_title);
        mTextViewSubtitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q);
        mEditTextAnswer = (EditText) rootView.findViewById(src.adux.votingapplib.R.id.editText_answer);
        mEditTextAnswer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AnswerManager.getInstance().reset();
        PollManager.getInstance().setFragment(false);
        PollManager.getInstance().setToggleState(true);

        mQid = getArguments().getString("hash");
        mType = getArguments().getString("type");

//        mUid = Settings.Secure.getString(getContext().getContentResolver(),
//                Settings.Secure.ANDROID_ID);
        mSession = new Session(this.getContext());
        mUid = mSession.getUserID();

        mAnswerFeedback = new AnswerFeedback();
        mAnswerFeedback.setQid(mQid);
        mAnswerFeedback.setUid(mUid);

        mButtonContinue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                //if(mAnswerFeedback.getResponse() !=null )
                mAnswerFeedback.setResponse(mEditTextAnswer.getText().toString().trim());
                hideKeyboard(mContext);
                System.out.println("Anshwer: "+ new Gson().toJson(mAnswerFeedback,AnswerFeedback.class));
                AnswerManager.getInstance().put_answer2(new Gson().toJson(mAnswerFeedback,AnswerFeedback.class));
                if(mType == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentNumericFeedback.this.getActivity());
                    builder.setTitle(
                            "Submit response?")
                            .setMessage("Do you wish to submit your response?")
                            .setCancelable(false)
                            .setPositiveButton("Ok",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            ((SurveyActivity) mContext).event_survey_completed(AnswerManager.getInstance());
                                        }
                                    })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            dialog.cancel();
                                        }
                                    }).create().show();
                }
                else {
                    postAnswer();
                    ((SurveysActivity) mContext).go_to_next();
                }
            }
        });
        return rootView;
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        Question q_data = (Question) getArguments().getSerializable("data");
        mAnswerFeedback.setQtype(q_data.getQuestionType());


//        if (q_data.getRequired()) {
//            mButtonContinue.setVisibility(View.GONE);
//        }

        mEditTextAnswer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
//                if (s.length() > 0) {
//                    mButtonContinue.setVisibility(View.VISIBLE);
//                } else {
//                    mButtonContinue.setVisibility(View.GONE);
//                }
            }
        });

        mTextViewTitle.setText("Enter your response below");
        //mTextViewSubtitle.setText("Feedback-numeric");
        mEditTextAnswer.setTextSize(36);
        //mEditTextAnswer.requestFocus();
//        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Service.INPUT_METHOD_SERVICE);
//        imm.showSoftInput(mEditTextAnswer, InputMethodManager.SHOW_IMPLICIT);

    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void postAnswer(){
        final String answers_json = AnswerManager.getInstance().get_answer();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpPostAnswer httpPostClient = new HttpPostAnswer();
                try {
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    Log.d(LOG_TAG, "Posting data : " + answers_json);
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    httpPostClient.post(SERVER_URL + "/answer", answers_json);
                } catch (IOException e) {
                    e.printStackTrace();
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentNumericFeedback.this.getActivity());
                    builder.setTitle("Error")
                            .setMessage("Some problem")
                            .setPositiveButton(android.R.string.ok, listener)
                            .show();
                }
            }
        });
        thread.start();
    }
}