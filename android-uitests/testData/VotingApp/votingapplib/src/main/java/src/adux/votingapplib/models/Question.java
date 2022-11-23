package src.adux.votingapplib.models;



import java.io.Serializable;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Question implements Serializable {

    @SerializedName("choices")
    @Expose
    private List<String> choices = null;
    @SerializedName("greenDots")
    @Expose
    private Integer greenDots;
    @SerializedName("redDots")
    @Expose
    private Integer redDots;
    @SerializedName("questionType")
    @Expose
    private String questionType;
    @SerializedName("number_of_items")
    @Expose
    private Integer number_of_items;

    public List<String> getChoices() {
        return choices;
    }

    public void setChoices(List<String> choices) {
        this.choices = choices;
    }

    public Integer getGreenDots() {
        return greenDots;
    }

    public void setGreenDots(Integer greenDots) {
        this.greenDots = greenDots;
    }

    public Integer getRedDots() {
        return redDots;
    }

    public void setRedDots(Integer redDots) {
        this.redDots = redDots;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public Integer getNumberOfItems() {
        return number_of_items;
    }

    public void setNumberOfItems(Integer number_of_items) {
        this.number_of_items = number_of_items;
    }
}
