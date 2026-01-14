<template>
  <div class="container-fluid">
    <!-- Lọc trạng thái -->
    <div class="d-flex align-items-center justify-content-between">
      <div class="me-3">
        <span class="fw-bold me-2" style="font-size: 16px;">Trạng thái:</span>
        <div class="form-check form-check-inline">
          <input class="form-check-input" type="radio" name="trangThai" id="sapDienRa" value="Sắp diễn ra"
            v-model="selectedTrangThai" @change="onStatusChange">
          <label class="form-check-label" for="sapDienRa">Sắp diễn ra</label>
        </div>
        <div class="form-check form-check-inline">
          <input class="form-check-input" type="radio" name="trangThai" id="dangDienRa" value="Đang diễn ra"
            v-model="selectedTrangThai" @change="onStatusChange">
          <label class="form-check-label" for="dangDienRa">Đang diễn ra</label>
        </div>
        <div class="form-check form-check-inline">
          <input class="form-check-input" type="radio" name="trangThai" id="daKetThuc" value="Đã kết thúc"
            v-model="selectedTrangThai" @change="onStatusChange">
          <label class="form-check-label" for="daKetThuc">Đã kết thúc</label>
        </div>
       <div class="form-check form-check-inline">
          <input class="form-check-input" type="radio" name="trangThai" id="tatCa" value="" 
            v-model="selectedTrangThai" @change="onStatusChange">
          <label class="form-check-label" for="tatCa">Tất cả</label>
        </div>
      </div>
      <div>
        <button class="btn buttonADD" @click="router.push('/admin/quanlyvoucher/add')">+ Thêm Voucher</button>
      </div>
    </div>

    <!-- Tìm kiếm và lọc theo giá, ngày -->
    <div class="row mt-3 g-2 align-items-center">
      <div class="col-md-3 d-flex align-items-center mt-2">
        <label class="me-2" style="white-space: nowrap;">Kiểu giảm giá:</label>
        <select class="form-select" v-model="selectedKieuGiamGia" @change="fetchData(0)">
          <option value="">Tất cả</option>
          <option value="Phần trăm">Phần trăm</option>
          <option value="Tiền mặt">Tiền mặt</option>
        </select>
      </div>
      <div class="col-md-5 d-flex align-items-center flex-nowrap">
        <label class="me-2" style="white-space: nowrap;">Giá trị giảm tối đa:</label>
        <div class="d-flex flex-nowrap w-100">
          <input type="number" class="form-control me-2" v-model="minPrice" placeholder="Min" min="0" step="1" @keyup.enter="fetchData(0)">
          <span class="align-self-center">-</span>
          <input type="number" class="form-control ms-2" v-model="maxPrice" placeholder="Max" min="0" step="1" @keyup.enter="fetchData(0)">
        </div>
      </div>
      <div class="col-md-6 d-flex align-items-center mt-2">
        <label class="me-2" style="white-space: nowrap;">Ngày:</label>
        <input type="datetime-local" class="form-control w-50" v-model="startDate">
        <span class="mx-2">-</span>
        <input type="datetime-local" class="form-control w-50" v-model="endDate">
      </div>
      <div class="col-md-1 text-end mt-2">
        <button class="btn btn-outline-danger" @click="refreshData">
          <i class="bi bi-arrow-clockwise"></i>
        </button>
      </div>
    </div>

    <!-- Tiêu đề và chọn số lượng hiển thị -->
    <div class="card p-3 border-0 mt-4">
      <div class="d-flex justify-content-between align-items-center border-bottom pb-2">
        <h5 class="fw-bold mb-0" style="color: #ff6600;">📋 Danh sách voucher</h5>
        <div class="d-flex align-items-center">
          <label for="limitSelect" class="me-2 fw-medium text-muted mb-0">Hiển thị:</label>
          <select id="limitSelect" class="form-select form-select-sm w-auto" v-model="pageSize">
            <option value="5">5</option>
            <option value="10">10</option>
            <option value="20">20</option>
          </select>
        </div>
      </div>
    </div>

    <!-- Bảng voucher -->
    <div class="table-responsive mt-4">
      <table class="table table-hover">
        <thead>
          <tr>
            <th scope="col">#</th>
            <th scope="col">Mã voucher</th>
            <th scope="col">Tên voucher</th>
            <th scope="col">Giá trị giảm</th>
            <th scope="col">Giá trị tối thiểu</th>
            <th scope="col">Số lượng</th>
            <th scope="col">Kiểu giảm giá</th>
            <th scope="col">Giá trị tối đa</th>
            <th scope="col">Ngày bắt đầu</th>
            <th scope="col">Ngày kết thúc</th>
            <th scope="col">Trạng thái</th>
            <th scope="col">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="dataVoucher.length === 0">
            <td colspan="13" class="text-center">
              <a-empty :image="simpleImage" />
            </td>
          </tr>
          <tr v-for="(voucher, index) in dataVoucher" :key="voucher.id">
            <td>{{ index + 1 + (store.voucherCurrentPage * pageSize) }}</td>
            <td>{{ voucher.maVoucher }}</td>
            <td>{{ voucher.tenVoucher }}</td>
            <td>{{ formatGiaTriGiam(voucher) }}</td>
            <td>{{ formatGiaTriToiThieu(voucher) }}</td>
            <td>{{ voucher.soLuong }}</td>
            <td>{{ voucher.kieuGiamGia }}</td>
            <td>{{ formatGiaTriToiDa(voucher) }}</td>
            <td>{{ formatDate(voucher.ngayBatDau) }}</td>
            <td>{{ formatDate(voucher.ngayHetHan) }}</td>
            <td>{{ voucher.trangThai }}</td>
            <td>
              <button class="btn btn-edit d-inline-flex align-items-center" 
                      v-if="voucher.trangThai !== 'Đã kết thúc'"
                      @click="router.push(`/admin/quanlyvoucher/update/${voucher.id}`)"
                      style="background-color: white !important; border-color: #ff6600 !important; color: #ff6600 !important;">
                <i class="fas fa-edit"></i>
                <span class="btn-text">Sửa</span>
              </button>
              <a-switch v-if="voucher.trangThai === 'Đang diễn ra'"
                        :checked="true" 
                        :style="{ backgroundColor: '#ff6600' }" 
                        @click="offVoucher(voucher.id)" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Phân trang -->
    <div class="d-flex justify-content-center align-items-center mt-4">
      <button class="btn buttonPT" @click="fetchData(store.voucherCurrentPage - 1)" 
              :disabled="store.voucherCurrentPage === 0">
        <i class="fas fa-chevron-left me-1"></i>Previous
      </button>
      <span class="mx-3 fw-bold">Trang {{ store.voucherCurrentPage + 1 }} / {{ store.voucherTotalPages }}</span>
      <button class="btn buttonPT" @click="fetchData(store.voucherCurrentPage + 1)" 
              :disabled="store.voucherCurrentPage >= store.voucherTotalPages - 1">
        Next<i class="fas fa-chevron-right ms-1"></i>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed, onUnmounted } from 'vue';
