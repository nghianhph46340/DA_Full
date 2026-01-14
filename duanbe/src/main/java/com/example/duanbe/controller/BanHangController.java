package com.example.duanbe.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.duanbe.config.UpdateAll;
import com.example.duanbe.entity.ChiTietKhuyenMai;
import com.example.duanbe.entity.ChiTietSanPham;
import com.example.duanbe.entity.HoaDon;
import com.example.duanbe.entity.HoaDonChiTiet;
import com.example.duanbe.entity.KhachHang;
import com.example.duanbe.entity.TheoDoiDonHang;
import com.example.duanbe.entity.Voucher;
import com.example.duanbe.repository.ChiTietKhuyenMaiRepo;
import com.example.duanbe.repository.ChiTietSanPhamRepo;
import com.example.duanbe.repository.HoaDonChiTietRepo;
import com.example.duanbe.repository.HoaDonRepo;
import com.example.duanbe.repository.KhachHangRepo;
import com.example.duanbe.repository.TheoDoiDonHangRepo;
import com.example.duanbe.repository.VoucherRepository;
import com.example.duanbe.response.ChiTietSanPhamView;
import com.example.duanbe.response.HoaDonChiTietResponse;
import com.example.duanbe.response.HoaDonResponse;
import com.example.duanbe.response.VoucherBHResponse;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@RestController
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = { RequestMethod.GET, RequestMethod.POST,
    RequestMethod.PUT, RequestMethod.DELETE })
@RequestMapping("/banhang")

public class BanHangController {
  @Autowired
  private HoaDonRepo hoaDonRepo;

  @Autowired
  private ChiTietSanPhamRepo chiTietSanPhamRepo;

  @Autowired
  private KhachHangRepo khachHangRepo;

  @Autowired
  private VoucherRepository voucherRepository;
  @Autowired
  private HoaDonChiTietRepo hoaDonChiTietRepo;

  @Autowired
  private TheoDoiDonHangRepo theoDoiDonHangRepo;

  @Autowired
  private ChiTietKhuyenMaiRepo chiTietKhuyenMaiRepo;

  @Autowired
  private UpdateAll updateAll;
  @Autowired
  private JavaMailSender mailSender;

  @PostMapping("/addKhHD")
  public ResponseEntity<?> addKhHd(
      @RequestParam(value = "idKH", required = false) String idKHStr,
      @RequestParam("idHD") Integer idHD,
      @RequestParam("diaChi") String diaChi,
      @RequestParam("tenKhachHang") String tenKhachHang,
      @RequestParam("soDienThoai") String soDienThoai,
      @RequestParam("email") String email) {
    try {
      System.out.println("=== API addKhHD được gọi ===");
      System.out.println("idKH: " + idKHStr);
      System.out.println("idHD: " + idHD);
      System.out.println("tenKhachHang: " + tenKhachHang);
      System.out.println("soDienThoai: " + soDienThoai);
      System.out.println("email: " + email);
      System.out.println("diaChi: " + diaChi);

      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại"));

      if (idKHStr != null && !idKHStr.equals("null") && !idKHStr.isEmpty()) {
        try {
          Integer idKH = Integer.valueOf(idKHStr);
          KhachHang khachHang = khachHangRepo.findById(idKH)
              .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại"));
          hoaDon.setKhachHang(khachHang);
          hoaDon.setHo_ten(khachHang.getHoTen());
          hoaDon.setSdt(khachHang.getSoDienThoai());
          hoaDon.setDia_chi(diaChi);
          hoaDon.setEmail(khachHang.getEmail());
          System.out.println("→ Lưu KHÁCH CÓ TK");
        } catch (NumberFormatException ex) {
          // Nếu idKH không phải là số, coi như nhập khách hàng mới
          hoaDon.setKhachHang(null);
          hoaDon.setHo_ten(tenKhachHang);
          hoaDon.setSdt(soDienThoai);
          hoaDon.setDia_chi(diaChi);
          hoaDon.setEmail(email);
          System.out.println("→ Lưu KHÁCH LẺ (idKH không parse được)");
        }
      } else {
        hoaDon.setKhachHang(null);
        hoaDon.setHo_ten(tenKhachHang);
        hoaDon.setSdt(soDienThoai);
        hoaDon.setDia_chi(diaChi);
        hoaDon.setEmail(email);
        System.out.println("→ Lưu KHÁCH LẺ (idKH = null)");
      }

      hoaDonRepo.save(hoaDon);
      System.out.println("✅ Đã lưu hóa đơn vào DB");

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      System.err.println("❌ Lỗi: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi cập nhật khách hàng cho hóa đơn: " + e.getMessage());
    }
  }

  // ✅ NEW: Endpoint riêng để update thông tin khách hàng vào hóa đơn
  @PostMapping("/updateCustomerInfo")
  public ResponseEntity<?> updateCustomerInfo(
      @RequestParam("idHD") Integer idHD,
      @RequestParam("tenKhachHang") String tenKhachHang,
      @RequestParam("soDienThoai") String soDienThoai,
      @RequestParam(value = "email", required = false) String email,
      @RequestParam(value = "diaChi", required = false) String diaChi) {
    try {
      System.out.println("=== API updateCustomerInfo được gọi ===");
      System.out.println("idHD: " + idHD);
      System.out.println("tenKhachHang: " + tenKhachHang);
      System.out.println("soDienThoai: " + soDienThoai);
      System.out.println("email: " + email);
      System.out.println("diaChi: " + diaChi);

      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại"));

      // ✅ Lưu thông tin khách lẻ
      hoaDon.setKhachHang(null); // id_khach_hang = NULL
      hoaDon.setHo_ten(tenKhachHang);
      hoaDon.setSdt(soDienThoai);
      hoaDon.setEmail(email != null && !email.isEmpty() ? email : null);
      hoaDon.setDia_chi(diaChi != null && !diaChi.isEmpty() ? diaChi : null);

      hoaDonRepo.save(hoaDon);
      System.out.println("✅ ĐÃ LƯU THÔNG TIN KHÁCH HÀNG VÀO DB");

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      System.err.println("❌ Lỗi updateCustomerInfo: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi: " + e.getMessage());
    }
  }

