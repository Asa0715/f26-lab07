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

- **Strategy.** `NotificationStrategy` (interface) and `EmailNotificationStrategy`
  (its only implementation). `NotificationHub` holds a strategy and calls
  `render(message)` to turn a message into text.
- **Observer (publish/subscribe).** `NotificationHub` is the subject, with
  `subscribe()` and `publish()`. `NotificationSubscriber` is the observer
  interface, and `OutboxSubscriber` is its only implementation, which appends
  to `Outbox`.
- **Factory.** `NotifierFactory.createStrategy()` decides which
  `NotificationStrategy` the hub gets. It always returns a new
  `EmailNotificationStrategy`.

### The problem each one solves

- **Strategy.** The same message must be rendered in more than one format,
  such as email for one member and SMS for another, and which format applies
  is chosen at runtime by configuration or by the recipient.
- **Observer.** One published notification must reach several independent
  destinations, such as the outbox, an audit log, and a Slack channel, and the
  set of destinations changes without the publisher being edited.
- **Factory.** Which `NotificationStrategy` to build depends on a condition
  known only at runtime, such as a config setting or the recipient's preferred
  channel, and that choice should be made in one place instead of at every
  call site.

### Which of those problems exist here

- **Strategy: no.** There is one format and nothing can choose another.
  - `EmailNotificationStrategy` is the only class that implements
    `NotificationStrategy`.
  - `NotificationHub`'s constructor hard-wires it
    (`this.strategy = NotifierFactory.getInstance().createStrategy();`,
    `NotificationHub.java:22`).
  - No constructor parameter or setter lets a caller supply a different
    strategy.
- **Observer: no.** There is one destination and nothing adds another.
  - `OutboxSubscriber` is the only class that implements
    `NotificationSubscriber`.
  - The only call to `subscribe()` anywhere in `src/` is in the hub's own
    constructor (`NotificationHub.java:23`). `BookingWorkflow` never
    subscribes anything.
  - The test `hubDeliversToItsOneSubscriber` asserts that the count is 1.
- **Factory: no.** There is no runtime condition to decide on.
  - `NotifierFactory.createStrategy()` (`NotifierFactory.java:19–20`) takes no
    arguments and contains no branch. It always returns
    `new EmailNotificationStrategy()`.
  - Its only caller is `NotificationHub.java:22`.

### The simpler structure

**Your proposal.** 

`notify/` shrinks to three classes:
- `NotificationMessage`, unchanged.
- `Outbox`, unchanged.
- `NotificationHub`, which renders the message itself and appends it to the
  outbox.

`NotificationStrategy`, `EmailNotificationStrategy`, `NotifierFactory`,
`NotificationSubscriber`, and `OutboxSubscriber` are deleted. The one method
that matters:

    public class NotificationHub {
        private final Outbox outbox;

        public NotificationHub() { this(new Outbox()); }
        public NotificationHub(Outbox outbox) { /* null check */ this.outbox = outbox; }

        public void publish(NotificationMessage message) {
            outbox.append("To: " + message.recipient()
                    + " | Subject: " + message.subject()
                    + " | " + message.body());
        }

        public Outbox getOutbox() { return outbox; }
    }

`BookingWorkflow` does not change, because it only calls `hub.publish(...)`.

**What stays the same.** 

Every message `publish` receives lands in the outbox
as one line, `"To: <recipient> | Subject: <subject> | <body>"`, in publish
order. That is what `publishedMessageLandsInTheOutboxFullyRendered`,
`aConfirmationFromTheWorkflowReachesTheOutbox`, and the
`hub.getOutbox().size()` checks in `BookingWorkflowTest` pin. The
`NotificationHub` constructors and `getOutbox()` keep their signatures, so
those tests run unchanged. The two tests that only check structure,
`hubDeliversToItsOneSubscriber` and `factoryHandsBackTheSameInstance`, are
deleted along with the layers they check.

**What you would keep, if anything.** None of the interfaces.

`NotificationStrategy` and `NotificationSubscriber` each have one
implementation and no second caller, so they add a hop without separating
anything. If a second format or destination arrives, extracting the interface
from `NotificationHub` is a small, mechanical refactor that the same tests can
verify. Keeping it now means paying for it before we know its shape.
`NotificationMessage` and `Outbox` stay, but they are data and storage, not
pattern layers.

### What would bring each layer back

- **`NotificationStrategy` (Strategy).** Members can choose to receive
  confirmations by email or by SMS, and the SMS text must fit in limited
  characters with no `To:` / `Subject:` header. Then the hub needs to render
  the same `NotificationMessage` two different ways, picked per recipient.
- **`NotificationSubscriber` (Observer).** Facilities asks that every "Room
  blocked" and "Block released" notice also be posted to their Slack channel,
  and compliance asks that every notification be written to an audit log. Then
  one `publish` must reach three destinations, and new ones can be added
  without editing `NotificationHub` or `BookingWorkflow`.
- **`NotifierFactory` (Factory).** The channel is chosen at startup from
  deployment config, for example email in production and a console printer in
  local dev. Then deciding which strategy to build belongs in one place
  instead of in the hub's constructor.

**Misuse or anti-pattern?** This is misuse. 

Strategy, Observer, and Factory are sound solutions, but here they were applied\
to problems this codebase does not have: one format, one destination, 
and no runtime choice. 

An **anti-pattern** would be a structure that is harmful whatever 
the requirements are. The distinction matters because it changes the fix 
and the follow-up. Misuse means removing the layers now and bringing them back 
when one of the requirements above actually arrives. Calling it an anti-pattern 
would wrongly suggest these patterns should never be used, and would leave 
the next developer without a clear trigger for when they become the right call.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

Strategy, with each pricing rule as a `PricingRule`. The
problem is that four independent, ordered price adjustments are hard-coded in
one `price()` method, so adding or reordering a rule means editing that method.

**Would you apply it today?** 

No. The four rules are fixed and already tested,
and no requirement asks for a new one.