import { useGbStore } from '@/stores/gbStore';
import { useRouter } from 'vue-router';
import { Empty } from 'ant-design-vue'; // Import Empty từ Ant Design Vue
import { toast } from 'vue3-toastify';

const router = useRouter();
const store = useGbStore();
const pageSize = ref(5);
const selectedTrangThai = ref('');
const selectedKieuGiamGia = ref('');
const minPrice = ref(null);
const maxPrice = ref(null);
const startDate = ref(null);
const endDate = ref(null);
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE; // Định nghĩa simpleImage

let refreshInterval = null;
let isFilterChanging = false; // ✅ Flag to prevent watcher race condition

// ✅ FIX: Set flag BEFORE clearing search to prevent watcher race condition
const onStatusChange = () => {  
  isFilterChanging = true;    // Set flag first!
  store.voucherSearchs = '';  // Clear search (watcher will skip)
  fetchData(0);
};

const fetchData = async (page = 0) => {
  // ✅ FIX: Only check page < 0, remove the buggy totalPages check
  // Previous bug: when totalPages = 0, condition "page >= totalPages" = "0 >= 0" = true
  // This caused early return and NO API call!
  if (page < 0) return;
  store.voucherCurrentPage = page;

  try {
    if (store.voucherSearchs && store.voucherSearchs.trim() !== '') {
      await store.searchVoucher(store.voucherSearchs, page, pageSize.value);
    } else if (minPrice.value || maxPrice.value) {
      await store.timKiemVoucherByPrice(minPrice.value, maxPrice.value, page, pageSize.value);
    } else if (startDate.value || endDate.value) {
      await store.timKiemVoucherByDate(startDate.value, endDate.value, page, pageSize.value);
    } else if (selectedTrangThai.value) {
      await store.getVoucherLocTrangThai(page, pageSize.value, selectedTrangThai.value);
    } else if (selectedKieuGiamGia.value) {
      await store.getVoucherLocKieuGiamGia(page, pageSize.value, selectedKieuGiamGia.value);
    } else {
      await store.getAllVouchers(page, pageSize.value);
    }
  } catch (error) {
    console.error('Lỗi trong fetchData:', error);
    toast.error('Có lỗi xảy ra khi tải dữ liệu!');
  }
};

