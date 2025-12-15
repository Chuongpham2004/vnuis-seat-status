import React, { useState, useEffect } from 'react';
import { subscribeToAllSeats, SEAT_IDS, updateSeatStatus } from '../services/firebase';
import SeatCard from './SeatCard';

const LibraryLayout = () => {
    const [seatStatuses, setSeatStatuses] = useState({});

    useEffect(() => {
        console.log("🚀 LibraryLayout component mounted - bắt đầu lắng nghe tất cả ghế");

        const unsubscribe = subscribeToAllSeats((statuses) => {
            console.log("📥 Nhận được trạng thái mới:", statuses);
            setSeatStatuses(statuses);
        });

        return () => {
            console.log("🛑 LibraryLayout unmounted - ngừng lắng nghe");
            unsubscribe();
        };
    }, []);

    // Handler for toggling seat status
    const handleToggleSeat = async (seatId, newStatus) => {
        console.log(`🔄 Toggle ${seatId} to ${newStatus}`);
        await updateSeatStatus(seatId, newStatus);
    };

    // Calculate statistics
    const totalSeats = SEAT_IDS.length;
    const availableSeats = SEAT_IDS.filter(id => seatStatuses[id] === 0).length;
    const occupiedSeats = SEAT_IDS.filter(id => seatStatuses[id] === 1).length;
    const connectingSeats = SEAT_IDS.filter(id => seatStatuses[id] === null).length;

    return (
        <div className="w-full max-w-4xl mx-auto">
            {/* Stats Bar */}
            <div className="glass rounded-2xl p-4 mb-16 flex flex-wrap justify-center gap-4 md:gap-8">
                <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full bg-emerald-500"></span>
                    <span className="text-white/70 text-sm">
                        Còn trống: <span className="font-bold text-emerald-400">{availableSeats}</span>
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full bg-red-500"></span>
                    <span className="text-white/70 text-sm">
                        Đã có người: <span className="font-bold text-red-400">{occupiedSeats}</span>
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full bg-gray-500"></span>
                    <span className="text-white/70 text-sm">
                        Đang kết nối: <span className="font-bold text-gray-400">{connectingSeats}</span>
                    </span>
                </div>
                <div className="flex items-center gap-2 border-l border-white/20 pl-4">
                    <span className="text-white/70 text-sm">
                        Tổng: <span className="font-bold text-white">{totalSeats}</span> ghế
                    </span>
                </div>
            </div>

            {/* Seats Grid */}
            <div className="flex flex-col items-center gap-4 md:gap-6">
                {/* First row - 3 seats */}
                <div className="flex justify-center gap-4 md:gap-6 flex-wrap">
                    {SEAT_IDS.slice(0, 3).map((seatId) => (
                        <div
                            key={seatId}
                            className="animate-scale-in w-[140px] md:w-[180px]"
                            style={{ animationDelay: `${SEAT_IDS.indexOf(seatId) * 0.1}s` }}
                        >
                            <SeatCard
                                seatId={seatId}
                                status={seatStatuses[seatId]}
                                isRealSensor={seatId === 'SEAT_C01'}
                                onToggle={handleToggleSeat}
                            />
                        </div>
                    ))}
                </div>

                {/* Second row - 2 seats centered */}
                <div className="flex justify-center gap-4 md:gap-6">
                    {SEAT_IDS.slice(3, 5).map((seatId) => (
                        <div
                            key={seatId}
                            className="animate-scale-in w-[140px] md:w-[180px]"
                            style={{ animationDelay: `${SEAT_IDS.indexOf(seatId) * 0.1}s` }}
                        >
                            <SeatCard
                                seatId={seatId}
                                status={seatStatuses[seatId]}
                                isRealSensor={false}
                                onToggle={handleToggleSeat}
                            />
                        </div>
                    ))}
                </div>
            </div>

            {/* Legend */}
            <div className="glass rounded-2xl p-4 mt-10 text-center">
                <div className="flex flex-wrap justify-center gap-4 md:gap-6 text-xs text-white/60">
                    <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/30 text-[10px] font-bold">
                            🔧 SENSOR
                        </span>
                        <span>= Cảm biến thực (ESP32)</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-400 border border-purple-500/30 text-[10px] font-bold">
                            💻 SIMULATED
                        </span>
                        <span>= Node giả lập Firebase</span>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default LibraryLayout;
