import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Specific to SGH O&G
 * ===Required constraints===
 * + exactly 2 HOs on Saturday
 * + exactly 1 HO on Mon-Fri and Sunday
 * + min 2 DAYS break between duties
 * + between 3-4 duties per person
 * + at most 1 Saturday duty per month
 * + Basic optimization w/point system
 * todo No duties while HO on leave/block (partially done, clean up interface)
 * todo No duty on the day before HO takes leave/block
 * todo When optimum is reached, generate more solutions using that solution
 */

public class SghOandG {

    /*Points:
     * Mon-Thu: 2
     * Fri: 3
     * Sat: 4
     * Sun: 3*/
    private static final int[] POINTS = {0, 2, 2, 2, 2, 3, 4, 3}; //0 == no duty that day
    private static final int MONTH = 9;
    private static final int YEAR = 2018;
    private static final int PEOPLE = 9;

    public static void main(String[] args) {
        LocalDate localDate = LocalDate.of(YEAR, MONTH, 1);
        final int DAYS = Month.of(MONTH).length(Year.isLeap(YEAR));

        Map<DayOfWeek, List<Integer>> dayDateMap = new HashMap<>();
        for (int i = DayOfWeek.MONDAY.getValue(); i < DayOfWeek.SUNDAY.getValue() + 1; i++) {
            dayDateMap.put(DayOfWeek.of(i), new ArrayList<>());
        }
        /*Calculate ideal number of points*/
        int sum = 0;
        for (int i = 0; i < DAYS; i++) {
            //get first day of month
            DayOfWeek dayOfWeek = DayOfWeek.of(localDate.get(ChronoField.DAY_OF_WEEK));

            //2 HOs on Saturday
            if (dayOfWeek == DayOfWeek.SATURDAY) {
                sum += POINTS[dayOfWeek.getValue()] * 2;
            } else {
                //1 HO otherwise
                sum += POINTS[dayOfWeek.getValue()];
            }

            dayDateMap.get(dayOfWeek).add(i + 1);

            localDate = localDate.plusDays(1);
        }

        //Round for now
        int ideal = (int) Math.round(sum * 1.0 / PEOPLE);

        System.out.println("Total points: " + sum + " Ideal: " + ideal);

        System.out.println(dayDateMap);

        Model model = new Model();

        /*duty[i][j] = points from duty assigned to HO i on day j
         * Used to calculate total number of points a HO gets for the month
         * e.g. sum(duty[i])*/
        //consider using Arrays.stream's distinct() in future ?performance
        IntVar[][] duty = model.intVarMatrix("Points assigned to HO i on day j", PEOPLE, DAYS, POINTS);

        /*DutyFlip[i][j] = points from duty on day i for HO j
         * Used to set constraints on number of duties in a day
         * e.g. model.count(2, dutyFlip[i], 2)*/
        IntVar[][] dutyFlip = new IntVar[DAYS][PEOPLE];
        for (int i = 0; i < PEOPLE; i++) {
            for (int j = 0; j < DAYS; j++) {
                dutyFlip[j][i] = duty[i][j];
            }
        }

        /*Boolean Matrix -faster performance than integer matrix (8.391 s, 124, 430 backtracks vs 1.918 s, 34, 159
         * backtracks)
         * isOnDuty[i][j] = true if HO i is on duty on day j
         * */
        BoolVar[][] isOnDuty = model.boolVarMatrix(PEOPLE, DAYS);
        for (int i = 0; i < PEOPLE; i++) {
            for (int j = 0; j < DAYS; j++) {
                model.reification(isOnDuty[i][j], model.arithm(duty[i][j], "!=", 0));
            }
        }

        /*min 2 DAYS break between duties*/
        for (int i = 0; i < PEOPLE; i++) {
            for (int j = 0; j < DAYS - 2; j++) {
                model.sum(new BoolVar[]{isOnDuty[i][j], isOnDuty[i][j + 1], isOnDuty[i][j + 2]}, "<", 2).post();
            }
        }

        /*Exactly one HO each weekday and Sunday, exactly two HOs on Saturday.
         * Count constraint performs better than global cardinality constraint*/
        IntVar ZERO = model.intVar(0);
        IntVar ONE = model.intVar(1);
        IntVar TWO = model.intVar(2);

        //Mon-Thu
        for (int i = DayOfWeek.MONDAY.getValue(); i <= DayOfWeek.THURSDAY.getValue(); i++) {
            for (int j : dayDateMap.get(DayOfWeek.of(i))) {
                model.count(POINTS[i], dutyFlip[j - 1], ONE).post();
            }
        }

        //Fri
        for (int i : dayDateMap.get(DayOfWeek.FRIDAY)) {
            model.count(POINTS[DayOfWeek.FRIDAY.getValue()], dutyFlip[i-1], ONE).post();
        }

        //Sat
        for (int i : dayDateMap.get(DayOfWeek.SATURDAY)) {
            model.count(POINTS[DayOfWeek.SATURDAY.getValue()], dutyFlip[i-1], TWO).post();
        }

        //Sun
        for (int i : dayDateMap.get(DayOfWeek.SUNDAY)) {
            model.count(POINTS[DayOfWeek.SUNDAY.getValue()], dutyFlip[i-1], ONE).post();
        }

        // TODO: 9/25/2018 At most 1 saturday duty per person
//        IntVar[] satDutyCount = model.intVarArray(PEOPLE, 0, 5);
//        for (int i = 0; i < PEOPLE; i++) {
//            model.count(POINTS[DayOfWeek.SATURDAY.getValue()], duty[i], satDutyCount[i]).post();
//        }
//        model.max(ONE, satDutyCount).post();



        // TODO: 9/25/2018 Leave

        //optimize such that each HO is close to ideal points?
        IntVar[] totalCost = model.intVarArray(PEOPLE, 0, 50);
        IntVar[] diffTotalCost = model.intVarArray(PEOPLE, 0, 50);
        IntVar idealVar = model.intVar(ideal);
        for (int i = 0; i < PEOPLE; i++) {
            model.sum(duty[i], "=", totalCost[i]).post();
            model.distance(totalCost[i], idealVar, "=", diffTotalCost[i]).post();
        }
        IntVar obj = model.intVar(0, 400);
        model.sum(diffTotalCost, "=", obj).post();
        model.setObjective(Model.MINIMIZE, obj);
        Solver solver = model.getSolver();

        //large neighborhood search
        //2D -> 1D array
        IntVar[] oneDArray = new IntVar[DAYS * PEOPLE];
        int counter = 0;
        for (int i = 0; i < PEOPLE; i++) {
            for (int j = 0; j < DAYS; j++) {
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
            System.out.println(prettify(duty, PEOPLE, DAYS));
        }

    }

    public static String prettify(IntVar[][] duty, int people, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("Days ");
        for (int i = 0; i < days; i++) {
            sb.append(String.format("%2d", i)).append("|");
        }
        sb.append("Score Duty count");

        //for each person (i.e. each line)
        for (int i = 0; i < people; i++) {

            sb.append("\n").append(String.format("%2d", i)).append("   ");
            //for each day (j = day - 1)
            for (int j = 0; j < days; j++) {
                int val = duty[i][j].getValue();
                sb.append(val == 0 ? "  " : String.format("% 2d", val)).append("|");
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
            sb.append(" ").append(score).append("   ").append(count);
        }
        return sb.toString();
    }
}