const refreshData = async () => {
  store.voucherSearchs = '';
  selectedTrangThai.value = '';
  selectedKieuGiamGia.value = '';
  minPrice.value = null;
  maxPrice.value = null;
  startDate.value = null;
  endDate.value = null;
  await store.getAllVouchers(0, pageSize.value);
};

const offVoucher = async (id) => {
  await store.offVoucher(id);
  await fetchData(store.voucherCurrentPage);
};

const dataVoucher = computed(() => {
  let data = [];
  if (store.voucherSearchs && store.voucherSearchs.trim() !== '') {
    data = store.voucherSearch.length > 0 ? [...store.voucherSearch] : [];
  } else {
    data = [...store.getAllVoucherArr];
  }
  return data.sort((a, b) => b.id - a.id);
});

const formatNumber = (number) => {
  if (!number) return '0';
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(number);
};

const formatGiaTriGiam = (voucher) => {
  const value = formatNumber(voucher.giaTriGiam);
  return voucher.kieuGiamGia === 'Phần trăm' ? `${value}%` : `${value} VNĐ`;
};

const formatGiaTriToiThieu = (voucher) => {
  return `${formatNumber(voucher.giaTriToiThieu)} VNĐ`;
};

const formatGiaTriToiDa = (voucher) => {
  return `${formatNumber(voucher.giaTriToiDa)} VNĐ`;
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

// ✅ Watch search - skip if filter is changing to prevent race condition
watch(() => store.voucherSearchs, async (newValue) => {
  if (isFilterChanging) {
    isFilterChanging = false;
    return; // Skip this watch trigger
  }
  if (!newValue || newValue.trim() === '') {
    await fetchData(0);
  } else {
    await store.searchVoucher(newValue, 0, pageSize.value);
  }
});

// ✅ REMOVED conflicting watchers - let radio buttons handle it
// watch([startDate, endDate], ...) → causing duplicates
// watch([pageSize, selectedTrangThai, ...]) → causing duplicates

onMounted(async () => {
  await store.getAllVouchers(0, pageSize.value);
  refreshInterval = setInterval(() => {
    fetchData(store.voucherCurrentPage);
  }, 5000);
});

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }
});
</script>

<style scoped>
.table {
  --bs-table-hover-bg: rgb(183 183 183 / 8%);
}

.buttonPT {
  background-color: white;
  text-align: center;
  color: #ff6600;
  border: 2px solid #ff6600;
  cursor: pointer;
  transition: all 0.3s ease-in-out;
  min-width: 110px;
  height: 40px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.buttonPT:hover:not(:disabled) {
  background-color: #ff6600;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(255, 102, 0, 0.3);
}

.buttonPT:disabled {
  background-color: #f5f5f5;
  border-color: #d9d9d9;
  color: #bfbfbf;
  cursor: not-allowed;
  opacity: 0.6;
}

.buttonADD {
  background-color: #ff6600;
  color: white;
  font-weight: bold;
  border: none;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.buttonADD:hover {
  background-color: #e55a00;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(255, 102, 0, 0.3);
}

.form-check-input {
  appearance: none;
  width: 16px;
  height: 16px;
  border: 1px solid #ff6600;
  border-radius: 50%;
  display: inline-block;
  position: relative;
  background-color: white;
}

.form-check-input:checked::before {
  content: "";
  width: 8px;
  height: 8px;
  background-color: #ff6600;
  border-radius: 50%;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.form-check-input:focus {
  box-shadow: none;
  outline: none;
}

.form-check-input:focus-visible {
  box-shadow: none;
  outline: none;
}

/* Nút edit */
.btn-edit {
  transition: all 0.3s ease-in-out;
  gap: 6px;
  font-size: 14px;
  padding: 5px 12px;
  white-space: nowrap;
  border-radius: 4px;
}

.btn-edit:hover {
  background-color: #ffe0cc !important;
  border-color: #ff6600 !important;
  color: #e55a00 !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(255, 102, 0, 0.2);
}

.btn-edit .btn-text {
  margin: 0;
}
</style>