import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.Arrays;
import java.util.List;

/* In general, this should be a general purpose solver, not specific to any one hospital.
 * ===Required constraints===
 * + exactly 1 HO1/2/3 on weekdays
 * + exactly 1 HO1/2 on weekends
 * + min 2 days break between duties
 * + exactly 3-5 duties per person
 * + at most 2 HO1 duties per month
 * + Basic optimization w/point system
 * + If in a 2 person team, no duties while the other HO is on long leave (user to manually input)
 * todo HO1 should be done by team on take (ideally - allow relaxation of this constraint if no solution found)
 * todo Optimization with weekends included (x1.5?)
 * todo No duties while HO on leave/block (partially done, clean up interface)
 * todo No duty on the day before HO takes leave/block
 * todo When optimum is reached, generate more solutions using that solution
 * todo No two HOs to be on call from the same team
 * TESTESTESTESTESTE
 *
 *
 * ===Required Features===
 * todo Cleaner constraint interface with model outputting active constraints, leave/block input as ranges
 * todo Month detection
 * todo Weekend detection and hence auto-constraining
 * todo Excel output
 * todo Telegram interface*/

public class Constraint {
    static final int HO1 = 6;
    static final int HO2 = 5;
    static final int HO3 = 3;

    public static void main(String[] args) {
        int days = 31;
        int people = 20;

        int[] typesOfDuty = {0, HO3, HO2, HO1}; //0 = no duty. 1 = HO1 that day and etc.
        int ideal = 20;

        // TODO: 7/22/2018 make a general purpose leave specifier
        int[][] leave = new int[people][];
        for (int i = 0; i < people; i++) {
            switch (i) {
                case 0:
                    leave[i] = new int[]{13, 14, 15};
                    break;
                case 1:
                    leave[i] = new int[]{2, 3, 4, 5, 6, 7, 13, 14};
                    break;
                case 2:
                    leave[i] = new int[]{13, 14};
                    break;
                case 3:
                    leave[i] = new int[]{21, 22, 23};
                    break;
                case 4:
                    leave[i] = new int[]{13, 14, 15, 16, 17, 18, 19, 20, 21};
                    break;
                case 5:
                    leave[i] = new int[]{13, 14, 15, 16, 17, 18};
                    break;
                case 6:
                    leave[i] = new int[]{0, 1, 5, 6, 7, 8};
                    break;
                case 7:
                    leave[i] = new int[]{13, 14, 19, 20, 21};
                    break;
                case 8:
                    leave[i] = new int[]{2, 13, 14};
                    break;
                case 9:
                    leave[i] = new int[]{13, 14};
                    break;
                case 10:
                    leave[i] = new int[]{5, 6, 7, 13, 14};
                    break;
                case 11:
                    leave[i] = new int[]{5, 6, 7, 13, 14};
                    break;
                case 12:
                    leave[i] = new int[]{0, 1};
                    break;
                case 13:
                    leave[i] = new int[]{10, 11, 12, 13, 14, 15};
                    break;
                case 14:
                    leave[i] = new int[]{13, 14};
                    break;
                case 15:
                    leave[i] = new int[]{0, 1, 20, 21, 27};
                    break;
                case 16:
                    leave[i] = new int[]{13, 14};
                    break;
                case 17:
                    leave[i] = new int[]{17, 20, 21, 22};
                    break;
                case 18:
                    leave[i] = new int[]{2, 13, 14, 20, 21, 22, 23, 24, 25, 26, 27, 28};
                    break;
                case 19:
                    leave[i] = new int[]{5, 13, 14, 20, 21, 22, 23, 24, 25, 26, 27, 28};
                    break;
                default:
                    leave[i] = new int[]{};
                    break;
            }
        }


        Model model = new Model();

        //duty[i][j] = duty (HO 1/2/3) assigned to HO i on day j
        IntVar[][] duty = model.intVarMatrix("HO i on day j", people, days, typesOfDuty);

        //dutyFlip[i][j] = duty (HO 1/2/3) assigned on day i to HO j
        //Simply transpose of duty[][]
        IntVar[][] dutyFlip = new IntVar[days][people];
        for (int i = 0; i < people; i++) {
            for (int j = 0; j < days; j++) {
                dutyFlip[j][i] = duty[i][j];
            }
        }

        /* Boolean Matrix - faster performance than integer matrix (8.391s, 124,430 backtracks vs 1.918s, 34,159
         * backtracks)
         * isOnDuty[i][j] = true if HO i is on duty on day j
         * */
        BoolVar[][] isOnDuty = model.boolVarMatrix(people, days);
        for (int i = 0; i < people; i++) {
            for (int j = 0; j < days; j++) {

                model.reification(isOnDuty[i][j], model.arithm(duty[i][j], "!=", 0));
            }
        }

        /*min 2 days break between duties*/
        for (int i = 0; i < people; i++) {
            for (int j = 0; j < days - 2; j++) {
                model.sum(new BoolVar[]{isOnDuty[i][j], isOnDuty[i][j + 1], isOnDuty[i][j + 2]}, "<", 2).post();
            }
        }

        /* Exactly one HO1/2/3 each weekday, exactly one HO1/2 each weekend.
         * Count constraint performs better than global cardinality constraint*/
        IntVar ONE = model.intVar(1);
        IntVar ZERO = model.intVar(0);
        List<Integer> weekends = Arrays.asList(0, 6, 7, 13, 14, 20, 21, 27, 28); // TODO: 7/22/2018 Do not hardcode this
        for (int i = 0; i < days; i++) {
            if (weekends.contains(i)) {
                model.count(HO3, dutyFlip[i], ZERO).post();
                model.count(HO2, dutyFlip[i], ONE).post();
                model.count(HO1, dutyFlip[i], ONE).post();
            } else {
                model.count(HO3, dutyFlip[i], ONE).post();
                model.count(HO2, dutyFlip[i], ONE).post();
                model.count(HO1, dutyFlip[i], ONE).post();
            }
        }

        /*At most 2 HO1 duties per person*/
        IntVar[] ho1Count = model.intVarArray(people, 0, 5);
        for (int i = 0; i < people; i++) {
            model.count(HO1, duty[i], ho1Count[i]).post();
        }
        model.max(model.intVar(2), ho1Count).post();

        /* HO leave */
        for (int i = 0; i < people; i++) {
            /*Performance from constraining is slightly better than assigning*/
            /*duty[0][i] = model.intVar(0);*/
            for (int j : leave[i]) {
                model.arithm(duty[i][j], "=", 0).post();
            }
        }

        //optimize such that each HO is close to ideal points?
        IntVar[] totalCost = model.intVarArray(people, 0, 50);
        IntVar[] diffTotalCost = model.intVarArray(people, 0, 50);
        IntVar idealVar = model.intVar(ideal);
        for (int i = 0; i < people; i++) {
            model.sum(duty[i], "=", totalCost[i]).post();
            model.distance(totalCost[i], idealVar, "=", diffTotalCost[i]).post();
        }
        IntVar obj = model.intVar(0, 400);
        model.sum(diffTotalCost, "=", obj).post();
        model.setObjective(Model.MINIMIZE, obj);
        Solver solver = model.getSolver();

        //large neighborhood search
        //2D -> 1D array
        IntVar[] oneDArray = new IntVar[days * people];
        int counter = 0;
        for (int i = 0; i < people; i++) {
            for (int j = 0; j < days; j++) {
                oneDArray[counter++] = duty[i][j];
            }
        }

        //Explanation-based seems to perform the best, combined with FailCounter(1000)/BacktrackCounter (10000)**
        solver.setLNS(INeighborFactory.explanationBased(oneDArray), new FailCounter(model, 1000));
        solver.showStatistics();
//        solver.setNoGoodRecordingFromRestarts();
        Solution solution = new Solution(model);
        System.out.println("Ideal = " + ideal);
        while (solver.solve()) {
            solution.record();
            System.out.println(prettify(solution, duty, people, days));
        }

    }

    public static String prettify(Solution solution, IntVar[][] duty, int people, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("Days ");
        for (int i = 0; i < days; i++) {
            sb.append(String.format("%2d", i)).append("|");
        }
        //for each person (i.e. each line)
        for (int i = 0; i < people; i++) {

            sb.append("\n").append(String.format("%2d", i)).append("   ");
            //for each day (j = day - 1)
            for (int j = 0; j < days; j++) {
                int val = duty[i][j].getValue();
                sb.append(val == 0 ? "  " : String.format("% 2d", getHODutyFromScore(val))).append("|");
            }

            //score
            int score = 0;
            int count = 0;
            for (IntVar intVar : duty[i]) {
                score += intVar.getValue();
                if (intVar.getValue() != 0)
                    count++;
            }

            //num duties
            sb.append(" ").append(score).append(" ").append(count);
        }
        return sb.toString();
    }

    public static int getHODutyFromScore(int score) {
        switch (score) {
            case HO1:
                return 1;
            case HO2:
                return 2;
            case HO3:
                return 3;
            default:
                throw new RuntimeException("No HO duty for score: " + score);
        }
    }
}