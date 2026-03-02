package org.thingai.app.scoringservice.entity.match;

import org.thingai.base.dao.annotations.DaoColumn;
import org.thingai.base.dao.annotations.DaoTable;

/**
 * AllianceTeam entity represents the association between an alliance and a team.
 */
@DaoTable(name = "alliance_team")
public class AllianceTeam {

    // Format: matchId + _R/_B
    @DaoColumn(name = "allianceId")
    private String allianceId;

    @DaoColumn(name = "teamId")
    private String teamId;

    @DaoColumn(name = "isSurrogate")
    private boolean isSurrogate;

    @DaoColumn(name = "shownState")
    private int shownState; // 0 = not shown, 1 = no robot, 2 = shown

    public String getAllianceId() {
        return allianceId;
    }

    public void setAllianceId(String allianceId) {
        this.allianceId = allianceId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public boolean isSurrogate() {
        return isSurrogate;
    }

    public void setSurrogate(boolean surrogate) {
        isSurrogate = surrogate;
    }

    public int getShownState() {
        return shownState;
    }

    public void setShownState(int shownState) {
        this.shownState = shownState;
    }
}
