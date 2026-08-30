package com.shoaib.bookmyevent.inventoryservice.config;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import com.shoaib.bookmyevent.inventoryservice.entity.Venue;
import com.shoaib.bookmyevent.inventoryservice.repository.EventRepository;
import com.shoaib.bookmyevent.inventoryservice.repository.VenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataSeeder.class);

    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;

    public DevDataSeeder(final VenueRepository venueRepository, final EventRepository eventRepository) {
        this.venueRepository = venueRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        // Seed only a completely empty inventory database. Partial data may be
        // intentional, so it must never be overwritten or completed automatically.
        if (venueRepository.count() > 0 || eventRepository.count() > 0) {
            LOGGER.info("Skipping development inventory seed because inventory data already exists");
            return;
        }

        final Venue oldTrafford = new Venue(null, "Old Trafford", "Manchester, UK", 80_000L);
        final Venue etihadStadium = new Venue(null, "Etihad Stadium", "Manchester, UK", 70_000L);
        venueRepository.saveAll(List.of(oldTrafford, etihadStadium));

        eventRepository.saveAll(List.of(
                new Event(
                        null,
                        "Coldplay",
                        40_000L,
                        40_000L,
                        oldTrafford,
                        new BigDecimal("10.00")),
                new Event(
                        null,
                        "Bruno Mars",
                        30_000L,
                        30_000L,
                        etihadStadium,
                        new BigDecimal("10.00"))));

        LOGGER.info("Seeded 2 venues and 2 events for the dev profile");
    }
}
