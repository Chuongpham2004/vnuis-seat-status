import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue, set } from "firebase/database";

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

// Danh sách các ghế trong thư viện
const SEAT_IDS = ['SEAT_C01', 'SEAT_C02', 'SEAT_C03', 'SEAT_C04', 'SEAT_C05'];

// Hàm lắng nghe trạng thái tất cả ghế theo thời gian thực
export const subscribeToAllSeats = (callback) => {
    console.log("📌 Bắt đầu subscribe đến tất cả ghế trong Firebase...");

    const unsubscribes = [];
    const seatStatuses = {};

    // Khởi tạo trạng thái null cho tất cả ghế
    SEAT_IDS.forEach(seatId => {
        seatStatuses[seatId] = null;
    });

    // Subscribe đến từng ghế
    SEAT_IDS.forEach(seatId => {
        const seatRef = ref(db, `/library_seats/${seatId}/status`);
        console.log(`📍 Đang lắng nghe path: /library_seats/${seatId}/status`);

        const unsubscribe = onValue(seatRef, (snapshot) => {
            console.log(`📦 Nhận được dữ liệu từ ${seatId}:`, snapshot.val());
            seatStatuses[seatId] = snapshot.val();
            // Gọi callback với bản sao của object để React nhận ra thay đổi
            callback({ ...seatStatuses });
        }, (error) => {
            console.error(`❌ LỖI Firebase cho ${seatId}:`, error.message);
        });

        unsubscribes.push(unsubscribe);
    });

    // Trả về hàm cleanup để hủy tất cả subscriptions
    return () => {
        console.log("🛑 Ngừng lắng nghe tất cả ghế");
        unsubscribes.forEach(unsubscribe => unsubscribe());
    };
};

// Hàm lắng nghe trạng thái chỗ ngồi theo thời gian thực (giữ cho tương thích ngược)
export const subscribeToSeatStatus = (callback) => {
    console.log("📌 Bắt đầu subscribe đến Firebase...");

    // Đường dẫn đến node trạng thái mà ESP32 đã gửi (SEAT C01)
    const seatRef = ref(db, '/library_seats/SEAT_C01/status');
    console.log("📍 Đang lắng nghe path: /library_seats/SEAT_C01/status");

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

// Hàm cập nhật trạng thái ghế lên Firebase
export const updateSeatStatus = async (seatId, newStatus) => {
    console.log(`📝 Đang cập nhật ${seatId} thành ${newStatus}...`);
    try {
        const seatRef = ref(db, `/library_seats/${seatId}/status`);
        await set(seatRef, newStatus);
        console.log(`✅ Đã cập nhật ${seatId} thành ${newStatus} thành công!`);
        return true;
    } catch (error) {
        console.error(`❌ Lỗi khi cập nhật ${seatId}:`, error.message);
        return false;
    }
};

// Export danh sách ghế để sử dụng trong components
export { SEAT_IDS };
