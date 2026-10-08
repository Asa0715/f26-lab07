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

**The diff and the suite.** 

The refactor is commit `52b1cef`, directly after the pin commit `4aa0749`. 
The diff can be checked with `git show 52b1cef`.

Edited 5 code files, all in `src/main/java/edu/cmu/cs214/scheduling/workflow/`:

* `BookingWorkflow.java`: +22 / −212 lines. Removed all four
  `switch (type)` blocks. It now runs the shared checks and delegates to a
  handler looked up in an `EnumMap<BookingType, BookingHandler>` built in the
  constructor.
* `BookingHandler.java` (new): +32 lines. Package-private interface with
  `submit`, `cancel`, `priceOf`, `describe`, plus `FACILITIES_CONTACT` and
  `recipientFor`.
* `RegularBookingHandler.java` (new): +91 lines. The former `case REGULAR`
  bodies from all four methods.
* `RecurringBookingHandler.java` (new): +122 lines. The former `case RECURRING`
  bodies, plus `MAX_SERIES_WEEKS`.
* `BlockedBookingHandler.java` (new): +71 lines. The former `case BLOCKED`
  bodies.

**Code total:** +338 / −212. The same commit also updates `REFACTOR.md`
(+34 / −4) with the directive section.

**Suite:** `mvn -B test` → `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS`. That is the 35 shipped tests plus the pin
(`recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds`), all green.

**What did NOT change: behavior and files.** 

*Behavior.* All 36 tests pass after the refactor, without editing any test. The
pin is among them. The behavior that surprised me while reading is still there:
RECURRING treats a back-to-back slot as a conflict (`<=`), while REGULAR and
BLOCKED allow it (`<`). The `<=` now lives in
`RecurringBookingHandler.submit` (lines 60–61), and REGULAR and BLOCKED keep
`<` in their own handlers. The three overlap loops were not merged into a
shared helper. That is the change the pin exists to catch, and it stays green.

*Files.* Nothing outside the scope was touched. I checked this rather than
assumed it.
- `git diff --name-only 4aa0749 52b1cef` lists only `REFACTOR.md` and five
  files in `workflow/`.

The agent did not reach outside the directive. `BookingType` was not given
behavior, and no test or out-of-scope file changed.

**One thing the agent changed that you had to look at twice.** 

The `recipientFor` move. In the shipped code it was a `private static` helper on
`BookingWorkflow` (line 293), called from the REGULAR and RECURRING branches of
`cancel` (lines 191 and 205). The agent moved it, along with
`FACILITIES_CONTACT`, into the `BookingHandler` interface as a static method
(`BookingHandler.java:29–30`). The two call sites now read
`BookingHandler.recipientFor(member)` (`RegularBookingHandler.java:73` and
`RecurringBookingHandler.java:93`), and the `"Booking cancelled"` call was
re-wrapped onto a new line.

I checked it line by line because it is the only place in the diff where code
was rewritten rather than moved verbatim, and no test guards it.

I accepted it for three reasons:
- The method body is unchanged: `member == null ? FACILITIES_CONTACT : member.getEmail()`.
- The constant's value is unchanged.
- The qualified name is required by Java, because static methods on an
  interface are not inherited by implementing classes, so an unqualified
  `recipientFor(member)` would not compile.

### The closing explanation

**Refactor or regenerate?** 

Refactoring was the better call.

Two questions decide it:
- *Spec quality: no one could rebuild this class from the written contract.*
  The README paragraph and Javadocs never state the per-type overlap rule (`<=`
  for RECURRING, `<` elsewhere), forward-only series cancellation, series-total
  `priceOf`, or the notification wording, so a rebuild would replace each of
  them with an agent's default.
- *Test coverage: the checks pin counts, not content.* `BookingWorkflowTest`
  mostly asserts sizes of `getBooked()`, `activeInRoom()`, and the outbox, and
  only two tests pin full notification text (both REGULAR confirmations), so a
  regenerated class could pass all 35 shipped tests while changing messages,
  recipients, and the boundary rule.

Code age (no fixes since generation) and reach (no client or front end, only
our own tests and `ReportService`) lean toward regenerating, but neither
outweighs a missing spec and shallow tests.

**What would flip your answer.** 

I would regenerate if the tests pinned the
content of every observable output for all three types, not just the counts.
That means the outcome messages, the skipped slots, the full outbox text
including recipients, and the boundary rule. A regenerated class could then be
checked against the suite instead of against the old code.

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
