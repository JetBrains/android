package src.adux.votingapplib.models.PollAnswers;

/**
 * Created by axap on 3/13/18.
 */

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AnswerCheckAll {

    @SerializedName("qid")
    @Expose
    private String qid;
    @SerializedName("qtype")
    @Expose
    private String qtype;
    @SerializedName("response")
    @Expose
    private List<Integer> response = null;
    @SerializedName("uid")
    @Expose
    private String uid;

    public String getQid() {
        return qid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    public String getQtype() {
        return qtype;
    }

    public void setQtype(String qtype) {
        this.qtype = qtype;
    }

    public List<Integer> getResponse() {
        return response;
    }

    public void setResponse(List<Integer> response) {
        this.response = response;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

}
