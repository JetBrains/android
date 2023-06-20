package src.adux.votingapplib.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerSingleChoice;
import src.adux.votingapplib.models.Question;
import com.google.gson.Gson;

import src.adux.votingapplib.models.Session;
import src.nex3z.togglebuttongroup.PollManager;
import src.nex3z.togglebuttongroup.SingleSelectToggleGroup;
import src.nex3z.togglebuttongroup.button.CircularToggle;

import java.io.IOException;

import httplib.HttpPostAnswer;

public class FragmentSingleChoice extends Fragment {

    private static final String LOG_TAG = FragmentSingleChoice.class.getSimpleName();
    private Question mQuestion;
    private FragmentActivity mContext;
    private Button mButtonContinue;
    private TextView mTextviewSubtitle;
    private TextView mTextViewTitle;
    private SingleSelectToggleGroup mToggleButtonGroup;
    private String mOptionNames ="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";

    private AnswerSingleChoice mAnswerSingleChoice;
    private String mQid;
    private String mUid;
    private String mType = "";
    private Session mSession;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                src.adux.votingapplib.R.layout.fragment_single_choice, container, false);
        mToggleButtonGroup = (SingleSelectToggleGroup) rootView.findViewById(src.adux.votingapplib.R.id.group_choices);
        mButtonContinue = (Button) rootView.findViewById(src.adux.votingapplib.R.id.button_continue);
        mTextViewTitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q_title);
        mTextviewSubtitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q);
        mSession = new Session(this.getContext());

        AnswerManager.getInstance().reset();
        PollManager.getInstance().setFragment(false);
        PollManager.getInstance().setToggleState(true);

        mQid = getArguments().getString("hash");
        mType = getArguments().getString("type");

//        mUid = Settings.Secure.getString(getContext().getContentResolver(),
//                Settings.Secure.ANDROID_ID);

        mSession = new Session(this.getContext());
        mUid = mSession.getUserID();
        mAnswerSingleChoice = new AnswerSingleChoice();
        mAnswerSingleChoice.setQid(mQid);
        mAnswerSingleChoice.setUid(mUid);

        mButtonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //index to 0, current index starts from 1.
                if(mAnswerSingleChoice.getResponse() !=null )
                    mAnswerSingleChoice.setResponse(mAnswerSingleChoice.getResponse() - 1);
                System.out.println("Answer: "+ new Gson().toJson(mAnswerSingleChoice,AnswerSingleChoice.class));
                AnswerManager.getInstance().put_answer2(new Gson().toJson(mAnswerSingleChoice,AnswerSingleChoice.class));
                if(mType == null){
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentSingleChoice.this.getActivity());
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
                    //((SurveyActivity) mContext).go_to_next();
                else {
                    postAnswer();
                    ((SurveysActivity) mContext).go_to_next();
                }
            }
        });

        return rootView;
    }

    private void collect_data() {
        int checkedId = mToggleButtonGroup.getCheckedId();
        if(checkedId != -1) {
                int max = mQuestion.getNumberOfItems();
                int firstChild = mToggleButtonGroup.getChildAt(0).getId();
                mAnswerSingleChoice.setResponse(sanitize_data(checkedId,max,firstChild));
        }

//        if (mQuestion.getRequired()) {
//            if (mToggleButtonGroup.getCheckedId() != -1) {
//                mButtonContinue.setVisibility(View.VISIBLE);
//            } else {
//                mButtonContinue.setVisibility(View.GONE);
//            }
//        }

    }

    private int sanitize_data(int choice,int max, int firstChild){
            if((choice - (firstChild -1)) % max == 0)
                return max;
            else
                return (choice - (firstChild -1)) % max;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        mContext = getActivity();
        mQuestion = (Question) getArguments().getSerializable("data");

        mTextViewTitle.setText(mQuestion != null ? "Select one of the following" : "");
        mTextviewSubtitle.setText(mQuestion != null ? "Single Choice" : "");

        mAnswerSingleChoice.setQtype(mQuestion.getQuestionType());

//        if (mQuestion.getRequired()) {
//            mButtonContinue.setVisibility(View.GONE);
//        }

//        List<String> qq_data = mQuestion.getChoices();
//        if (mQuestion.getRandomChoices()) {
//            Collections.shuffle(qq_data);
//        }
//

        GradientDrawable mGreenGradient = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), src.nex3z.togglebuttongroup.R.drawable.bg_circle);
        mGreenGradient.setColor(Color.parseColor("#4AB97F"));

        for(int i = 0; i < mQuestion.getNumberOfItems(); i++){
            CircularToggle toggle = new CircularToggle(mContext);
            toggle.setTextSize(24);
            toggle.setText(Character.toString(mOptionNames.charAt(i)));
            toggle.setCheckedImageDrawable(mGreenGradient);
            mToggleButtonGroup.addView(toggle);
        }

        mToggleButtonGroup.setOnCheckedChangeListener(new SingleSelectToggleGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SingleSelectToggleGroup group, int checkedId) {
                Log.v(LOG_TAG, "onCheckedChanged(): checkedId = " + checkedId);
                collect_data();
            }
        });

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
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentSingleChoice.this.getActivity());
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