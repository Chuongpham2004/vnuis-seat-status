import React, { useState, useEffect } from 'react';
import { subscribeToSeatStatus } from '../services/firebase';

const SeatStatus = () => {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    console.log("🚀 SeatStatus component mounted - bắt đầu lắng nghe Firebase");

    const unsubscribe = subscribeToSeatStatus((newStatus) => {
      console.log("📥 Component nhận được status mới:", newStatus);
      setStatus(newStatus);
    });

    return () => {
      console.log("🛑 Component unmounted - ngừng lắng nghe");
      unsubscribe();
    };
  }, []);

  const isLoading = status === null;
  const isOccupied = status === 1;

  // Loading State
  if (isLoading) {
    return (
      <div className="glass-card rounded-3xl p-8 md:p-10 w-[340px] md:w-[400px] text-center shimmer">
        <div className="mb-6">
          <span className="inline-block px-4 py-1.5 rounded-full text-xs font-semibold tracking-widest text-white/70 bg-white/10 border border-white/10">
            CONNECTING
          </span>
        </div>

        <h2 className="text-3xl md:text-4xl font-bold text-white mb-8 tracking-tight">
          SEAT C01
        </h2>

        <div className="flex items-center justify-center gap-2 mb-6">
          <div className="w-3 h-3 bg-white/80 rounded-full loading-dot"></div>
          <div className="w-3 h-3 bg-white/80 rounded-full loading-dot"></div>
          <div className="w-3 h-3 bg-white/80 rounded-full loading-dot"></div>
        </div>

        <p className="text-white/60 text-sm font-medium">
          Đang kết nối với cảm biến...
        </p>
      </div>
    );
  }

  return (
    <div
      className={`
        glass-card rounded-3xl p-8 md:p-10 w-[340px] md:w-[400px] text-center
        transform transition-all duration-500 hover:scale-[1.02] hover:-translate-y-1
        ${isOccupied
          ? 'border-red-500/30 shadow-[0_0_60px_-15px_rgba(239,68,68,0.4)]'
          : 'border-emerald-500/30 shadow-[0_0_60px_-15px_rgba(16,185,129,0.4)]'
        }
      `}
    >
      {/* Status Badge */}
      <div className="mb-6">
        <span
          className={`
            inline-block px-4 py-1.5 rounded-full text-xs font-semibold tracking-widest
            transition-all duration-300
            ${isOccupied
              ? 'bg-red-500/20 text-red-400 border border-red-500/30'
              : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
            }
          `}
        >
          {isOccupied ? 'OCCUPIED' : 'AVAILABLE'}
        </span>
      </div>

      {/* Seat Name */}
      <h2 className="text-3xl md:text-4xl font-bold text-white mb-8 tracking-tight">
        SEAT C01
      </h2>

      {/* Status Icon */}
      <div className="relative mb-8 flex items-center justify-center">
        <div
          className={`
            relative w-28 h-28 md:w-32 md:h-32 rounded-full flex items-center justify-center
            transition-all duration-500
            ${isOccupied
              ? 'bg-gradient-to-br from-red-500 to-red-600 pulse-occupied'
              : 'bg-gradient-to-br from-emerald-400 to-emerald-600 pulse-available'
            }
          `}
        >
          {/* Inner Icon */}
          <div className="relative z-10">
            {isOccupied ? (
              <svg className="w-14 h-14 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
              </svg>
            ) : (
              <svg className="w-14 h-14 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
              </svg>
            )}
          </div>

          {/* Animated Ring - positioned relative to the circle itself */}
          <div
            className={`
              absolute inset-0 rounded-full
              ${isOccupied ? 'animate-ping bg-red-500/20' : 'animate-ping bg-emerald-500/20'}
            `}
            style={{ animationDuration: '2s' }}
          />
        </div>
      </div>

      {/* Status Text */}
      <p className={`
        text-2xl md:text-3xl font-bold mb-3 transition-all duration-300
        ${isOccupied ? 'text-red-400' : 'text-emerald-400'}
      `}>
        {isOccupied ? 'ĐÃ CÓ NGƯỜI' : 'CÒN TRỐNG'}
      </p>

      {/* Description */}
      <p className="text-white/50 text-sm">
        {isOccupied
          ? 'Ghế đang được sử dụng'
          : 'Ghế sẵn sàng để sử dụng'
        }
      </p>

      {/* Real-time Indicator */}
      <div className="mt-6 pt-6 border-t border-white/10">
        <div className="flex items-center justify-center gap-2 text-xs text-white/40">
          <span className={`
            w-2 h-2 rounded-full
            ${isOccupied ? 'bg-red-500' : 'bg-emerald-500'}
            animate-pulse
          `} />
          <span>Cập nhật thời gian thực</span>
        </div>
      </div>
    </div>
  );
};

export default SeatStatus;
