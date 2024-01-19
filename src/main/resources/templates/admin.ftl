<!DOCTYPE html>
<html>
<head>
    <title>Railway Admin - Login Alert</title>
</head>
<body style="font-family: 'Arial', sans-serif; background-color: #f0f0f0; margin: 0; padding: 0;">

    <div style="background-color: #183c5f; color: #fff; padding: 20px; text-align: center;">
        <h1 style="margin: 0; font-size: 36px;">Railway Admin</h1>
    </div>

    <div style="max-width: 600px; margin: 20px auto; background-color: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);">
        <h2 style="color: #183c5f; font-size: 24px;">Login Alert</h2>
        <p style="color: #555; font-size: 16px;">Hello ${admin.name},</p>
        
        <p style="color: #555; font-size: 16px;">Your Railway Admin account was just accessed.</p>

        <p style="color: #888; font-size: 14px;">Login Details:</p>
        <ul style="list-style-type: none; padding: 0;">
            <li style="margin-bottom: 10px; color: #183c5f;"><strong>Email:</strong> ${admin.email}</li>
            <li style="margin-bottom: 10px; color: #183c5f;"><strong>Details:</strong> ${infoContent}</li>
            <!-- Add more details as needed -->
        </ul>
        
        <p style="color: #555; font-size: 16px;">If this was not you, please contact us immediately.</p>
        
        <p style="color: #888; font-size: 14px;">Best regards,<br/>
           The Railway Team</p>
    </div>

    <div style="background-color: #183c5f; color: #fff; padding: 10px; text-align: center; position: fixed; bottom: 0; width: 100%;">
        &copy; 2024 Railway Admin. All rights reserved.
    </div>
</body>
</html>