  /**
   * ✅ Cập nhật trạng thái đơn hàng sau thanh toán
   * PUT method vì đây là update operation
   */
  @PutMapping("/trangThaiDonHang")
  public ResponseEntity<?> trangThaiDonHang(@RequestParam("idHD") Integer idHD) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại"));

      // ✅ LOGIC MỚI: Set trạng thái dựa trên loại hóa đơn và phương thức nhận
      String loaiHoaDon = hoaDon.getLoai_hoa_don();
      String phuongThucNhanHang = hoaDon.getPhuong_thuc_nhan_hang();
      String trangThaiMoi = "";

      if ("Offline".equals(loaiHoaDon)) {
        // Offline - Thanh toán tại quầy
        if ("Nhận tại cửa hàng".equals(phuongThucNhanHang)) {
          // Trường hợp 1: Offline + Nhận tại cửa hàng
          // → Đã thanh toán + Đã nhận hàng → HOÀN THÀNH
          trangThaiMoi = "Hoàn thành";
          hoaDon.setTrang_thai(trangThaiMoi);
          TheoDoiDonHang tdhd = theoDoiDonHangRepo.findByIdHoaDon(idHD).get(0);
          tdhd.setTrang_thai(trangThaiMoi);
          theoDoiDonHangRepo.save(tdhd);
        } else {
          // Trường hợp 2: Offline + Giao hàng
          // → Đã thanh toán nhưng CHƯA giao → ĐÃ XÁC NHẬN
          trangThaiMoi = "Hoàn thành";
          hoaDon.setTrang_thai(trangThaiMoi);
          trangThaiMoi = "Đã xác nhận";
          // TheoDoiDonHang tdhd = theoDoiDonHangRepo.findByIdHoaDon(idHD).get(0);
          // tdhd.setTrang_thai(trangThaiMoi);
          // theoDoiDonHangRepo.save(tdhd);
        }
      } else {
        // Trường hợp 3: Online (đã được xử lý trong callback ZaloPay/PayOS)
        // → Đã thanh toán online nhưng CHƯA giao → ĐÃ XÁC NHẬN
        trangThaiMoi = "Đã xác nhận";
        hoaDon.setTrang_thai(trangThaiMoi);
      }

      hoaDonRepo.save(hoaDon);

      // ✅ THÊM: Insert/Update bảng theo_doi_don_hang
      if ("Đã xác nhận".equals(trangThaiMoi)) {
        TheoDoiDonHang tracking = new TheoDoiDonHang();
        tracking.setHoaDon(hoaDon);
        tracking.setTrang_thai("Đã xác nhận");
        tracking.setNgay_chuyen(LocalDateTime.now());
        tracking.setNoi_dung_doi("Đơn hàng đã được xác nhận và chờ giao hàng");
        theoDoiDonHangRepo.save(tracking);
        System.out.println("✅ Đã tạo theo dõi đơn hàng: Đã xác nhận");
      }

