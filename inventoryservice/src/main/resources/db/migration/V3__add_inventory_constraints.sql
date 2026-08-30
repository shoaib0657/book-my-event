ALTER TABLE venue
    ADD CONSTRAINT chk_venue_total_capacity_positive
        CHECK (total_capacity > 0);

ALTER TABLE event
    ADD CONSTRAINT chk_event_total_capacity_positive
        CHECK (total_capacity > 0),
    ADD CONSTRAINT chk_event_remaining_capacity_non_negative
        CHECK (left_capacity >= 0),
    ADD CONSTRAINT chk_event_remaining_within_total
        CHECK (left_capacity <= total_capacity),
    ADD CONSTRAINT chk_event_ticket_price_non_negative
        CHECK (ticket_price >= 0);
