package com.project.railway.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.project.railway.dto.Coach;
import com.project.railway.dto.CoachSeat;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.Seat_type;
import com.project.railway.repository.CoachSeat_Repository;
import com.project.railway.repository.Coach_Repository;

/**
 * Runs once at startup. Coaches made by the old "total seats" step have no CoachSeat
 * rows, so every class showed 0 free seats (waitlist only). This creates their seats
 * from the coach layout and renames them to the new style ("SLEEPER_CLASS1" -> "S1").
 * Coaches that already have seats are never touched, so running it again does nothing.
 */
@Component
public class LegacyCoachUpgrade implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LegacyCoachUpgrade.class);
	private static final Pattern OLD_NUMBER = Pattern.compile("^([A-Z0-9_]+?)(\\d+)$");

	@Autowired
	private Coach_Repository coachRepository;
	@Autowired
	private CoachSeat_Repository coachSeatRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			Integer created = transactionTemplate.execute(status -> upgrade());
			if (created != null && created > 0) {
				log.info("Created {} seats for coaches set up before seat allocation existed", created);
			}
		} catch (RuntimeException e) {
			// Never stop the app from starting; the admin can still use "Change coaches"
			log.error("Could not create seats for old coaches", e);
		}
	}

	private int upgrade() {
		List<Coach> coaches = coachRepository.findCoachesWithoutSeats();
		if (coaches.isEmpty()) {
			return 0;
		}
		List<CoachSeat> seats = new ArrayList<>();
		for (Coach coach : coaches) {
			Seat_type type;
			try {
				type = Seat_type.valueOf(coach.getCoachType());
			} catch (IllegalArgumentException | NullPointerException e) {
				log.warn("Skipping coach {}: unknown class {}", coach.getId(), coach.getCoachType());
				continue;
			}

			// "SLEEPER_CLASS3" -> "S3"
			String number = coach.getCoachNumber() == null ? "" : coach.getCoachNumber();
			Matcher m = OLD_NUMBER.matcher(number);
			if (m.matches() && m.group(1).equals(type.name())) {
				coach.setCoachNumber(CoachLayout.prefix(type) + m.group(2));
			}

			int count = coach.getNumberOfSeats();
			if (count <= 0 || count > CoachLayout.capacity(type)) {
				count = CoachLayout.capacity(type);
				coach.setNumberOfSeats(count);
			}
			for (int n = 1; n <= count; n++) {
				CoachSeat seat = new CoachSeat();
				seat.setCoach(coach);
				seat.setSeatNumber(n);
				seat.setBerthType(CoachLayout.berthType(type, n));
				seats.add(seat);
			}
		}
		coachRepository.saveAll(coaches);
		coachSeatRepository.saveAll(seats);

		Map<Integer, Integer> perTrain = new HashMap<>();
		coaches.forEach(c -> perTrain.merge(c.getTrain() == null ? 0 : c.getTrain().getTrainNumber(), 1, Integer::sum));
		log.info("Gave seats to {} old coaches on trains {}", coaches.size(), perTrain.keySet());
		return seats.size();
	}
}
