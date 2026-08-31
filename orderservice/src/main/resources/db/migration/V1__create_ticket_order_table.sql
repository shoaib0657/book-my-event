CREATE TABLE ticket_order
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id   CHAR(36)       NOT NULL,
    customer_id  BIGINT         NOT NULL,
    event_id     BIGINT         NOT NULL,
    ticket_count BIGINT         NOT NULL,
    total_price  DECIMAL(12, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_ticket_order_booking_id UNIQUE (booking_id),
    CONSTRAINT chk_ticket_order_customer_id_positive CHECK (customer_id > 0),
    CONSTRAINT chk_ticket_order_event_id_positive CHECK (event_id > 0),
    CONSTRAINT chk_ticket_order_ticket_count_positive CHECK (ticket_count > 0),
    CONSTRAINT chk_ticket_order_total_price_non_negative CHECK (total_price >= 0)
);
