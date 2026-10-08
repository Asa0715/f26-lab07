# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** 

`src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowTest.java`,
test `recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds`.
It pins this behavior of `BookingWorkflow.submit` for a RECURRING request: 
if a week's slot starts exactly when an existing booking in the same room ends, 
that week is skipped. The outcome is still accepted, with 2 of 3 weeks booked. 
`getSkipped()` holds exactly that first slot, and the first booking written 
has occurrence index 2, not 1. The test is green against the shipped code (36/36). 
I added one new method and one import, and did not edit or delete any existing test method.

**Why that one, and does a shipped test already cover it?** 

The three branches of `submit` use different comparisons for room overlap. 
`REGULAR` and `BLOCKED` use strict `<`, so back-to-back slots are allowed. 
`RECURRING` uses `<=` (`BookingWorkflow.java:121–122`), so back-to-back slots 
count as a conflict. A refactor that replaces the type switch is likely to pull 
those three loops into one shared `overlaps()` helper. That would silently change 
the recurring rule while all 35 shipped tests stay green.

The closest shipped test is `regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`,
but it pins only the `REGULAR` side of that boundary.
`recurringSubmitBooksEveryWeekOfAnOpenSeries` books into an empty room, so it
never skips a week. My pin adds the `RECURRING` side of the boundary and pins
two side effects: the skipped list and the gap in occurrence numbering.


**What a regeneration would do differently here.** 

The decision is what "overlap" means at a boundary, and whether it is the same 
for every booking type. A one-line spec ("members book rooms once or weekly; 
staff can block rooms") does not mention boundaries. A regenerated class would 
almost certainly write a single overlap check with half-open intervals 
(a.start < b.end && b.start < a.end), which matches TimeSlot's own Javadoc 
("inclusive start, exclusive end"). That check would be used for all three types.
A weekly series that starts right when another booking ends would then book every 
week, with an empty skipped list and occurrence indexes 1..n, and this test 
would go red.

### The directive

**The refactor and the exact directive.** 

Refactor: Replace Conditional with Polymorphism in
`src/main/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflow.java`.

Remove the BookingType switch from all four methods (submit, cancel, priceOf,
describe). Add a package-private BookingHandler interface and three
implementations (Regular/Recurring/BlockedBookingHandler) in the workflow
package, and move each case body into its handler verbatim. BookingWorkflow
picks the handler from an EnumMap built in its constructor; that is the only
place a type maps to behavior. Keep the shared checks and the current
default-branch results in BookingWorkflow. Public signatures do not change.

This is a move, not a fix. Behavior must stay identical, including:
- RECURRING's overlap check uses `<=`; REGULAR and BLOCKED use `<`. Keep both,
  and do not merge them into a shared helper.
- Every message string, notification (recipient, subject, body, count, order),
  and store call order (nextSeriesId before the weekly loop) stays the same.
- Cancel and price semantics stay the same. RECURRING cancels this occurrence
  and later ones. BLOCKED needs adminOverride. RECURRING priceOf sums the
  series.

Scope
In: BookingWorkflow.java, plus new files in the workflow/ package.
Out: domain/ (including BookingType; no behavior on the enum), notify/,
pricing/, reporting/, all of src/test/, pom.xml, .github/, and the *.md files.
Why: the smell lives only in BookingWorkflow; everything else is the contract
its callers and tests rely on, or belongs to a later milestone, and the tests
are the check, so they cannot change with the code.
If you think something out of scope must change, stop and ask.

Done: `mvn -B test` shows 36 tests, all green, with no test files changed;
there is no `switch` left in workflow/; nothing is committed. List the files
you touched.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
