package src.adux.votingapplib.models.PollAnswers.AnswerRedGreen;

/**
 * Created by axap on 3/13/18.
 */

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Response {

    @SerializedName("red")
    @Expose
    private List<Integer> red = null;
    @SerializedName("green")
    @Expose
    private List<Integer> green = null;

    public List<Integer> getRed() {
        return red;
    }

    public void setRed(List<Integer> red) {
        this.red = red;
    }

    public List<Integer> getGreen() {
        return green;
    }

    public void setGreen(List<Integer> green) {
        this.green = green;
    }

}