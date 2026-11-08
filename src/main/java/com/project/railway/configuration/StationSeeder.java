package com.project.railway.configuration;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.project.railway.dto.Station;
import com.project.railway.dto.StationMaster;
import com.project.railway.repository.StationMaster_Repository;
import com.project.railway.repository.Station_Repository;
import com.project.railway.service.StationDirectory;

/**
 * At startup:
 * 1. If the station directory is empty, fills it with a starter list of stations.
 *    The admin can add more from the admin page (POST /admin/stations).
 *    Stations from the starter list that were added before positions existed get their position.
 * 2. Gives a code to existing stops whose name exactly matches a directory station.
 */
@Component
@Order(1)
public class StationSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StationSeeder.class);

	@Autowired
	private StationMaster_Repository masterRepository;
	@Autowired
	private Station_Repository stationRepository;
	@Autowired
	private StationDirectory directory;
	@Autowired
	private TransactionTemplate tx;

	private static final String[][] STARTER = {
			// code, name, city, state, latitude, longitude (station position, for distance estimates)
			{ "SBC", "KSR Bengaluru City Jn", "Bengaluru", "Karnataka", "12.9781", "77.5695" },
			{ "YPR", "Yesvantpur Jn", "Bengaluru", "Karnataka", "13.0237", "77.5502" },
			{ "SMVB", "SMVT Bengaluru", "Bengaluru", "Karnataka", "12.9911", "77.656" },
			{ "BNC", "Bengaluru Cantonment", "Bengaluru", "Karnataka", "12.9936", "77.598" },
			{ "KJM", "Krishnarajapuram", "Bengaluru", "Karnataka", "13.0003", "77.6763" },
			{ "MYS", "Mysuru Jn", "Mysuru", "Karnataka", "12.3163", "76.6455" },
			{ "MYA", "Mandya", "Mandya", "Karnataka", "12.5218", "76.8951" },
			{ "TK", "Tumakuru", "Tumakuru", "Karnataka", "13.3409", "77.101" },
			{ "ASK", "Arsikere Jn", "Arsikere", "Karnataka", "13.3139", "76.2577" },
			{ "HAS", "Hassan Jn", "Hassan", "Karnataka", "13.0072", "76.1004" },
			{ "DVG", "Davangere", "Davangere", "Karnataka", "14.4644", "75.9218" },
			{ "UBL", "SSS Hubballi Jn", "Hubballi", "Karnataka", "15.3466", "75.1424" },
			{ "DWR", "Dharwad", "Dharwad", "Karnataka", "15.4425", "75.0102" },
			{ "BGM", "Belagavi", "Belagavi", "Karnataka", "15.8484", "74.5137" },
			{ "GDG", "Gadag Jn", "Gadag", "Karnataka", "15.4215", "75.6214" },
			{ "HPT", "Hosapete Jn", "Hosapete", "Karnataka", "15.269", "76.388" },
			{ "BAY", "Ballari Jn", "Ballari", "Karnataka", "15.1497", "76.931" },
			{ "RC", "Raichur", "Raichur", "Karnataka", "16.201", "77.356" },
			{ "BJP", "Vijayapura", "Vijayapura", "Karnataka", "16.8306", "75.7178" },
			{ "MAQ", "Mangaluru Central", "Mangaluru", "Karnataka", "12.8655", "74.841" },
			{ "MAJN", "Mangaluru Jn", "Mangaluru", "Karnataka", "12.8675", "74.8705" },
			{ "UD", "Udupi", "Udupi", "Karnataka", "13.329", "74.771" },
			{ "KAWR", "Karwar", "Karwar", "Karnataka", "14.846", "74.146" },
			{ "BWT", "Bangarapet Jn", "Bangarapet", "Karnataka", "12.9887", "78.1756" },
			{ "MAS", "MGR Chennai Central", "Chennai", "Tamil Nadu", "13.0827", "80.275" },
			{ "MS", "Chennai Egmore", "Chennai", "Tamil Nadu", "13.078", "80.261" },
			{ "KPD", "Katpadi Jn", "Vellore", "Tamil Nadu", "12.973", "79.138" },
			{ "JTJ", "Jolarpettai Jn", "Jolarpettai", "Tamil Nadu", "12.57", "78.577" },
			{ "SA", "Salem Jn", "Salem", "Tamil Nadu", "11.6713", "78.1135" },
			{ "ED", "Erode Jn", "Erode", "Tamil Nadu", "11.333", "77.727" },
			{ "CBE", "Coimbatore Jn", "Coimbatore", "Tamil Nadu", "10.996", "76.967" },
			{ "MDU", "Madurai Jn", "Madurai", "Tamil Nadu", "9.919", "78.111" },
			{ "ERS", "Ernakulam Jn", "Kochi", "Kerala", "9.969", "76.291" },
			{ "TVC", "Thiruvananthapuram Central", "Thiruvananthapuram", "Kerala", "8.488", "76.952" },
			{ "SC", "Secunderabad Jn", "Hyderabad", "Telangana", "17.434", "78.502" },
			{ "HYB", "Hyderabad Deccan", "Hyderabad", "Telangana", "17.39", "78.468" },
			{ "KCG", "Kacheguda", "Hyderabad", "Telangana", "17.387", "78.497" },
			{ "BZA", "Vijayawada Jn", "Vijayawada", "Andhra Pradesh", "16.518", "80.619" },
			{ "GTL", "Guntakal Jn", "Guntakal", "Andhra Pradesh", "15.172", "77.377" },
			{ "DMM", "Dharmavaram Jn", "Dharmavaram", "Andhra Pradesh", "14.417", "77.718" },
			{ "TPTY", "Tirupati", "Tirupati", "Andhra Pradesh", "13.628", "79.419" },
			{ "MAO", "Madgaon Jn", "Margao", "Goa", "15.273", "73.967" },
			{ "VSG", "Vasco da Gama", "Vasco da Gama", "Goa", "15.398", "73.813" },
			{ "CSMT", "Mumbai CSMT", "Mumbai", "Maharashtra", "18.94", "72.835" },
			{ "LTT", "Lokmanya Tilak Terminus", "Mumbai", "Maharashtra", "19.069", "72.892" },
			{ "MMCT", "Mumbai Central", "Mumbai", "Maharashtra", "18.97", "72.819" },
			{ "KYN", "Kalyan Jn", "Mumbai", "Maharashtra", "19.236", "73.131" },
			{ "PUNE", "Pune Jn", "Pune", "Maharashtra", "18.529", "73.874" },
			{ "NGP", "Nagpur Jn", "Nagpur", "Maharashtra", "21.152", "79.088" },
			{ "ADI", "Ahmedabad Jn", "Ahmedabad", "Gujarat", "23.026", "72.601" },
			{ "BPL", "Bhopal Jn", "Bhopal", "Madhya Pradesh", "23.267", "77.413" },
			{ "JP", "Jaipur Jn", "Jaipur", "Rajasthan", "26.92", "75.787" },
			{ "NDLS", "New Delhi", "Delhi", "Delhi", "28.643", "77.219" },
			{ "DLI", "Delhi Jn", "Delhi", "Delhi", "28.661", "77.228" },
			{ "NZM", "Hazrat Nizamuddin", "Delhi", "Delhi", "28.588", "77.254" },
			{ "LKO", "Lucknow Charbagh", "Lucknow", "Uttar Pradesh", "26.832", "80.922" },
			{ "PNBE", "Patna Jn", "Patna", "Bihar", "25.603", "85.137" },
			{ "HWH", "Howrah Jn", "Kolkata", "West Bengal", "22.583", "88.342" },
			{ "SDAH", "Sealdah", "Kolkata", "West Bengal", "22.568", "88.371" }, };

	@Override
	public void run(ApplicationArguments args) {
		try {
			tx.executeWithoutResult(status -> {
				if (masterRepository.count() == 0) {
					List<StationMaster> list = new ArrayList<>();
					for (String[] s : STARTER) {
						list.add(new StationMaster(s[0], s[1], s[2], s[3], Double.valueOf(s[4]), Double.valueOf(s[5])));
					}
					masterRepository.saveAll(list);
					log.info("Added {} stations to the station directory", list.size());
				} else {
					int placed = 0;
					for (String[] s : STARTER) {
						StationMaster m = masterRepository.findById(s[0]).orElse(null);
						if (m != null && !m.hasPosition()) {
							m.setLatitude(Double.valueOf(s[4]));
							m.setLongitude(Double.valueOf(s[5]));
							placed++;
						}
					}
					if (placed > 0) {
						log.info("Added map positions to {} stations", placed);
					}
				}
				int linked = 0;
				for (Station stop : stationRepository.findByStationCodeIsNull()) {
					directory.linkCode(stop);
					if (stop.getStationCode() != null) {
						linked++;
					}
				}
				if (linked > 0) {
					log.info("Linked {} existing stops to station codes", linked);
				}
			});
		} catch (RuntimeException e) {
			log.error("Station directory setup failed; live search may be empty until it is fixed", e);
		}
	}
}
