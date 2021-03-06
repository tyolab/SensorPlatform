package au.carrsq.sensorplatform.UI;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import au.carrsq.sensorplatform.Core.MainActivity;
import au.carrsq.sensorplatform.Core.SurveyModel;
import au.carrsq.sensorplatform.Logger.LoggingModule;
import au.carrsq.sensorplatform.R;
import au.carrsq.sensorplatform.Utilities.IO;

public class SurveyFragment extends Fragment {

    SurveyModel survey;

    private FrameLayout next;
    private TextView question;
    private int currentQuestion = 0;

    private RadioButton stronglyDisagree, disagree, undecided, agree, stronglyAgree;

    private RadioGroup group;

    private String answers = "";

    public SurveyFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        View v = inflater.inflate(R.layout.fragment_survey, container, false);
        question = (TextView) v.findViewById(R.id.question);
        next = (FrameLayout) v.findViewById(R.id.next_question_button);
        next.setOnClickListener(next_listener);

        stronglyDisagree = (RadioButton) v.findViewById(R.id.stronglyDisagree);
        disagree = (RadioButton) v.findViewById(R.id.disagree);
        undecided = (RadioButton) v.findViewById(R.id.undecided);
        agree = (RadioButton) v.findViewById(R.id.agree);
        stronglyAgree = (RadioButton) v.findViewById(R.id.stronglyAgree);

        group = (RadioGroup)v.findViewById(R.id.lickertGroup);

        loadSurvey();
        showQuestion(currentQuestion);

        return v;
    }

    View.OnClickListener next_listener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String answer = getCheckedLickertButton();
            if(answer.equals("invalid")) {
                handleEmpty();
                return;
            }

            answers += answer + ";";

            if(++currentQuestion < survey.questions.size()) {
                Log.d("NEXT QUESTION", getCheckedLickertButton());
                Log.d("ANSWERS", "--> " + answers);
                showQuestion(currentQuestion);

            } else {
                // write to file
                LoggingModule lm = LoggingModule.getInstance();
                lm.writeSurveyToFile(answers, survey.questions.size());

                // reset answer string
                answers = "";

                leaveSurvey();

            }
        }
    };

    private void leaveSurvey() {
        // go back to main data collection screen
        MainActivity app = (MainActivity) getActivity();
        app.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        app.setIntent(new Intent("au.carrsq.sensorplatform"));
        app.goToAppFragment();
    }

    private void handleEmpty() {
        Toast.makeText(getActivity(), "Please select an answer.", Toast.LENGTH_SHORT).show();
    }

    private String getCheckedLickertButton() {
        if(stronglyAgree.isChecked())
            return "Strongly Agree";
        if(agree.isChecked())
            return "Agree";
        if(undecided.isChecked())
            return "Undecided";
        if(disagree.isChecked())
            return "Disagree";
        if(stronglyDisagree.isChecked())
            return "Strongly Disagree";

        return "invalid";
    }

    private void loadSurvey() {
        String surveyJson = IO.loadJSONFromFile("survey.json");
        if( !surveyJson.equals("") && surveyJson != null)
            this.survey = new Gson().fromJson(surveyJson, SurveyModel.class);
        else {
            Toast t = new Toast(getActivity());
            t.setText("Could not load survey.");
            t.setDuration(Toast.LENGTH_SHORT);
            t.show();
            leaveSurvey();
        }
    }

    private void showQuestion(int index) {
        resetButtons();
        question.setText("\"" + survey.questions.get(index).q + "\"");
    }

    private void resetButtons() {
        Log.d("SURVEY", "Reset");
        group.clearCheck();
    }




}
