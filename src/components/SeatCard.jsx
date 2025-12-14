import React from 'react';

const SeatCard = ({ seatId, status, isRealSensor = false }) => {
    const displayName = seatId.replace('SEAT_', '');
    const isLoading = status === null;
    const isOccupied = status === 1;

    // Determine status colors and text
    const getStatusInfo = () => {
        if (isLoading) {
            return {
                bgColor: 'from-gray-500 to-gray-600',
                statusText: 'ĐANG KẾT NỐI',
                statusColor: 'text-gray-400',
                borderColor: 'border-gray-500/30',
                shadowColor: 'shadow-[0_0_30px_-10px_rgba(107,114,128,0.3)]',
                pulseClass: '',
                icon: (
                    <div className="flex gap-1">
                        <div className="w-2 h-2 bg-white/80 rounded-full loading-dot"></div>
                        <div className="w-2 h-2 bg-white/80 rounded-full loading-dot"></div>
                        <div className="w-2 h-2 bg-white/80 rounded-full loading-dot"></div>
                    </div>
                ),
            };
        }
        if (isOccupied) {
            return {
                bgColor: 'from-red-500 to-red-600',
                statusText: 'ĐÃ CÓ NGƯỜI',
                statusColor: 'text-red-400',
                borderColor: 'border-red-500/30',
                shadowColor: 'shadow-[0_0_30px_-10px_rgba(239,68,68,0.4)]',
                pulseClass: 'pulse-occupied',
                icon: (
                    <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                ),
            };
        }
        return {
            bgColor: 'from-emerald-400 to-emerald-600',
            statusText: 'CÒN TRỐNG',
            statusColor: 'text-emerald-400',
            borderColor: 'border-emerald-500/30',
            shadowColor: 'shadow-[0_0_30px_-10px_rgba(16,185,129,0.4)]',
            pulseClass: 'pulse-available',
            icon: (
                <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                </svg>
            ),
        };
    };

    const statusInfo = getStatusInfo();

    return (
        <div
            className={`
        glass-card rounded-2xl p-5 text-center
        transform transition-all duration-500 hover:scale-[1.03] hover:-translate-y-1
        ${statusInfo.borderColor} ${statusInfo.shadowColor}
      `}
        >
            {/* Sensor Badge */}
            <div className="flex justify-between items-center mb-3">
                <span
                    className={`
            px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase
            ${isRealSensor
                            ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                            : 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
                        }
          `}
                >
                    {isRealSensor ? '🔧 SENSOR' : '💻 SIMULATED'}
                </span>
                <span className={`w-2 h-2 rounded-full animate-pulse ${isLoading ? 'bg-gray-500' : isOccupied ? 'bg-red-500' : 'bg-emerald-500'
                    }`} />
            </div>

            {/* Seat Name */}
            <h3 className="text-xl font-bold text-white mb-3 tracking-tight">
                {displayName}
            </h3>

            {/* Status Icon */}
            <div className="flex justify-center mb-3">
                <div
                    className={`
            relative w-16 h-16 rounded-full flex items-center justify-center
            bg-gradient-to-br ${statusInfo.bgColor} ${statusInfo.pulseClass}
            transition-all duration-500
          `}
                >
                    {statusInfo.icon}
                </div>
            </div>

            {/* Status Text */}
            <p className={`text-sm font-bold ${statusInfo.statusColor} transition-all duration-300`}>
                {statusInfo.statusText}
            </p>
        </div>
    );
};

export default SeatCard;
