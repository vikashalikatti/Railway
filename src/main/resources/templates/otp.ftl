<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Your Railway OTP</title>
</head>
<body style="margin:0; padding:0; background-color:#f2f4f8; font-family:Arial, Helvetica, sans-serif;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f2f4f8; padding:24px 0;">
        <tr>
            <td align="center">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px; background-color:#ffffff; border-radius:8px; overflow:hidden;">
                    <tr>
                        <td style="background-color:#1b3a73; border-bottom:4px solid #f47b20; padding:18px 24px; color:#ffffff; font-size:22px; font-weight:bold;">
                            Railway
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:28px 24px 8px; color:#1c2430; font-size:16px; line-height:1.5;">
                            <p style="margin:0 0 12px;">Hello ${name},</p>
                            <p style="margin:0 0 20px;">${intro}</p>
                        </td>
                    </tr>
                    <tr>
                        <td align="center" style="padding:0 24px;">
                            <div style="display:inline-block; padding:14px 28px; border:2px dashed #1b3a73; border-radius:8px; background-color:#fff1e6; font-size:34px; font-weight:bold; letter-spacing:10px; color:#112650;">
                                ${otp}
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:20px 24px 28px; color:#5d6878; font-size:14px; line-height:1.5;">
                            <p style="margin:0 0 8px;">This code expires in ${minutes} minutes. Don’t share it with anyone.</p>
                            <p style="margin:0;">If you didn’t ask for this, you can ignore this email.</p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
