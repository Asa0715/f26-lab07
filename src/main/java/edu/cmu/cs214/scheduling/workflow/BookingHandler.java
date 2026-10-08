package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * The type-specific half of each workflow operation. {@link BookingWorkflow}
 * runs the shared checks and hands the rest to the handler for the booking's type.
 */
interface BookingHandler {

    String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    /** Validates and writes a request whose room is already known to exist. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a booking that exists and is not yet cancelled. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes for an existing booking, in dollars. */
    double priceOf(Booking booking);

    /** A one-line summary of an existing booking. */
    String describe(Booking booking, String roomName);

    static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }
}
