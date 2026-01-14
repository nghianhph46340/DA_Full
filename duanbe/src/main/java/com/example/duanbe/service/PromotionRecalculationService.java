package com.example.duanbe.service;

import com.example.duanbe.entity.ChiTietKhuyenMai;
import com.example.duanbe.entity.ChiTietSanPham;
import com.example.duanbe.entity.HoaDon;
import com.example.duanbe.entity.HoaDonChiTiet;
import com.example.duanbe.entity.KhuyenMai;
import com.example.duanbe.entity.Voucher;
import com.example.duanbe.repository.ChiTietKhuyenMaiRepo;
import com.example.duanbe.repository.ChiTietSanPhamRepo;
import com.example.duanbe.repository.HoaDonChiTietRepo;
import com.example.duanbe.repository.HoaDonRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PromotionRecalculationService {

    @Autowired
    private ChiTietKhuyenMaiRepo ctkmRepo;

    @Autowired
    private ChiTietSanPhamRepo ctspRepo;

    @Autowired
    private HoaDonChiTietRepo hoaDonChiTietRepo;

    @Autowired
    private HoaDonRepo hoaDonRepo;

    /**
     * Giá sau khuyến mãi mới nhất (dùng để truyền cho các service khác)
     */
    public BigDecimal giaMoiNhat = BigDecimal.ZERO;

    /**
     * ✅ MAIN METHOD: Tính lại giá khuyến mãi VÀ cập nhật hóa đơn đang chờ
     * Gọi khi admin sửa giá sản phẩm
     * 
     * @param idChiTietSanPham ID sản phẩm vừa thay đổi giá
     */
    @Transactional
    public void recalculatePromotionPrices(Integer idChiTietSanPham) {
        try {
            // 1. Lấy giá hiện tại của sản phẩm
            giaMoiNhat = BigDecimal.ZERO;
            ChiTietSanPham ctsp = ctspRepo.findById(idChiTietSanPham).orElse(null);
            if (ctsp == null) {
                System.out.println("⚠️ Không tìm thấy CTSP #" + idChiTietSanPham);
                return;
            }

            BigDecimal giaBan = ctsp.getGia_ban();
            if (giaBan == null || giaBan.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("⚠️ Giá bán không hợp lệ cho CTSP #" + idChiTietSanPham);
                return;
            }

            // 2. Cập nhật giá khuyến mãi trong chi_tiet_khuyen_mai
            updatePromotionDetails(idChiTietSanPham, giaBan);

            // 3. ✅ NEW: Cập nhật tất cả hóa đơn đang chờ có chứa sản phẩm này
            updatePendingInvoicesForProduct(idChiTietSanPham);

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi tính lại giá khuyến mãi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cập nhật giá khuyến mãi trong bảng chi_tiet_khuyen_mai
     */
    private void updatePromotionDetails(Integer idChiTietSanPham, BigDecimal giaBan) {
        List<ChiTietKhuyenMai> danhSachKM = ctkmRepo.findAllByChiTietSanPhamId(idChiTietSanPham, "Đang diễn ra");

        if (danhSachKM.isEmpty()) {
            System.out.println("ℹ️ CTSP #" + idChiTietSanPham + " không có khuyến mãi");
            giaMoiNhat = giaBan; // Không có KM -> dùng giá gốc
            return;
        }

        System.out.println("🔄 Bắt đầu tính lại " + danhSachKM.size() + " khuyến mãi cho CTSP #"
                + idChiTietSanPham + " (Giá: " + giaBan + ")");

        BigDecimal giaTotNhat = giaBan;

        for (ChiTietKhuyenMai ctkm : danhSachKM) {
            KhuyenMai km = ctkm.getKhuyenMai();

            if (km == null || !"Đang diễn ra".equals(km.getTrangThai())) {
                continue;
            }

            BigDecimal giaSauGiam = calculateDiscountedPrice(giaBan, km);

            // Chọn giá thấp nhất (KM tốt nhất)
            if (giaSauGiam.compareTo(giaTotNhat) < 0) {
                giaTotNhat = giaSauGiam;
            }

            // Cập nhật lại giá sau giảm trong DB
            ctkm.setGiaSauGiam(giaSauGiam);
            ctkmRepo.save(ctkm);

            System.out.println(String.format(
                    "✅ Cập nhật CTKM #%d: %s | %s | Giá gốc: %s → Giá sau giảm: %s",
                    ctkm.getId(),
                    km.getTenKhuyenMai(),
                    km.getKieuGiamGia(),
                    giaBan,
                    giaSauGiam));
        }

        giaMoiNhat = giaTotNhat;
    }

    /**
     * ✅ NEW: Cập nhật tất cả hóa đơn đang chờ có chứa sản phẩm này
     * - Update don_gia = giá mới × số lượng
     * - Tính lại tổng tiền hóa đơn (có voucher)
     */
    @Transactional
    public void updatePendingInvoicesForProduct(Integer idChiTietSanPham) {
        try {
            // Lấy giá mới nhất (đã tính khuyến mãi)
            BigDecimal giaMoi = getGiaSauKhuyenMai(idChiTietSanPham);

            if (giaMoi == null || giaMoi.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("⚠️ Không có giá hợp lệ để update cho CTSP #" + idChiTietSanPham);
                return;
            }

            // Tìm tất cả hoa_don_chi_tiet có CTSP này trong hóa đơn "Đang chờ"
            List<HoaDonChiTiet> pendingItems = hoaDonChiTietRepo.findHDCTDangChoByCTSP(idChiTietSanPham);

            if (pendingItems.isEmpty()) {
                System.out.println("ℹ️ Không có hóa đơn đang chờ chứa CTSP #" + idChiTietSanPham);
                return;
            }

            System.out.println(
                    "🔄 Cập nhật " + pendingItems.size() + " dòng hóa đơn chi tiết cho CTSP #" + idChiTietSanPham);

            // Set để track các hóa đơn cần update tổng
            java.util.Set<Integer> invoiceIdsToUpdate = new java.util.HashSet<>();

            for (HoaDonChiTiet hdct : pendingItems) {
                // ✅ FIX: don_gia = đơn giá × số lượng
                BigDecimal donGiaMoi = giaMoi.multiply(BigDecimal.valueOf(hdct.getSo_luong()));
                BigDecimal donGiaCu = hdct.getDon_gia();

                if (!donGiaMoi.equals(donGiaCu)) {
                    hdct.setDon_gia(donGiaMoi);
                    hoaDonChiTietRepo.save(hdct);

                    System.out.println(String.format("  📦 HDCT #%d: %s × %d = %s (cũ: %s)",
                            hdct.getId_hoa_don_chi_tiet(),
                            giaMoi,
                            hdct.getSo_luong(),
                            donGiaMoi,
                            donGiaCu));

                    // Track hóa đơn cần cập nhật tổng
                    invoiceIdsToUpdate.add(hdct.getHoaDon().getId_hoa_don());
                }
            }

            // ✅ Cập nhật tổng tiền cho các hóa đơn bị ảnh hưởng
            for (Integer idHoaDon : invoiceIdsToUpdate) {
                recalculateInvoiceTotal(idHoaDon);
            }

            System.out.println("✅ Hoàn thành cập nhật " + invoiceIdsToUpdate.size() + " hóa đơn đang chờ");

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi cập nhật hóa đơn đang chờ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ NEW: Tính lại tổng tiền hóa đơn (bao gồm voucher)
     */
    @Transactional
    public void recalculateInvoiceTotal(Integer idHoaDon) {
        try {
            HoaDon hoaDon = hoaDonRepo.findById(idHoaDon).orElse(null);
            if (hoaDon == null) {
                return;
            }

            // Chỉ tính cho hóa đơn đang chờ
            String trangThai = hoaDon.getTrang_thai();
            if (!"Đang chờ".equals(trangThai) && !"Chờ xác nhận".equals(trangThai)) {
                return;
            }

            BigDecimal tongTienTruocGiam = BigDecimal.ZERO;
            List<HoaDonChiTiet> items = hoaDon.getHoaDonChiTietList();

            // Tính tổng tiền sản phẩm
            for (HoaDonChiTiet hdct : items) {
                tongTienTruocGiam = tongTienTruocGiam.add(hdct.getDon_gia());
            }

            // Tính voucher
            BigDecimal giamGia = BigDecimal.ZERO;
            if (hoaDon.getVoucher() != null) {
                Voucher voucher = hoaDon.getVoucher();

                // Kiểm tra đơn hàng có đủ điều kiện tối thiểu không
                if (tongTienTruocGiam.compareTo(voucher.getGiaTriToiThieu()) >= 0) {

                    if ("Phần trăm".equals(voucher.getKieuGiamGia())) {
                        giamGia = tongTienTruocGiam
                                .multiply(voucher.getGiaTriGiam())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                        // Áp dụng giới hạn giảm tối đa
                        if (voucher.getGiaTriToiDa() != null
                                && voucher.getGiaTriToiDa().compareTo(BigDecimal.ZERO) > 0) {
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
                } else {
                    // Không đủ điều kiện tối thiểu -> gỡ voucher
                    System.out.println("⚠️ Hóa đơn #" + idHoaDon + " không đủ điều kiện voucher, tự động gỡ");
                    hoaDon.setVoucher(null);
                }
            }

            // Tính tổng tiền sau giảm
            BigDecimal tongTienSauGiam = tongTienTruocGiam.subtract(giamGia);
            if (tongTienSauGiam.compareTo(BigDecimal.ZERO) < 0) {
                tongTienSauGiam = BigDecimal.ZERO;
            }

            // Thêm phí vận chuyển nếu có
            BigDecimal phiVanChuyen = hoaDon.getPhi_van_chuyen();
            if (phiVanChuyen == null) {
                phiVanChuyen = BigDecimal.ZERO;
            }

            BigDecimal tongThanhToan = tongTienSauGiam.add(phiVanChuyen);

            // Cập nhật hóa đơn
            hoaDon.setTong_tien_truoc_giam(tongTienTruocGiam);
            hoaDon.setTong_tien_sau_giam(tongThanhToan);
            hoaDonRepo.save(hoaDon);

            System.out.println(String.format("✅ Cập nhật HĐ #%d: Tổng: %s - Giảm: %s + Ship: %s = %s",
                    idHoaDon, tongTienTruocGiam, giamGia, phiVanChuyen, tongThanhToan));

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi tính tổng hóa đơn #" + idHoaDon + ": " + e.getMessage());
        }
    }

    /**
     * Lấy giá sau khuyến mãi của sản phẩm (giá thấp nhất)
     */
    public BigDecimal getGiaSauKhuyenMai(Integer idChiTietSanPham) {
        ChiTietSanPham ctsp = ctspRepo.findById(idChiTietSanPham).orElse(null);
        if (ctsp == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal giaBan = ctsp.getGia_ban();
        if (giaBan == null) {
            return BigDecimal.ZERO;
        }

        // Tìm giá khuyến mãi tốt nhất
        List<ChiTietKhuyenMai> danhSachKM = ctkmRepo.findAllByChiTietSanPhamId(idChiTietSanPham, "Đang diễn ra");

        if (danhSachKM.isEmpty()) {
            return giaBan; // Không có KM -> dùng giá gốc
        }

        BigDecimal giaTotNhat = giaBan;
        for (ChiTietKhuyenMai ctkm : danhSachKM) {
            if (ctkm.getGiaSauGiam() != null && ctkm.getGiaSauGiam().compareTo(giaTotNhat) < 0) {
                giaTotNhat = ctkm.getGiaSauGiam();
            }
        }

        return giaTotNhat;
    }

    public BigDecimal giaMoi() {
        return giaMoiNhat;
    }

    /**
     * Tính giá sau khi áp dụng khuyến mãi
     */
    private BigDecimal calculateDiscountedPrice(BigDecimal giaBan, KhuyenMai km) {
        BigDecimal giaSauGiam;

        if ("Phần trăm".equals(km.getKieuGiamGia())) {
            BigDecimal giaTriGiam = km.getGiaTriGiam();
            BigDecimal giaTriToiDa = km.getGiaTriToiDa();

            BigDecimal soTienGiam = giaBan
                    .multiply(giaTriGiam)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            if (giaTriToiDa != null && soTienGiam.compareTo(giaTriToiDa) > 0) {
                soTienGiam = giaTriToiDa;
            }

            giaSauGiam = giaBan.subtract(soTienGiam);

        } else if ("Tiền mặt".equals(km.getKieuGiamGia())) {
            giaSauGiam = giaBan.subtract(km.getGiaTriGiam());

        } else {
            giaSauGiam = giaBan;
        }

        // Không cho giá âm
        if (giaSauGiam.compareTo(BigDecimal.ZERO) < 0) {
            giaSauGiam = BigDecimal.ZERO;
        }

        return giaSauGiam;
    }

    /**
     * Tính lại cho nhiều sản phẩm cùng lúc
     */
    public void recalculateMultipleProducts(List<Integer> listIdCTSP) {
        for (Integer id : listIdCTSP) {
            recalculatePromotionPrices(id);
        }
    }
}