      // ✅ Trả về HoaDonResponse đầy đủ
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi cập nhật trạng thái hóa đơn: " + e.getMessage());
    }
  }

  @PostMapping("/removeCustomerFromInvoice")
  public ResponseEntity<?> removeCustomerFromInvoice(@RequestParam("idHD") Integer idHD) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại"));

      // ✅ LƯU LẠI phương thức nhận hàng hiện tại
      String currentPhuongThuc = hoaDon.getPhuong_thuc_nhan_hang();

      // Reset thông tin khách hàng về khách lẻ
      hoaDon.setKhachHang(null);
      hoaDon.setHo_ten("Khách lẻ");
      hoaDon.setSdt(null);
      hoaDon.setDia_chi(null);
      hoaDon.setEmail(null);

      // ✅ GIỮ NGUYÊN phương thức nhận hàng (KHÔNG reset về "Nhận tại cửa hàng")
      // Chỉ reset phí ship về 0 nếu phương thức là "Nhận tại cửa hàng"
      // if ("Nhận tại cửa hàng".equals(currentPhuongThuc)) {
      hoaDon.setPhi_van_chuyen(BigDecimal.ZERO);
      // }
      // Nếu là "Giao hàng", GIỮ NGUYÊN cả phương thức và phí ship

      hoaDonRepo.save(hoaDon);

      // Cập nhật lại tổng tiền sau khi bỏ phí vận chuyển
      updateTongTienHoaDon(idHD);

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi bỏ chọn khách hàng: " + e.getMessage());
    }
  }

  @PostMapping("/setTrangThaiNhanHang")
  public ResponseEntity<?> setTrangThaiNhanHang(
      @RequestParam("idHD") Integer idHD,
      @RequestParam("phuongThucNhanHang") String ptnh,
      @RequestParam("phiVanChuyen") BigDecimal pvc) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với id: " + idHD));

      hoaDon.setPhuong_thuc_nhan_hang(ptnh);

      if ("Giao hàng".equalsIgnoreCase(ptnh)) {
        hoaDon.setPhi_van_chuyen(pvc);
      } else if ("Nhận tại cửa hàng".equalsIgnoreCase(ptnh)) {
        hoaDon.setDia_chi(null);
        hoaDon.setPhi_van_chuyen(BigDecimal.ZERO);
      }

      // ✅ LƯU PHÍ SHIP
      hoaDonRepo.save(hoaDon);

      // ✅ GỌI updateTongTienHoaDon để tính lại ĐÚNG
      // (tongTienTruocGiam = CHỈ sản phẩm, KHÔNG cộng ship)
      updateTongTienHoaDon(idHD);

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi cập nhật phương thức nhận hàng: " + e.getMessage());
    }
  }

  @GetMapping("/getAllHoaDonCTT")
  public List<HoaDonResponse> getAllHDCTT() {
    return hoaDonRepo.getAllHoaDonCTT();
  }

  @GetMapping("/getHoaDonByIdHoaDon")
  public HoaDonResponse getHoaDonByIdHoaDon(@RequestParam("idHD") Integer idHD) {
    return hoaDonRepo.findHoaDonById(idHD).get(0);
  }

  /**
   * ✅ Tạo hóa đơn mới
   * POST method vì đây là create operation
   */
  @PostMapping("/createHoaDon")
  public ResponseEntity<?> createHoaDon() {
    try {
      // 1. Validate input
      // 3. Create new invoice
      HoaDon newHoaDon = new HoaDon(generateUniqueMaHoaDon());

      // 4. Set default values
      newHoaDon.setTong_tien_truoc_giam(BigDecimal.ZERO);
      newHoaDon.setTong_tien_sau_giam(BigDecimal.ZERO);
      newHoaDon.setPhi_van_chuyen(BigDecimal.ZERO);

      // 5. Save to database
      HoaDon savedHoaDon = hoaDonRepo.save(newHoaDon);
      TheoDoiDonHang theoDoiDonHang = new TheoDoiDonHang();
      theoDoiDonHang.setTrang_thai("Chờ xác nhận");
      theoDoiDonHang.setHoaDon(savedHoaDon);
      theoDoiDonHang.setNgay_chuyen(LocalDateTime.now());
      theoDoiDonHangRepo.save(theoDoiDonHang);
      // 6. Create response DTO
      Map<String, Object> response = new HashMap<>();
      response.put("id_hoa_don", savedHoaDon.getId_hoa_don());
      response.put("ma_hoa_don", savedHoaDon.getMa_hoa_don());
      response.put("ngay_tao", savedHoaDon.getNgay_tao().format(DateTimeFormatter.ISO_DATE_TIME));
      response.put("trang_thai", savedHoaDon.getTrang_thai());

      return ResponseEntity.ok(savedHoaDon);

    } catch (ResponseStatusException e) {
      return ResponseEntity.status(e.getStatusCode()).body(
          Map.of(
              "error", true,
              "message", e.getReason()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(
          Map.of(
              "error", true,
              "message", "Lỗi hệ thống: " + e.getMessage()));
    }
  }

  @DeleteMapping("/deleteHoaDon")
  @Transactional
  public ResponseEntity<?> deleteHoaDon(@RequestParam(value = "idHoaDon") Integer id) {
    try {
      Optional<HoaDon> hoaDonOpt = hoaDonRepo.findById(id);
      if (hoaDonOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("success", false, "message", "Không tìm thấy hóa đơn với ID: " + id));
      }

      HoaDon hoaDon = hoaDonOpt.get();

      // Lưu thông tin trước khi xóa
      List<Map<String, Integer>> productUpdates = new ArrayList();
      for (HoaDonChiTiet chiTiet : hoaDon.getHoaDonChiTietList()) {
        Map<String, Integer> update = new HashMap<>();
        update.put("idCTSP", chiTiet.getChiTietSanPham().getId_chi_tiet_san_pham());
        update.put("soLuong", chiTiet.getSo_luong());
        productUpdates.add(update);
      }

      // Lưu thông tin voucher
      Integer idVoucher = null;
      if (hoaDon.getVoucher() != null && "Đang diễn ra".equalsIgnoreCase(hoaDon.getVoucher().getTrangThai())) {
        idVoucher = hoaDon.getVoucher().getId();
      }

      // Xóa hóa đơn trước (cascade sẽ xóa chi tiết)
      hoaDonRepo.delete(hoaDon);
      hoaDonRepo.flush(); // Đảm bảo xóa được thực thi ngay

      // Cập nhật lại số lượng tồn sản phẩm sau khi xóa
      for (Map<String, Integer> update : productUpdates) {
        ChiTietSanPham ctsp = chiTietSanPhamRepo.findById(update.get("idCTSP")).orElse(null);
        if (ctsp != null) {
          ctsp.setSo_luong(ctsp.getSo_luong() + update.get("soLuong"));
          chiTietSanPhamRepo.save(ctsp);
        }
      }

      // Cập nhật voucher nếu có
      if (idVoucher != null) {
        Voucher voucher = voucherRepository.findById(idVoucher).orElse(null);
        if (voucher != null) {
          voucher.setSoLuong(voucher.getSoLuong() + 1);
          voucherRepository.save(voucher);
        }
      }

      return ResponseEntity
          .ok(Map.of("success", true, "message", "Đã xóa hóa đơn và cập nhật lại tồn kho, voucher nếu có"));
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "message", "Lỗi khi xóa hóa đơn: " + e.getMessage()));
    }
  }

  // ✅ CẬP NHẬT PHÍ VẬN CHUYỂN
  /**
   * ✅ Cập nhật phí vận chuyển
   * Sau khi cập nhật, tính lại tổng tiền và trả về full invoice data
   */
  @PutMapping("/hoa-don/{idHoaDon}/phi-van-chuyen")
  public ResponseEntity<?> updatePhiVanChuyen(
      @PathVariable Integer idHoaDon,
      @RequestParam BigDecimal phiVanChuyen) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHoaDon)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn"));

      hoaDon.setPhi_van_chuyen(phiVanChuyen);
      hoaDonRepo.save(hoaDon);

      System.out.println("✅ Đã cập nhật phí vận chuyển " + phiVanChuyen + " cho hóa đơn " + idHoaDon);

      // Tính lại tổng tiền sau khi cập nhật phí vận chuyển
      updateTongTienHoaDon(idHoaDon);

      // Trả về HoaDonResponse với thông tin đầy đủ
      return ResponseEntity.ok(getFullInvoiceResponse(idHoaDon));
    } catch (Exception e) {
      System.err.println("❌ Lỗi cập nhật phí vận chuyển: " + e.getMessage());
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * ✅ Cập nhật thông tin cơ bản của hóa đơn
   * Chỉ cập nhật: ho_ten, email, sdt, dia_chi, ngay_sua
   * Giữ nguyên: tổng tiền, voucher, trạng thái, sản phẩm, v.v.
   */
  @PutMapping("/updateHoaDon")
  public ResponseEntity<HoaDonResponse> updateHoaDon(@RequestBody HoaDon hoaDonInput) {
    try {
      // 1. Validate: Kiểm tra id_hoa_don có được truyền lên không
      if (hoaDonInput.getId_hoa_don() == null) {
        return ResponseEntity.badRequest().build();
      }

      // 2. Tìm hóa đơn hiện tại trong database
      HoaDon existingHoaDon = hoaDonRepo.findById(hoaDonInput.getId_hoa_don())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
              "Không tìm thấy hóa đơn với ID: " + hoaDonInput.getId_hoa_don()));

      // 3. Chỉ cập nhật các trường thông tin cơ bản
      existingHoaDon.setHo_ten(hoaDonInput.getHo_ten());
      existingHoaDon.setEmail(hoaDonInput.getEmail());
      existingHoaDon.setSdt(hoaDonInput.getSdt());
      existingHoaDon.setDia_chi(hoaDonInput.getDia_chi());

      // 4. Tự động set ngày sửa = thời điểm hiện tại
      existingHoaDon.setNgay_sua(LocalDateTime.now());

      // 5. Lưu hóa đơn đã cập nhật
      hoaDonRepo.save(existingHoaDon);

      System.out.println("✅ Đã cập nhật thông tin hóa đơn ID: " + existingHoaDon.getId_hoa_don());

      // 6. Trả về HoaDonResponse với thông tin đầy đủ
      return ResponseEntity.ok(getFullInvoiceResponse(existingHoaDon.getId_hoa_don()));

    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      System.err.println("❌ Lỗi khi cập nhật hóa đơn: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GetMapping("/getSPHD")
  public List<HoaDonChiTietResponse> getAllSPHD(@RequestParam(value = "idHoaDon") Integer idHD) {
    return hoaDonChiTietRepo.getSPGH(idHD);
  }

  @PostMapping("/themSPHDMoi")
  public ResponseEntity<?> themSPHDMoi(
      @RequestParam("idHoaDon") Integer idHD,
      @RequestParam("idCTSP") Integer idCTSP,
      @RequestParam("soLuong") Integer soLuongInput) {
    try {
      // Kiểm tra hóa đơn
      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));

      // Kiểm tra sản phẩm
      ChiTietSanPham ctsp = chiTietSanPhamRepo.findById(idCTSP)
          .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

      // ✅ 1. Lấy giá HIỆN TẠI của sản phẩm (sau khuyến mãi)
      BigDecimal donGiaPerUnit = updateAll.tinhGiaCuoiCung(idCTSP)[1];
      System.out.println("💰 Giá gốc: " + ctsp.getGia_ban());
      System.out.println("💰 Giá sau KM: " + donGiaPerUnit);

      // ✅ 2. Kiểm tra tồn kho - tính tổng số lượng đã mua (tất cả các dòng)
      List<HoaDonChiTiet> allItemsOfProduct = hoaDonChiTietRepo
          .findAllByHoaDonAndChiTietSanPham(idHD, idCTSP);

      int soLuongTonKho = ctsp.getSo_luong();
      int soLuongDaMuaAllPrices = allItemsOfProduct.stream()
          .mapToInt(HoaDonChiTiet::getSo_luong)
          .sum();

      int soLuongCoTheMua = soLuongTonKho - soLuongDaMuaAllPrices;
      int soLuong = Math.min(soLuongInput, soLuongCoTheMua);

      if (soLuong <= 0) {
        return ResponseEntity.badRequest()
            .body("Sản phẩm đã hết hàng hoặc đã đạt giới hạn trong giỏ!");
      }

      // ✅ 3. TÌM sản phẩm với CÙNG ID (BỎ QUA GIÁ - LUÔN UPDATE GIÁ MỚI)
      List<HoaDonChiTiet> existingItems = hoaDonChiTietRepo
          .findByHoaDonAndChiTietSanPham(idHD, idCTSP);

      HoaDonChiTiet chiTiet;

      // ✅ 4. NẾU ĐÃ TỒN TẠI -> CỘNG SỐ LƯỢNG + UPDATE GIÁ MỚI
      if (!existingItems.isEmpty()) {
        // Lấy dòng đầu tiên (nếu có nhiều dòng duplicate, merge vào dòng đầu)
        chiTiet = existingItems.get(0);

        // ✅ Lấy giá cũ để log
        BigDecimal giaCu = chiTiet.getDon_gia().divide(BigDecimal.valueOf(chiTiet.getSo_luong()), 2,
            RoundingMode.HALF_UP);

        // ✅ Cộng số lượng
        int soLuongMoi = chiTiet.getSo_luong() + soLuong;
        chiTiet.setSo_luong(soLuongMoi);

        // ✅ LUÔN CẬP NHẬT GIÁ MỚI (don_gia = đơn giá mới * số lượng)
        chiTiet.setDon_gia(donGiaPerUnit.multiply(BigDecimal.valueOf(soLuongMoi)));

        if (!giaCu.equals(donGiaPerUnit)) {
          System.out.println("🔄 Giá thay đổi: " + giaCu + " → " + donGiaPerUnit);
        }
        System.out.println("✅ Merge vào dòng cũ, tổng SL: " + soLuongMoi + ", giá mới: " + donGiaPerUnit);
      }
      // ✅ 5. NẾU CHƯA TỒN TẠI -> TẠO DÒNG MỚI
      else {
        chiTiet = new HoaDonChiTiet();
        chiTiet.setHoaDon(hoaDon);
        chiTiet.setChiTietSanPham(ctsp);
        chiTiet.setSo_luong(soLuong);
        // ✅ don_gia = đơn giá * số lượng
        chiTiet.setDon_gia(donGiaPerUnit.multiply(BigDecimal.valueOf(soLuong)));

        System.out.println("✅ Thêm dòng mới với giá: " + donGiaPerUnit);
      }

      // ✅ 6. Trừ tồn kho
      ctsp.setSo_luong(ctsp.getSo_luong() - soLuong);
      chiTietSanPhamRepo.save(ctsp);

      // ✅ 7. Lưu chi tiết hóa đơn
      hoaDonChiTietRepo.save(chiTiet);

      // ✅ 8. Cập nhật lại tổng tiền và voucher (hàm này sẽ tính toàn bộ)
      updateTongTienHoaDon(idHD);

      // ✅ 9. Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi thêm sản phẩm: " + e.getMessage());
    }
  }

  /**
   * ✅ API mới: Get realtime stock và status của CTSP
   * Gọi trước khi tăng/giảm số lượng để check stock hiện tại
   */
  @GetMapping("/getCTSPRealtime/{idCTSP}")
  public ResponseEntity<?> getCTSPRealtime(@PathVariable Integer idCTSP) {
    try {
      ChiTietSanPham ctsp = chiTietSanPhamRepo.findById(idCTSP)
          .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

      // Trả về thông tin cần thiết
      Map<String, Object> response = new HashMap<>();
      response.put("id_chi_tiet_san_pham", ctsp.getId_chi_tiet_san_pham());
      response.put("so_luong", ctsp.getSo_luong());
      response.put("trang_thai", ctsp.getTrang_thai());
      response.put("trang_thai_san_pham", ctsp.getSanPham().getTrang_thai());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi lấy thông tin sản phẩm: " + e.getMessage());
    }
  }

  /**
   * ✅ API mới: Kiểm tra stock realtime cho TẤT CẢ items trong hóa đơn
   * Gọi khi: switch tab, reload page, trước khi thanh toán
   */
  @GetMapping("/checkCartStock/{idHoaDon}")
  public ResponseEntity<?> checkCartStock(@PathVariable Integer idHoaDon) {
    try {
      List<HoaDonChiTiet> items = hoaDonChiTietRepo.findByIdHoaDon(idHoaDon);
      List<Map<String, Object>> stockStatus = new ArrayList<>();
      List<String> invalidItems = new ArrayList<>();

      for (HoaDonChiTiet item : items) {
        ChiTietSanPham ctsp = item.getChiTietSanPham();
        Map<String, Object> status = new HashMap<>();
        status.put("id", ctsp.getId_chi_tiet_san_pham());
        status.put("name", ctsp.getSanPham().getTen_san_pham());
        status.put("qty_in_cart", item.getSo_luong());
        status.put("stock", ctsp.getSo_luong());
        status.put("ctsp_active", ctsp.getTrang_thai());
        status.put("product_active", ctsp.getSanPham().getTrang_thai());

        boolean isInvalid = !Boolean.TRUE.equals(ctsp.getTrang_thai())
            || !Boolean.TRUE.equals(ctsp.getSanPham().getTrang_thai())
            || ctsp.getSo_luong() < 0;
        status.put("invalid", isInvalid);

        if (isInvalid)
          invalidItems.add(ctsp.getSanPham().getTen_san_pham());
        stockStatus.add(status);
      }

      return ResponseEntity.ok(Map.of(
          "items", stockStatus,
          "has_invalid_items", !invalidItems.isEmpty(),
          "invalid_item_names", invalidItems));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", "Lỗi kiểm tra stock: " + e.getMessage()));
    }
  }

  @PostMapping("/setSPHD")
  public ResponseEntity<?> setSPHD(
      @RequestParam("idHoaDon") Integer idHD,
      @RequestParam("idCTSP") Integer idCTSP,
      @RequestParam("soLuongMoi") Integer soLuongMoi) {
    try {
      // ✅ QUY TẮC MỚI: Enforce minimum quantity = 1
      if (soLuongMoi < 1) {
        return ResponseEntity.badRequest()
            .body("Số lượng tối thiểu là 1. Vui lòng sử dụng nút xóa để loại bỏ sản phẩm khỏi giỏ hàng.");
      }

      if (soLuongMoi <= 0) {
        return ResponseEntity.badRequest().body("Số lượng phải lớn hơn 0!");
      }

      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));

      ChiTietSanPham chiTietSP = chiTietSanPhamRepo.findById(idCTSP)
          .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

      Optional<HoaDonChiTiet> optionalCT = hoaDonChiTietRepo
          .findByChiTietSanPhamIdAndHoaDonId(idCTSP, idHD);

      int soLuongTrongHD = optionalCT.map(HoaDonChiTiet::getSo_luong).orElse(0);
      int soLuongTonKho = chiTietSP.getSo_luong();

      int tongToiDa = soLuongTonKho + soLuongTrongHD;
      if (soLuongMoi > tongToiDa) {
        return ResponseEntity.badRequest().body("Vượt quá số lượng tồn kho cho phép!");
      }

      // Cập nhật tồn kho
      int chenhLech = soLuongMoi - soLuongTrongHD;
      chiTietSP.setSo_luong(soLuongTonKho - chenhLech);

      // Tìm đơn giá (ưu tiên giá khuyến mãi)
      BigDecimal donGiaLe = chiTietSanPhamRepo.getAllCTSPKM().stream()
          .filter(ct -> ct.getId_chi_tiet_san_pham().equals(chiTietSP.getId_chi_tiet_san_pham()))
          .map(ct -> BigDecimal.valueOf(ct.getGia_ban()))
          .findFirst()
          .orElse(BigDecimal.ZERO);

      // Tạo hoặc cập nhật chi tiết hóa đơn
      HoaDonChiTiet chiTiet = optionalCT.orElseGet(() -> {
        HoaDonChiTiet newCT = new HoaDonChiTiet();
        newCT.setHoaDon(hoaDon);
        newCT.setChiTietSanPham(chiTietSP);
        return newCT;
      });

      chiTiet.setSo_luong(soLuongMoi);
      // don_gia phải lưu TỔNG TIỀN (giá_lẻ × số_lượng)
      chiTiet.setDon_gia(donGiaLe.multiply(BigDecimal.valueOf(soLuongMoi)));

      // Lưu lại DB
      chiTietSanPhamRepo.save(chiTietSP);
      hoaDonChiTietRepo.save(chiTiet);
      updateTongTienHoaDon(hoaDon.getId_hoa_don());

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi cập nhật số lượng: " + e.getMessage());
    }
  }

  @PostMapping("/giamSPHD")
  public ResponseEntity<?> giamSPHD(
      @RequestParam("idHoaDon") Integer idHD,
      @RequestParam("idCTSP") Integer idCTSP,
      @RequestParam("soLuong") Integer soLuong) {
    try {
      HoaDonChiTiet chiTiet = hoaDonChiTietRepo.findByChiTietSanPhamIdAndHoaDonId(idCTSP, idHD)
          .orElseThrow(() -> new RuntimeException("Sản phẩm không có trong hóa đơn"));

      ChiTietSanPham sp = chiTietSanPhamRepo.findById(idCTSP)
          .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

      BigDecimal giaLe = chiTiet.getDon_gia().divide(BigDecimal.valueOf(chiTiet.getSo_luong()), 2,
          RoundingMode.HALF_UP);

      int soLuongConLai = chiTiet.getSo_luong() - soLuong;
      if (soLuongConLai <= 0) {
        hoaDonChiTietRepo.delete(chiTiet);
      } else {
        chiTiet.setSo_luong(soLuongConLai);
        chiTiet.setDon_gia(giaLe.multiply(BigDecimal.valueOf(soLuongConLai)));
        hoaDonChiTietRepo.save(chiTiet);
      }

      sp.setSo_luong(sp.getSo_luong() + soLuong);
      chiTietSanPhamRepo.save(sp);

      HoaDon hoaDon = hoaDonRepo.findById(idHD)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));

      updateTongTienHoaDon(hoaDon.getId_hoa_don());

      // ✅ Trả về full invoice data cho FE
      return ResponseEntity.ok(getFullInvoiceResponse(idHD));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi giảm sản phẩm: " + e.getMessage());
    }
  }

  @DeleteMapping("/xoaSPHD")
  public ResponseEntity<?> xoaSanPhamKhoiHoaDon(
      @RequestParam("idHoaDon") Integer idHoaDon,
      @RequestParam("idChiTietSanPham") Integer idChiTietSanPham) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHoaDon)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));

      if ("Đã thanh toán".equalsIgnoreCase(hoaDon.getTrang_thai())) {
        return ResponseEntity.badRequest()
            .body(Map.of("success", false, "message", "Không thể xóa sản phẩm từ hóa đơn đã thanh toán!"));
      }

      // ✅ NEW: Get quantity before deleting to restore stock
      List<HoaDonChiTiet> hdctList = hoaDonChiTietRepo.findByHoaDonAndChiTietSanPham(idHoaDon,
          idChiTietSanPham);
      if (!hdctList.isEmpty()) {
        int soLuongXoaTong = hdctList.stream()
            .mapToInt(HoaDonChiTiet::getSo_luong)
            .sum();

        // Xóa tất cả các dòng
        hoaDonChiTietRepo.xoaSPKhoiHD(idHoaDon, idChiTietSanPham);

        // Restore stock
        ChiTietSanPham ctsp = chiTietSanPhamRepo.findById(idChiTietSanPham)
            .orElseThrow(() -> new RuntimeException("CTSP không tồn tại!"));
        ctsp.setSo_luong(ctsp.getSo_luong() + soLuongXoaTong);
        chiTietSanPhamRepo.save(ctsp);
      } else {
        // Fallback: just delete if not found
        hoaDonChiTietRepo.xoaSPKhoiHD(idHoaDon, idChiTietSanPham);
      }

      // Cập nhật tổng tiền
      try {
        updateTongTienHoaDon(idHoaDon);
      } catch (Exception ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("success", false, "message", "Lỗi khi cập nhật tổng tiền: " + ex.getMessage()));
      }

      // ✅ Trả về full invoice data (HoaDonResponse, không phải entity)
      return ResponseEntity.ok(getFullInvoiceResponse(idHoaDon));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "message", "Lỗi khi xóa sản phẩm: " + e.getMessage()));
    }
  }

  @GetMapping("/get-suitable-vouchers")
  public ResponseEntity<?> getSuitableVouchers(@RequestParam("tongTien") BigDecimal tongTien) {
    try {
      List<VoucherBHResponse> vouchers = voucherRepository.listVoucherHopLeTheoGia(tongTien, LocalDateTime.now());
      return ResponseEntity.ok(vouchers);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi lấy danh sách voucher: " + e.getMessage());
    }
  }

  @PutMapping("/update-phuongThucNhanHang")
  public ResponseEntity<?> updatePhuongThuNhanHang(
      @RequestParam("idHoaDon") Integer idHoaDon,
      @RequestParam("phuongThuc") String phuongThuc) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHoaDon)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));
      hoaDon.setPhuong_thuc_nhan_hang(phuongThuc);
      hoaDonRepo.save(hoaDon);
      return ResponseEntity.ok(hoaDonRepo.findHoaDonById(idHoaDon).get(0));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi cập nhật phương thức nhận hàng: " + e.getMessage());
    }
  }

  @PostMapping("/apply-voucher")
  public ResponseEntity<?> applyVoucher(
      @RequestParam("idHoaDon") Integer idHoaDon,
      @RequestParam(value = "idVoucher", required = false) Integer idVoucher) {
    try {
      HoaDon hoaDon = hoaDonRepo.findById(idHoaDon)
          .orElseThrow(() -> new RuntimeException("Hóa đơn không tồn tại!"));

      Voucher oldVoucher = hoaDon.getVoucher();

      // Trường hợp 1: Bỏ voucher (idVoucher là null)
      if (idVoucher == null) {
        if (oldVoucher != null) {
          // Trả lại số lượng cho voucher cũ
          oldVoucher.setSoLuong(oldVoucher.getSoLuong() + 1);
          voucherRepository.save(oldVoucher);
        }
        hoaDon.setVoucher(null);
      }
      // Trường hợp 2: Áp dụng voucher mới
      else {
        Voucher newVoucher = voucherRepository.findById(idVoucher)
            .orElseThrow(() -> new RuntimeException("Voucher không tồn tại!"));

        // Nếu voucher mới khác voucher cũ
        if (oldVoucher == null || !oldVoucher.getId().equals(newVoucher.getId())) {
          // Trả lại số lượng cho voucher cũ (nếu có)
          if (oldVoucher != null) {
            oldVoucher.setSoLuong(oldVoucher.getSoLuong() + 1);
            voucherRepository.save(oldVoucher);
          }

          // Kiểm tra số lượng voucher mới
          if (newVoucher.getSoLuong() <= 0) {
            return ResponseEntity.badRequest().body("Voucher đã hết số lượng!");
          }

          // Trừ số lượng voucher mới
          newVoucher.setSoLuong(newVoucher.getSoLuong() - 1);
          voucherRepository.save(newVoucher);
          hoaDon.setVoucher(newVoucher);
        }
        // Nếu giống nhau thì không làm gì cả (hoặc có thể check lại điều kiện)
      }

      hoaDonRepo.save(hoaDon);

      // Cập nhật lại tổng tiền (sẽ tự động tính lại giảm giá dựa trên voucher đã set)
      updateTongTienHoaDon(idHoaDon);

      // Trả về hóa đơn đã cập nhật
      return ResponseEntity.ok(hoaDonRepo.findHoaDonById(idHoaDon).get(0));

    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Lỗi khi áp dụng voucher: " + e.getMessage());
    }
  }

  // hàm này cần xem lại
  @PostMapping("/admin/khach-hang/them-moi")
  @ResponseBody
  public Map<String, Object> themKhachHang(
      @RequestBody KhachHang khachHang) {
    Map<String, Object> response = new HashMap<>();
    try {
      KhachHang newKhachHang = khachHangRepo.save(khachHang);
      response.put("success", true);
      response.put("idKhachHang", newKhachHang.getIdKhachHang());
      response.put("message", "Thêm khách hàng thành công");
    } catch (Exception e) {
      response.put("success", false);
      response.put("message", "Lỗi khi thêm khách hàng: " + e.getMessage());
    }
    return response;
  }

  // callback zalopay
  @PostMapping("/zalopay/callback")
  public ResponseEntity<String> handleZaloPayCallback(@RequestBody Map<String, Object> callbackData) {
    try {
      // Kiểm tra tính hợp lệ của callback
      if (isValidCallback(callbackData)) {
        // Lấy thông tin từ callback
        Long appTransId = Long.parseLong(callbackData.get("app_trans_id").toString());
        String status = callbackData.get("status").toString();

        if ("1".equals(status)) { // Thanh toán thành công
          // Cập nhật trạng thái hóa đơn
          Optional<HoaDon> hoaDonOpt = hoaDonRepo.findById(appTransId.intValue());
          if (hoaDonOpt.isPresent()) {
            HoaDon hoaDon = hoaDonOpt.get();
            hoaDon.setTrang_thai("Đã thanh toán");
            hoaDonRepo.save(hoaDon);
          }
        }
        return ResponseEntity.ok("Callback processed successfully");
      } else {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid callback data");
      }
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing callback");
    }
  }

  private boolean isValidCallback(Map<String, Object> callbackData) {
    // Kiểm tra tính hợp lệ của callback (ví dụ: chữ ký HMAC)
    return true; // Thay thế bằng logic thực tế
  }

  @GetMapping("/check-so-luong")
  public ResponseEntity<Map<String, Integer>> checkSoLuong(
      @RequestParam("idCTSP") Integer idCTSP) {
    Optional<ChiTietSanPham> ctspOpt = chiTietSanPhamRepo.findById(idCTSP);

    if (!ctspOpt.isPresent()) {
      return ResponseEntity.notFound().build();
    }

    int soLuongTon = ctspOpt.get().getSo_luong();

    Map<String, Integer> response = new HashMap<>();
    response.put("soLuongTon", soLuongTon);

    return ResponseEntity.ok(response);
  }

  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  /**
   * ✅ Helper method: Get full invoice response data
   * Used after any invoice modification to return consistent data to Frontend
   */
  private HoaDonResponse getFullInvoiceResponse(Integer idHoaDon) {
    List<HoaDonResponse> responses = hoaDonRepo.findHoaDonById(idHoaDon);
    if (responses.isEmpty()) {
      throw new RuntimeException("Không tìm thấy hóa đơn sau khi cập nhật");
    }
    return responses.get(0);
  }

  // Gen mã hóa đơn
  private String generateUniqueMaHoaDon() {
    Random random = new Random();
    String maHoaDon;
    boolean isDuplicate;
    do {
      StringBuilder code = new StringBuilder("HD");
      for (int i = 0; i < 6; i++) {
        code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
      }
      maHoaDon = code.toString();

      final String finalMaHoaDon = maHoaDon;
      isDuplicate = hoaDonRepo.findAll().stream()
          .anyMatch(hd -> finalMaHoaDon.equalsIgnoreCase(hd.getMa_hoa_don()));

    } while (isDuplicate);

    return maHoaDon;
  }

  // update tổng tiền theo hóa đơn hiện tại
  private void updateTongTienHoaDon(Integer idHoaDon) {
    Optional<HoaDon> hoaDonOpt = hoaDonRepo.findById(idHoaDon);
    if (!hoaDonOpt.isPresent()) {
      throw new RuntimeException("Không tìm thấy hóa đơn");
    }

    HoaDon hoaDon = hoaDonOpt.get();
    BigDecimal tongTienTruocGiam = BigDecimal.ZERO;
    BigDecimal tongTienSauGiam = BigDecimal.ZERO;
    BigDecimal phuThu = BigDecimal.ZERO;

    List<HoaDonChiTiet> hoaDonChiTietList = hoaDon.getHoaDonChiTietList();

    // Tính tổng tiền sản phẩm
    for (HoaDonChiTiet hoaDonChiTiet : hoaDonChiTietList) {
      tongTienTruocGiam = tongTienTruocGiam.add(hoaDonChiTiet.getDon_gia());
    }

    // Tính voucher
    BigDecimal giamGia = BigDecimal.ZERO;
    if (hoaDon.getVoucher() != null) {
      Voucher voucher = hoaDon.getVoucher();

      // Kiểm tra đơn hàng có đủ điều kiện tối thiểu không
      if (tongTienTruocGiam.compareTo(voucher.getGiaTriToiThieu()) >= 0) {

        if ("Phần trăm".equals(voucher.getKieuGiamGia())) {
          // Giảm theo phần trăm: tính % rồi chia 100
          giamGia = tongTienTruocGiam
              .multiply(voucher.getGiaTriGiam())
              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

          // Áp dụng giới hạn giảm tối đa (nếu có)
          if (voucher.getGiaTriToiDa() != null && voucher.getGiaTriToiDa().compareTo(BigDecimal.ZERO) > 0) {
            if (giamGia.compareTo(voucher.getGiaTriToiDa()) > 0) {
              giamGia = voucher.getGiaTriToiDa();
            }
          }
        } else {
          // Giảm cố định (Tiền mặt)
          giamGia = voucher.getGiaTriGiam();
        }

        // Đảm bảo giảm giá không vượt quá tổng tiền
        if (giamGia.compareTo(tongTienTruocGiam) > 0) {
          giamGia = tongTienTruocGiam;
        }
      }
    }

    // Tính tổng tiền sau giảm
    tongTienSauGiam = tongTienTruocGiam.subtract(giamGia);
    if (tongTienSauGiam.compareTo(BigDecimal.ZERO) < 0) {
      tongTienSauGiam = BigDecimal.ZERO;
    }

    // Lưu vào DB
    hoaDon.setTong_tien_truoc_giam(tongTienTruocGiam);
    hoaDon.setTong_tien_sau_giam(tongTienSauGiam);
    hoaDonRepo.save(hoaDon);

    System.out.println("✅ Updated invoice: tongTruocGiam=" + tongTienTruocGiam +
        ", tongSauGiam=" + tongTienSauGiam +
        ", giamGia=" + giamGia);

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
