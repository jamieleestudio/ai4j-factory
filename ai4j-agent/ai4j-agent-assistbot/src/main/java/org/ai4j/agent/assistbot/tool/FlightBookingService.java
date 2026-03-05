package org.ai4j.agent.assistbot.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.function.Function;

public class FlightBookingService implements Function<FlightBookingService.BookingRequest, FlightBookingService.BookingResponse> {

    private static final Logger logger = LoggerFactory.getLogger(FlightBookingService.class);

    public record BookingRequest(String from, String to, String date) {}
    public record BookingResponse(String bookingId, String status, String message) {}

    @Override
    public BookingResponse apply(BookingRequest request) {
        logger.info("Booking flight request: {}", request);
        
        // Mock logic
        String bookingId = "FL-" + new Random().nextInt(10000);
        String message = String.format("Flight booked from %s to %s on %s", 
                request.from(), request.to(), request.date());
        
        return new BookingResponse(bookingId, "CONFIRMED", message);
    }
}
