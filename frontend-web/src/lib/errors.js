// Vietnamese messages for the backend's stable error codes.
const MESSAGES = {
  INVALID_CREDENTIALS: 'Sai tài khoản hoặc mật khẩu.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  ACCESS_DENIED: 'Bạn không có quyền thực hiện thao tác này.',
  SEAT_NOT_AVAILABLE: 'Ghế này không còn trống.',
  SEAT_BUSY: 'Ghế đang được xử lý bởi yêu cầu khác, thử lại sau giây lát.',
  USER_HAS_ACTIVE_RESERVATION: 'Bạn đang có một lượt đặt chỗ chưa kết thúc.',
  USER_BANNED: 'Tài khoản đang bị tạm khoá đặt chỗ do điểm uy tín thấp.',
  INSUFFICIENT_CREDIT: 'Điểm uy tín chưa đủ để đặt chỗ (tối thiểu 60).',
  INVALID_QR_CODE: 'Mã QR không khớp với ghế đã đặt.',
  NO_PHYSICAL_PRESENCE: 'Cảm biến chưa phát hiện bạn ngồi vào ghế. Hãy ngồi xuống rồi thử lại.',
  RESERVATION_EXPIRED: 'Lượt đặt chỗ đã hết hạn.',
  INVALID_RESERVATION_STATE: 'Lượt đặt chỗ không còn ở trạng thái phù hợp.',
  RESERVATION_NOT_FOUND: 'Không tìm thấy lượt đặt chỗ.',
  SEAT_NOT_FOUND: 'Không tìm thấy ghế.',
  AI_SERVICE_UNAVAILABLE: 'Dịch vụ AI đang không hoạt động.',
  MODEL_NOT_TRAINED: 'Mô hình dự báo chưa được huấn luyện.',
  NOT_ENOUGH_DATA: 'Chưa đủ dữ liệu lịch sử để huấn luyện.',
  VALIDATION_FAILED: 'Dữ liệu gửi lên không hợp lệ.',
  USER_NOT_FOUND: 'Không tìm thấy người dùng.',
  FACE_VERIFICATION_REQUIRED: 'Cần xác thực khuôn mặt trước khi đặt ghế.',
  FACE_NOT_ENROLLED: 'Bạn chưa đăng ký khuôn mặt.',
  FACE_ALREADY_ENROLLED: 'Tài khoản đã đăng ký khuôn mặt. Liên hệ thủ thư nếu cần đăng ký lại.',
  FACE_NOT_MATCHED: 'Khuôn mặt không khớp với sinh viên đã đăng ký.',
  FACE_LIVENESS_FAILED: 'Chưa nhận ra động tác quay đầu. Hãy làm theo hướng dẫn chậm và rõ hơn.',
  NO_FACE: 'Không thấy khuôn mặt. Hãy nhìn thẳng vào camera, đủ ánh sáng.',
  MULTIPLE_FACES: 'Có nhiều hơn một khuôn mặt trước camera.',
  FACE_CHALLENGE_EXPIRED: 'Phiên xác thực đã hết hạn, hãy thử lại.',
  FACE_VERIFY_LOCKED: 'Xác thực sai quá nhiều lần. Vui lòng thử lại sau 10 phút.',
  FACE_SERVICE_UNAVAILABLE: 'Dịch vụ nhận dạng khuôn mặt đang không hoạt động.',
  CONSENT_REQUIRED: 'Bạn cần đồng ý cho phép xử lý dữ liệu khuôn mặt.',
  INVALID_IMAGE: 'Ảnh từ camera không hợp lệ, hãy thử lại.',
  NETWORK_ERROR: 'Không kết nối được tới máy chủ.',
  SIMULATOR_UNREACHABLE: 'Không kết nối được iot-simulator (cổng 8090).',
}

export function errorMessage(error) {
  if (!error) return ''
  if (error.code && MESSAGES[error.code]) return MESSAGES[error.code]
  return error.detail || error.message || 'Đã có lỗi xảy ra.'
}
