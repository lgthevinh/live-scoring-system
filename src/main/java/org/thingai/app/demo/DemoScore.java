package org.thingai.app.demo;

import com.google.gson.Gson;
import org.thingai.app.scoringservice.strategy.IScoreStrategy;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.ScoreDefine;

import java.util.HashMap;

public class DemoScore extends Score implements IScoreStrategy {
    // New scoring system fields
    private int whiteBallsScored; // 1 point each via human player
    private int goldenBallsScored; // 3 points each via robot autonomous
    private int barriersPushed; // 0-2, 10 points each
    private int imbalanceCategory; // 0=balanced(2.0), 1=medium(1.5), 2=large(1.3)
    private int partialParking; // number of robots partially in green zone (5 points each)
    private int fullParking; // number of robots fully in green zone (10 points each)
    private int penaltyCount; // minor penalty violations, 5 points per violation
    private int yellowCardCount; // yellow card violations, 10 points per violation
    private boolean redCard; // if true, entire score is zeroed

    // Legacy fields (keeping for compatibility)
    private int ballsCollected;
    private int ballsScored;
    private int foulsCommitted;

    private double getBalancingCoefficient() {
        double baseCoeff = switch (imbalanceCategory) {
            case 0 -> 2.0; // balanced (0-1 ball difference)
            case 1 -> 1.5; // medium imbalance (2-3 balls)
            case 2 -> 1.3; // large imbalance (4+ balls)
            default -> 1.3;
        };

        // Subtract 0.2 if alliance didn't push their barrier (assuming 1 barrier per alliance)
        if (barriersPushed == 0) {
            baseCoeff -= 0.2;
        }

        // Ensure coefficient doesn't go below 0
        return Math.max(0.0, baseCoeff);
    }

    @Override
    public void calculateTotalScore() {
        // If red card, score is 0
        if (redCard) {
            totalScore = 0;
            penaltiesScore = 0;
            return;
        }

        // Biological points = robot-scored balls + human-scored balls
        int biologicalPoints = (goldenBallsScored * 3) + whiteBallsScored;

        // Barrier points
        int barrierPoints = barriersPushed * 10;

        // End game points
        int endGamePoints = (partialParking * 5) + (fullParking * 10);

        // Fleet bonus: 10 points if both robots fully parked
        if (fullParking >= 2) {
            endGamePoints += 10;
        }

        // Adjusted balancing coefficient
        double coeff = getBalancingCoefficient();

        // Calculate base score
        double baseScore = (biologicalPoints + barrierPoints) * coeff;

        // Total score includes penalties
        totalScore = (int) Math.round(baseScore + endGamePoints - (penaltyCount * 5) - (yellowCardCount * 10));
    }

    @Override
    public void calculatePenalties() {
        penaltiesScore = (penaltyCount * 5) + (yellowCardCount * 10);
    }

    @Override
    public void fromJson(String json) {
        Gson gson = new Gson();
        DemoScore temp = gson.fromJson(json, DemoScore.class);
        // New fields
        this.whiteBallsScored = temp.whiteBallsScored;
        this.goldenBallsScored = temp.goldenBallsScored;
        this.barriersPushed = temp.barriersPushed;
        this.imbalanceCategory = temp.imbalanceCategory;
        this.partialParking = temp.partialParking;
        this.fullParking = temp.fullParking;
        this.penaltyCount = temp.penaltyCount;
        this.yellowCardCount = temp.yellowCardCount;
        this.redCard = temp.redCard;
        // Legacy fields
        this.ballsCollected = temp.ballsCollected;
        this.ballsScored = temp.ballsScored;
        this.foulsCommitted = temp.foulsCommitted;
    }

    @Override
    public String getRawScoreData() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public HashMap<String, ScoreDefine> getScoreDefinitions() {
        HashMap<String, ScoreDefine> definitions = new HashMap<>();

        definitions.put("whiteBallsScored", new ScoreDefine("White Balls Scored by Human", null, null));
        definitions.put("goldenBallsScored", new ScoreDefine("Golden Balls Scored by Robot", null, null));
        definitions.put("barriersPushed", new ScoreDefine("Barriers Pushed Away", null, null));
        definitions.put("imbalanceCategory", new ScoreDefine("Ball Imbalance Category (0=Balanced, 1=Medium, 2=Large)", null, null));
        definitions.put("partialParking", new ScoreDefine("Robots Partially in Green Zone", null, null));
        definitions.put("fullParking", new ScoreDefine("Robots Fully in Green Zone", null, null));
        definitions.put("penaltyCount", new ScoreDefine("Number of Minor Penalty Violations", null, null));
        definitions.put("yellowCardCount", new ScoreDefine("Number of Yellow Card Violations", null, null));
        definitions.put("redCard", new ScoreDefine("Red Card Issued", null, null));

        // Legacy
        definitions.put("ballsCollected", new ScoreDefine("Balls Collected", null, null));
        definitions.put("ballsScored", new ScoreDefine("Balls Scored", null, null));
        definitions.put("foulsCommitted", new ScoreDefine("Fouls Committed", null, null));

        return definitions;
    }
}
