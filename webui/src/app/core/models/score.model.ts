export interface Score {
  id: string;
  status: number;
  totalScore: number;
  penaltiesScore: number;
  rawScoreData: string; // JSON string

  // Fanroc scoring system fields (from JSON)
  whiteBallsScored?: number;
  goldenBallsScored?: number;
  allianceBarrierPushed?: boolean;
  opponentBarrierPushed?: boolean;
  imbalanceCategory?: number;
  partialParking?: number;
  fullParking?: number;
  penaltyCount?: number;
  yellowCardCount?: number;
  redCard?: boolean;
}

export interface CustomScoreData {
  [key: string]: number | boolean;
}

export enum EUiType {
  COUNTER = 'COUNTER',
  TOGGLE = 'TOGGLE',
  TEXT = 'TEXT'
}

export enum EValueType {
  NUMBER = 'NUMBER',
  BOOLEAN = 'BOOLEAN',
  STRING = 'STRING'
}

export interface ScoreDefine {
  key: string;
  displayName: string;
  uiType: EUiType;
  valueType: EValueType;
  min?: number;
  max?: number;
  step?: number;
  defaultValue?: any;
  order?: number;
}

/**
 * Fanroc scoring calculation utilities (mirrors FanrocScore.java logic)
 */
export class FanrocScoringCalculator {

  /**
   * Get the balancing coefficient based on imbalance category
   * @param imbalanceCategory - The imbalance category (0=balanced, 1=medium, 2=large)
   * @param allianceBarrierPushed - true if alliance pushed their barrier, false if not (reduces coefficient by 0.2 if false)
   */
  static getBalancingCoefficient(imbalanceCategory: number, allianceBarrierPushed: boolean = false): number {
    let baseCoeff: number;
    switch (imbalanceCategory) {
      case 0:
        baseCoeff = 2.0; // balanced (0-1 ball difference)
        break;
      case 1:
        baseCoeff = 1.5; // medium imbalance (2-3 balls)
        break;
      case 2:
        baseCoeff = 1.3; // large imbalance (4+ balls)
        break;
      default:
        baseCoeff = 1.3;
        break;
    }

    // Subtract 0.2 if alliance didn't push their barrier (assuming 1 barrier per alliance)
    if (!allianceBarrierPushed) {
      baseCoeff -= 0.2;
    }

    // Ensure coefficient doesn't go below 0
    return Math.max(0.0, baseCoeff);
  }

  /**
   * Calculate biological points (balls in ecosystem)
   */
  static calculateBiologicalPoints(goldenBallsScored: number, whiteBallsScored: number): number {
    return (goldenBallsScored * 3) + whiteBallsScored;
  }

  /**
   * Calculate total Fanroc score
   */
  static calculateTotalScore(
    whiteBallsScored: number = 0,
    goldenBallsScored: number = 0,
    allianceBarrierPushed: boolean = false,
    opponentBarrierPushed: boolean = false,
    imbalanceCategory: number = 2,
    partialParking: number = 0,
    fullParking: number = 0,
    penaltyCount: number = 0,
    yellowCardCount: number = 0,
    redCard: boolean = false
  ): number {
    // If red card, score is 0
    if (redCard) {
      return 0;
    }

    const biologicalPoints = this.calculateBiologicalPoints(goldenBallsScored, whiteBallsScored);
    const barrierPoints = (allianceBarrierPushed ? 10 : 0) + (opponentBarrierPushed ? 10 : 0);

    // End game points
    let endGamePoints = (partialParking * 5) + (fullParking * 10);

    // Fleet bonus: 10 points if both robots fully parked
    if (fullParking >= 2) {
      endGamePoints += 10;
    }

    // Adjusted balancing coefficient
    const coeff = this.getBalancingCoefficient(imbalanceCategory, allianceBarrierPushed);

    // Calculate base score
    const baseScore = (biologicalPoints + barrierPoints) * coeff;

    // Penalty deductions (minor: 5 points each, yellow card: 10 points each)
    const penaltyPoints = (penaltyCount * 5) + (yellowCardCount * 10);

    // Total score (rounded like Java)
    return Math.round(baseScore + endGamePoints - penaltyPoints);
  }

  /**
   * Check if a score object has Fanroc fields (used for auto-detection)
   */
  static isFanrocScore(score: Score): boolean {
    return score.whiteBallsScored !== undefined ||
           score.goldenBallsScored !== undefined ||
           score.allianceBarrierPushed !== undefined ||
           score.opponentBarrierPushed !== undefined ||
           score.imbalanceCategory !== undefined ||
           score.partialParking !== undefined ||
           score.fullParking !== undefined ||
           score.penaltyCount !== undefined ||
           score.yellowCardCount !== undefined ||
           score.redCard !== undefined;
  }
}
