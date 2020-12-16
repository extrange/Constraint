# Hospital Timetable Scheduler

This program uses the Choco Constraint solver library to solve timetable allocations for a sample hospital duty planning scenario.

## An example:

Constraints:

 * exactly 2 people on Saturday
 * exactly 1 person on Mon-Fri and Sunday
 * min 2 days break between duties
 * between 3-4 duties per person per month
 * at most 1 Saturday duty per month
 * allocate duties fairly based on point system (Mon-Thu: 2, Fri: 3, Sat: 4, Sun: 3)
 
 ### Solution (see [SghOandG.java](/src/SghOandG.java) for source)
 
 Numbers represent the person doing the duty e.g. 1 = person A, 2 = person B and so on
![](/animation.gif)
![](/optimal_solution.jpg)
