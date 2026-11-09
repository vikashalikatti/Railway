<#ftl output_format="HTML">
<#-- Ticket email: booked, waitlist confirmed, cancelled, chart prepared. Model: t (TicketMailer.Ticket), title, intro. HTML output format escapes every value. -->
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${title} – PNR ${t.pnr()?c}</title>
</head>
<body style="margin:0; padding:0; background-color:#f2f4f8; font-family:Arial, Helvetica, sans-serif; color:#1c2430;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f2f4f8; padding:24px 0;">
<tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:640px; background-color:#ffffff; border-radius:8px; overflow:hidden;">

    <tr>
        <td style="background-color:#1b3a73; border-bottom:4px solid #f47b20; padding:16px 24px; color:#ffffff;">
            <span style="font-size:20px; font-weight:bold;">Railway</span>
            <span style="float:right; font-size:13px; line-height:26px; color:#c9d4ea;">Electronic reservation slip</span>
        </td>
    </tr>

    <tr>
        <td style="padding:24px 24px 4px;">
            <p style="margin:0 0 4px; font-size:22px; font-weight:bold; color:#112650;">${title}</p>
            <p style="margin:0 0 4px; font-size:15px; line-height:1.5;">Hello ${t.customerName()!"there"},</p>
            <p style="margin:0; font-size:15px; line-height:1.5; color:#3d4757;">${intro}</p>
        </td>
    </tr>

    <#-- PNR and train -->
    <tr>
        <td style="padding:20px 24px 0;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #d5dbe6; border-radius:6px;">
                <tr>
                    <td style="padding:12px 16px; background-color:#fff1e6; border-bottom:1px solid #d5dbe6;">
                        <span style="font-size:12px; color:#5d6878;">PNR</span><br>
                        <span style="font-size:24px; font-weight:bold; letter-spacing:2px; color:#112650;">${t.pnr()?c}</span>
                    </td>
                    <td align="right" style="padding:12px 16px; background-color:#fff1e6; border-bottom:1px solid #d5dbe6;">
                        <span style="font-size:12px; color:#5d6878;">Status</span><br>
                        <span style="font-size:16px; font-weight:bold; color:<#if t.status() == "Confirmed">#1e7b45<#elseif t.status() == "Cancelled">#b42318<#else>#a15c07</#if>;">${t.status()}</span>
                    </td>
                </tr>
                <tr>
                    <td colspan="2" style="padding:14px 16px 4px; font-size:16px; font-weight:bold;">
                        ${t.trainNumber()?c} ${t.trainName()!""}
                    </td>
                </tr>
                <tr>
                    <td colspan="2" style="padding:4px 16px 14px;">
                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                            <tr>
                                <td width="42%" style="vertical-align:top;">
                                    <span style="font-size:22px; font-weight:bold;">${t.departs()}</span><br>
                                    <span style="font-size:14px;">${t.fromStation()}</span>
                                </td>
                                <td width="16%" align="center" style="vertical-align:middle; color:#8a94a6; font-size:13px;">
                                    ${t.distance()}<br>&#8594;
                                </td>
                                <td width="42%" align="right" style="vertical-align:top;">
                                    <span style="font-size:22px; font-weight:bold;">${t.arrives()}</span><br>
                                    <span style="font-size:14px;">${t.toStation()}</span>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td colspan="2" style="padding:0 16px 14px;">
                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:13px;">
                            <tr>
                                <td style="padding:6px 0; color:#5d6878;">Journey date</td>
                                <td style="padding:6px 0; font-weight:bold;">${t.journeyDate()}</td>
                                <td style="padding:6px 0; color:#5d6878;">Class</td>
                                <td style="padding:6px 0; font-weight:bold;">${t.classLabel()}</td>
                            </tr>
                            <tr>
                                <td style="padding:6px 0; color:#5d6878;">Quota</td>
                                <td style="padding:6px 0; font-weight:bold;">${t.quotaLabel()}</td>
                                <td style="padding:6px 0; color:#5d6878;">Total fare</td>
                                <td style="padding:6px 0; font-weight:bold;">${t.totalFare()}</td>
                            </tr>
                            <#if t.bookedAt()?has_content>
                            <tr>
                                <td style="padding:6px 0; color:#5d6878;">Booked on</td>
                                <td colspan="3" style="padding:6px 0;">${t.bookedAt()}</td>
                            </tr>
                            </#if>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
    </tr>

    <#-- Passengers -->
    <tr>
        <td style="padding:20px 24px 0;">
            <p style="margin:0 0 8px; font-size:15px; font-weight:bold; color:#112650;">Passengers</p>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:13px; border:1px solid #d5dbe6; border-radius:6px;">
                <tr style="background-color:#f2f4f8;">
                    <td style="padding:8px 10px; font-weight:bold;">#</td>
                    <td style="padding:8px 10px; font-weight:bold;">Name</td>
                    <td style="padding:8px 10px; font-weight:bold;">Age</td>
                    <td style="padding:8px 10px; font-weight:bold;">Booking status</td>
                    <td style="padding:8px 10px; font-weight:bold;">Current status</td>
                </tr>
                <#list t.passengers() as p>
                <tr>
                    <td style="padding:8px 10px; border-top:1px solid #e6eaf1;">${p.no()}</td>
                    <td style="padding:8px 10px; border-top:1px solid #e6eaf1;">${p.name()}<br><span style="color:#8a94a6;">${p.gender()!""}</span></td>
                    <td style="padding:8px 10px; border-top:1px solid #e6eaf1;">${p.age()}</td>
                    <td style="padding:8px 10px; border-top:1px solid #e6eaf1; color:#5d6878;">${p.bookingStatus()}</td>
                    <td style="padding:8px 10px; border-top:1px solid #e6eaf1; font-weight:bold; color:<#if p.confirmed()>#1e7b45<#elseif p.cancelled()>#b42318<#else>#a15c07</#if>;">${p.currentStatus()}</td>
                </tr>
                </#list>
            </table>
        </td>
    </tr>

    <tr>
        <td style="padding:20px 24px 28px; font-size:13px; line-height:1.6; color:#5d6878;">
            <p style="margin:0 0 6px;">Carry a photo ID (Aadhaar, PAN, driving licence, passport or voter ID) when you travel. It must match a passenger on this ticket.</p>
            <#if t.event() != "CHART_PREPARED">
            <p style="margin:0 0 6px;">The chart is prepared 4 hours before departure. Waitlisted passengers who aren't confirmed by then don't travel; you'll get an email with the final status.</p>
            </#if>
            <p style="margin:0;">You can check this PNR and cancel passengers under My bookings.</p>
        </td>
    </tr>
</table>
</td></tr>
</table>
</body>
</html>
