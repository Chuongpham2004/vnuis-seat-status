import React from 'react';
import SeatStatus from './components/SeatStatus';

const App = () => {
  return (
    <div className="relative min-h-screen flex flex-col items-center justify-center px-4 py-8">
      {/* Animated Background Orbs */}
      <div className="bg-orbs">
        <div className="orb orb-1"></div>
        <div className="orb orb-2"></div>
        <div className="orb orb-3"></div>
      </div>

      {/* Main Content */}
      <div className="relative z-10 flex flex-col items-center">
        {/* Header */}
        <div className="text-center mb-12 animate-fade-in-up">
          {/* Logo/Badge */}
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass mb-6">
            <span className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></span>
            <span className="text-xs font-semibold tracking-widest text-white/70 uppercase">
              Live Status
            </span>
          </div>

          {/* Title */}
          <h1 className="text-5xl md:text-6xl lg:text-7xl font-extrabold tracking-tight mb-3">
            <span className="text-gradient text-glow">VNU-IS</span>
            <span className="text-white"> LIBRARY</span>
          </h1>

          <h2 className="text-xl md:text-2xl font-medium text-white/60 tracking-widest">
            SEAT MANAGEMENT SYSTEM
          </h2>
        </div>

        {/* Seat Status Card */}
        <div className="animate-scale-in" style={{ animationDelay: '0.2s' }}>
          <SeatStatus />
        </div>

        {/* Footer Info */}
        <div
          className="mt-10 glass rounded-2xl px-6 py-4 max-w-md text-center animate-fade-in-up"
          style={{ animationDelay: '0.4s' }}
        >
          <div className="flex items-center justify-center gap-3 text-white/70">
            <svg className="w-5 h-5 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clipRule="evenodd" />
            </svg>
            <span className="text-sm font-medium">
              Dữ liệu realtime từ cảm biến ESP32
            </span>
          </div>
        </div>

        {/* Credits */}
        <p className="mt-6 text-xs text-white/30 tracking-wider">
          © 2025 VNU-IS Library • Powered by IoT
        </p>
      </div>
    </div>
  );
};

export default App;