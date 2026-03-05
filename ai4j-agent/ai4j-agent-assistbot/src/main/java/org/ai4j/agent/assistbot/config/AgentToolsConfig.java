package org.ai4j.agent.assistbot.config;

import org.ai4j.agent.assistbot.tool.FlightBookingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class AgentToolsConfig {

    @Bean
    @Description("Book a flight for a user given origin, destination and date")
    public Function<FlightBookingService.BookingRequest, FlightBookingService.BookingResponse> flightBookingService() {
        return new FlightBookingService();
    }
}
