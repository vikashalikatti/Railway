package com.project.railway.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import com.project.railway.dto.Admin;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import io.jsonwebtoken.io.IOException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

	@Autowired
	private JavaMailSender javaMailSender;

	@Autowired
	private Configuration configuration;

	public boolean sendInfoEmail(Admin admin) throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, java.io.IOException {
		try {
			MimeMessage mimeMessage = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

			helper.setFrom("Railway <gangsteryt111@gmail.com>");
			helper.setTo(admin.getEmail());
			helper.setSubject("Login Alert");

			// Include IP and location details in the email content
			String infoContent = getInfoContent();

			if (configuration instanceof freemarker.template.Configuration) {
				freemarker.template.Configuration freemarkerConfiguration = (freemarker.template.Configuration) configuration;
				Template template = freemarkerConfiguration.getTemplate("admin.ftl");

				Map<String, Object> model = new HashMap<>();
				model.put("admin", admin);
				model.put("email", admin.getEmail());
				model.put("infoContent", infoContent);
				System.out.println(infoContent+"-------------------->");
				String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

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

	private String getInfoContent() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String currentTime = dateFormat.format(new Date(System.currentTimeMillis()));

		String localHostName = getLocalHostName();
		String ipAddress = getIpAddress();

		return "Time: " + currentTime + "\nLocation: " + localHostName + "\nIP Address: " + ipAddress;
	}

	private String getLocalHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return "N/A";
		}
	}

	private String getIpAddress() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return "N/A";
		}
	}
}
