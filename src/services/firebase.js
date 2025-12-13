import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue } from "firebase/database";

// **************************************************
// CẤU HÌNH FIREBASE CỦA BẠN ĐÃ LẤY TỪ CONSOLE
// **************************************************
const firebaseConfig = {
    apiKey: "AIzaSyAFi7xrclnQufDYp5rRbikXdNN_aYfFPUI",
    authDomain: "vnuis-library-seat-management.firebaseapp.com",
    databaseURL: "https://vnuis-library-seat-management-default-rtdb.asia-southeast1.firebasedatabase.app/",
    projectId: "vnuis-library-seat-management",
    storageBucket: "vnuis-library-seat-management.firebasestorage.app",
    messagingSenderId: "983950508513",
    appId: "1:983950508513:web:b66d5859594e21160712a0",
    measurementId: "G-3NHQJXYMEC"
};

// Khởi tạo Firebase App
const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

console.log("🔥 Firebase đã được khởi tạo thành công!");
console.log("📡 Database URL:", firebaseConfig.databaseURL);

// Hàm lắng nghe trạng thái chỗ ngồi theo thời gian thực
export const subscribeToSeatStatus = (callback) => {
    console.log("📌 Bắt đầu subscribe đến Firebase...");

    // Đường dẫn đến node trạng thái mà ESP32 đã gửi (SEAT C01)
    const seatRef = ref(db, 'connection_status/library_seats/SEAT_C01/status');
    console.log("📍 Đang lắng nghe path: connection_status/library_seats/SEAT_C01/status");

    // onValue sẽ kích hoạt ngay lập tức và mỗi khi dữ liệu thay đổi
    const unsubscribe = onValue(seatRef, (snapshot) => {
        console.log("📦 Nhận được dữ liệu từ Firebase!");
        console.log("   - snapshot.exists():", snapshot.exists());
        console.log("   - snapshot.val():", snapshot.val());
        console.log("   - snapshot.key:", snapshot.key);

        const status = snapshot.val(); // Lấy giá trị (0 hoặc 1)
        console.log("✅ Trạng thái ghế:", status, "(0=Trống, 1=Có người)");

        callback(status); // Gọi hàm callback để cập nhật trạng thái trong React
    }, (error) => {
        console.error("❌ LỖI Firebase:", error.message);
        console.error("   - Error code:", error.code);
    });

    return unsubscribe; // Trả về hàm hủy đăng ký (cleanup)
};