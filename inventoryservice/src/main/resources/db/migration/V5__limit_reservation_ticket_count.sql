ALTER TABLE inventory_reservation
    ADD CONSTRAINT chk_inventory_reservation_ticket_count_maximum CHECK (ticket_count <= 100);
