package com.project.railway.helper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import com.opencagedata.jopencage.JOpenCageGeocoder;
import com.opencagedata.jopencage.model.JOpenCageComponents;
import com.opencagedata.jopencage.model.JOpenCageForwardRequest;
import com.opencagedata.jopencage.model.JOpenCageLatLng;
import com.opencagedata.jopencage.model.JOpenCageResponse;
import com.opencagedata.jopencage.model.JOpenCageResult;
import com.project.railway.dto.Admin;

import de.westnordost.osmapi.common.errors.OsmApiException;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

	@Autowired
	private JavaMailSender javaMailSender;

	@Autowired
	private Configuration configuration;

	
	private String getGeolocationDetails(String location) {
		String apiKey = "YOUR_API_KEY";

		JOpenCageGeocoder jOpenCageGeocoder = new JOpenCageGeocoder(apiKey);

		try {
			JOpenCageForwardRequest request = new JOpenCageForwardRequest(location);
			request.setMinConfidence(1);
			request.setNoAnnotations(false);
			request.setNoDedupe(true);

			JOpenCageResponse response = jOpenCageGeocoder.forward(request);

			if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
				JOpenCageResult result = response.getResults().get(0);

				JOpenCageLatLng geometry = result.getGeometry();
				double latitude = result.getGeometry().getLat();
				double longitude = result.getGeometry().getLng();
				JOpenCageComponents components = result.getComponents();

				return "Latitude: " + latitude + "\nLongitude: " + longitude + "\nCity: " + components.getCity()
						+ "\nCountry: " + components.getCountry();
			} else {
				return "No geolocation details found.";
			}
		} catch (OsmApiException e) {
			e.printStackTrace();
			return "Failed to retrieve geolocation details.";
		}
	}

	private String getInfoContent(Admin admin, String location) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		String currentTime = dateFormat.format(new Date(System.currentTimeMillis()));

		String localHostName = getLocalHostName();
		String geolocationDetails = getGeolocationDetails(location);

		return "Time: " + currentTime + "\nLocation: " + localHostName + "\nRequested Location: " + location + "\n"
				+ geolocationDetails;
	}

	public boolean sendInfoEmail(Admin admin, String location)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		try {
			MimeMessage mimeMessage = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

			helper.setFrom("Railway <gangsteryt111@gmail.com>");
			helper.setTo(admin.getEmail());
			helper.setSubject("Login Alert");

			String infoContent = getInfoContent(admin, location);

			if (configuration instanceof freemarker.template.Configuration) {

				freemarker.template.Configuration freemarkerConfiguration = (freemarker.template.Configuration) configuration;
				Template template = freemarkerConfiguration.getTemplate("admin.ftl");

				Map<String, Object> model = new HashMap<>();
				model.put("admin", admin);
				model.put("infoContent", infoContent);
//				System.out.println(infoContent+"-------------------------->");

				String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

				// Send email
				helper.setText(content, true);
				javaMailSender.send(mimeMessage);

				return true;
			} else {
				System.err.println("Error: Configuration is not of type freemarker.template.Configuration");
				return false;
			}
		} catch (MessagingException | IOException | TemplateException e) {
			e.printStackTrace();
			return false;
		}
	}

	private String getLocalHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return "N/A";
		}
	}

	// Rest of the methods (getIpAddress, and other existing methods) remain
	// unchanged...
	
}