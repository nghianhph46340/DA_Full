package com.example.duanbe.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.duanbe.entity.HoaDon;
import com.example.duanbe.repository.HoaDonRepo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class PaymentEmailService {
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private HoaDonRepo hoaDonRepo;

    @Async("emailTaskExecutor")
    public void sendPaymentSuccessEmailAsync(Integer idHoaDon) {
        try {
            HoaDon hoaDon = hoaDonRepo.findById(idHoaDon).orElse(null);
            if (hoaDon != null && hoaDon.getEmail() != null) {
                sendEmail(hoaDon.getEmail(), hoaDon.getMa_hoa_don());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendEmail(String toEmail, String maHoaDon) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String tenDN = toEmail.split("@")[0];
            helper.setTo(toEmail);
            helper.setSubject("✓ Xác nhận đơn hàng #" + maHoaDon + " - MenWear");

            // Professional HTML Email Template
            String body = "<!DOCTYPE html>"
                    + "<html lang='vi'>"
                    + "<head>"
                    + "<meta charset='UTF-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                    + "<style>"
                    + "body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4; }"
                    + ".email-container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }"
                    + ".header { background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%); padding: 40px 20px; text-align: center; }"
                    + ".logo { width: 180px; height: auto; margin-bottom: 20px; }"
                    + ".header-text { color: #ffffff; font-size: 24px; font-weight: 700; margin: 0; letter-spacing: 2px; }"
                    + ".content { padding: 40px 30px; }"
                    + ".greeting { font-size: 18px; color: #2c3e50; margin-bottom: 20px; font-weight: 600; }"
                    + ".message-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 12px; padding: 25px; margin: 25px 0; box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3); }"
                    + ".order-code-label { color: #ffffff; font-size: 14px; opacity: 0.9; margin-bottom: 8px; }"
                    + ".order-code { color: #ffffff; font-size: 32px; font-weight: 700; letter-spacing: 3px; margin: 0; text-align: center; }"
                    + ".info-card { background-color: #f8f9fa; border-left: 4px solid #667eea; padding: 20px; margin: 20px 0; border-radius: 8px; }"
                    + ".info-title { color: #2c3e50; font-size: 16px; font-weight: 600; margin-bottom: 12px; }"
                    + ".info-text { color: #555555; line-height: 1.8; margin: 8px 0; }"
                    + ".button-container { text-align: center; margin: 30px 0; }"
                    + ".track-button { display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff; padding: 15px 40px; text-decoration: none; border-radius: 50px; font-weight: 600; font-size: 16px; box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4); transition: transform 0.3s ease; }"
                    + ".track-button:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5); }"
                    + ".support-box { background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 8px; padding: 15px; margin: 25px 0; }"
                    + ".support-text { color: #856404; margin: 5px 0; font-size: 14px; }"
                    + ".footer { background-color: #2c3e50; color: #ffffff; padding: 30px; text-align: center; }"
                    + ".footer-text { margin: 8px 0; font-size: 14px; opacity: 0.9; }"
                    + ".social-links { margin: 20px 0; }"
                    + ".social-link { display: inline-block; margin: 0 10px; color: #ffffff; text-decoration: none; }"
                    + ".divider { height: 1px; background: linear-gradient(90deg, transparent, #ddd, transparent); margin: 30px 0; }"
                    + "</style>"
                    + "</head>"
                    + "<body>"
                    + "<div class='email-container'>"

                    // Header with Logo
                    + "<div class='header'>"
                    + "<img src='https://res.cloudinary.com/dryt7bnjl/image/upload/v1768019485/anhLogoMenWear-1-removebg-preview_zoeaui.png' alt='MenWear Logo' class='logo'>"
                    // + "<h1 class='header-text'>MENWEAR</h1>"
                    + "</div>"

                    // Main Content
                    + "<div class='content'>"
                    + "<p class='greeting'>Xin chào " + tenDN + ",</p>"
                    + "<p class='info-text'>Cảm ơn bạn đã tin tưởng và mua sắm tại <strong>MenWear</strong> - Thương hiệu áo sơ mi nam cao cấp.</p>"

                    // Order Code Box
                    + "<div class='message-box'>"
                    + "<div class='order-code-label'>Mã đơn hàng của bạn</div>"
                    + "<h2 class='order-code'>" + maHoaDon + "</h2>"
                    + "</div>"

                    // Order Info
                    + "<div class='info-card'>"
                    + "<div class='info-title'>📦 Thông tin đơn hàng</div>"
                    + "<p class='info-text'>✓ Đơn hàng của bạn đã được tiếp nhận và đang được xử lý</p>"
                    + "<p class='info-text'>✓ Bạn sẽ nhận được email xác nhận khi đơn hàng được giao</p>"
                    + "<p class='info-text'>✓ Thời gian giao hàng dự kiến: 2-3 ngày làm việc</p>"
                    + "</div>"

                    // Track Order Button
                    + "<div class='button-container'>"
                    + "<a href='http://localhost:5173/tracuudonhang-banhang?code=" + maHoaDon
                    + "' class='track-button'>Theo dõi đơn hàng</a>"
                    + "</div>"

                    + "<div class='divider'></div>"

                    // Support Info
                    + "<div class='support-box'>"
                    + "<p class='support-text'><strong>💬 Cần hỗ trợ?</strong></p>"
                    + "<p class='support-text'>Nếu bạn có bất kỳ thắc mắc nào, vui lòng liên hệ:</p>"
                    + "<p class='support-text'>📞 Hotline: 1900-xxxx | 📧 Email: support@menwear.vn</p>"
                    + "</div>"

                    // Closing
                    + "<p class='info-text' style='margin-top: 30px;'>Trân trọng,</p>"
                    + "<p class='info-text'><strong>Đội ngũ MenWear</strong></p>"
                    + "</div>"

                    // Footer
                    + "<div class='footer'>"
                    + "<p class='footer-text'><strong>MENWEAR</strong> - Phong cách lịch lãm, đẳng cấp quý ông</p>"
                    + "<div class='social-links'>"
                    + "<a href='#' class='social-link'>Facebook</a> | "
                    + "<a href='#' class='social-link'>Instagram</a> | "
                    + "<a href='#' class='social-link'>Zalo</a>"
                    + "</div>"
                    + "<p class='footer-text'>© 2024 MenWear. All rights reserved.</p>"
                    + "<p class='footer-text' style='font-size: 12px; opacity: 0.7;'>Email này được gửi tự động, vui lòng không trả lời.</p>"
                    + "</div>"

                    + "</div>"
                    + "</body>"
                    + "</html>";

            helper.setText(body, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
