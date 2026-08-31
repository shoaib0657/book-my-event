package com.shoaib.bookmyevent.bookingservice.service;

import com.shoaib.bookmyevent.bookingservice.request.BookingRequest;
import com.shoaib.bookmyevent.bookingservice.response.BookingResponse;
import org.springframework.stereotype.Service;

@Service
public class BookingService {
    public BookingResponse createBooking(final BookingRequest request) {

        // Check if user exists
        // Check if there is enough inventory
        // -- get event information to also get Venue information
        // create booking
        // send booking to Order Service on a Kafka Topic

        return BookingResponse.builder().build();
    }
}
