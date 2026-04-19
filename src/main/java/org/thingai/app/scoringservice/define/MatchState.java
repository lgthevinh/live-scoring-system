package org.thingai.app.scoringservice.define;

/*
This class defines the regular life cycle state of a robotics competition match. The state is used to validate request
made by referee and scorekeeper. This also will sync with the front-end to display information when system sync is
needed:
    - NOT_STARTED: The match has not started yet. Scorekeeper can load the match or jump to active match. Match will be
    store in buffer.
    - LOADED: The match is loaded. Scorekeeper can jump to active match.
    - ACTIVE: The match is active. Scorekeeper now can start match timer. Referee is not
    yet be able to access score submission, but can access match information.
    - IN_PROGRESS: The match is started and in progress. Scorekeeper can stop match timer and allow referee to update
    score, but not yet submit score.
    - ON_REVIEW: The match is over, and on review. Referee can submit score, but not yet finalize score.
    - SUBMITTED: The match score is submitted, but not yet finalized. Scorekeeper can finalize score, but not yet start
    next match.
    - COMPLETED: The match is completed, match score is finalized (commited). Scorekeeper can start next match.
 */
public class MatchState {
    public static final int NOT_STARTED = 0;
    public static final int LOADED = 1;
    public static final int ACTIVE = 2;
    public static final int IN_PROGRESS = 3;
    public static final int ON_REVIEW = 4;
    public static final int SUBMITTED = 5;
    public static final int COMPLETED = 6;
}
