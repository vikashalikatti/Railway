package com.project.railway.dto;

import com.project.railway.helper.OtpStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class Otp {
	
	private OtpStatus status;
	private String meaage;
}
