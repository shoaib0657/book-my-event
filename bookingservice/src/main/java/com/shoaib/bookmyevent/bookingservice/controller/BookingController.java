package com.shoaib.bookmyevent.bookingservice.controller;

import com.shoaib.bookmyevent.bookingservice.request.BookingRequest;
import com.shoaib.bookmyevent.bookingservice.response.BookingResponse;
import com.shoaib.bookmyevent.bookingservice.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Customer booking operations")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(final BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Create a booking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking created"),
            @ApiResponse(responseCode = "400", description = "Booking request is invalid"),
            @ApiResponse(responseCode = "404", description = "Customer or event was not found"),
            @ApiResponse(responseCode = "409", description = "Requested tickets are unavailable"),
            @ApiResponse(responseCode = "503", description = "A required downstream service is unavailable")
    })
    public BookingResponse createBooking(@Valid @RequestBody final BookingRequest request) {
        return bookingService.createBooking(request);
    }
}
