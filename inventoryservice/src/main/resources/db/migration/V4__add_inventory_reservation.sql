CREATE TABLE inventory_reservation
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id   CHAR(36)      NOT NULL,
    event_id     BIGINT        NOT NULL,
    ticket_count BIGINT        NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    total_price  DECIMAL(12,2) NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_inventory_reservation_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_inventory_reservation_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT chk_inventory_reservation_ticket_count_positive CHECK (ticket_count > 0),
    CONSTRAINT chk_inventory_reservation_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_inventory_reservation_total_price_non_negative CHECK (total_price >= 0),
    CONSTRAINT chk_inventory_reservation_status CHECK (status IN ('RESERVED', 'RELEASED')),
    INDEX idx_inventory_reservation_event_id (event_id)
);
