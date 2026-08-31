DELETE FROM inventory_reservation;
DELETE FROM event;
DELETE FROM venue;

INSERT INTO venue (id, name, address, total_capacity)
VALUES (1, 'Old Trafford', 'Manchester, UK', 80000),
       (2, 'Etihad Stadium', 'Manchester, UK', 70000);

INSERT INTO event (id, name, venue_id, total_capacity, left_capacity, ticket_price)
VALUES (3, 'Coldplay', 1, 40000, 40000, 10.00),
       (4, 'Bruno Mars', 2, 30000, 30000, 10.00);
