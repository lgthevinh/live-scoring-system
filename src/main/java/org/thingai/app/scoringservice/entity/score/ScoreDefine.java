package org.thingai.app.scoringservice.entity.score;

import org.thingai.app.scoringservice.define.ui.ScoreUiType;
import org.thingai.app.scoringservice.define.ui.ScoreValueType;

public class ScoreDefine {
    public final String displayName;
    public final ScoreUiType uiType;
    public final ScoreValueType valueType;

    public ScoreDefine(String displayName, ScoreUiType uiType, ScoreValueType valueType) {
        this.displayName = displayName;
        this.uiType = uiType;
        this.valueType = valueType;
    }
}
