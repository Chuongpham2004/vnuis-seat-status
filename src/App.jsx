import React from 'react';
import LibraryLayout from './components/LibraryLayout';

const App = () => {
  return (
    <div className="relative min-h-screen flex flex-col items-center px-4 py-6">
      {/* Animated Background Orbs */}
      <div className="bg-orbs">
        <div className="orb orb-1"></div>
        <div className="orb orb-2"></div>
        <div className="orb orb-3"></div>
      </div>

      {/* Header */}
      <header className="relative z-10 text-center py-6 animate-fade-in-up">
        {/* Logo/Badge */}
        <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass mb-4">
          <span className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></span>
          <span className="text-xs font-semibold tracking-widest text-white/70 uppercase">
            Live Status
          </span>
        </div>

        {/* Title */}
        <h1 className="text-4xl md:text-5xl lg:text-6xl font-extrabold tracking-tight mb-2">
          <span className="text-gradient text-glow">VNU-IS</span>
          <span className="text-white"> LIBRARY</span>
        </h1>

        <h2 className="text-lg md:text-xl font-medium text-white/60 tracking-widest">
          SEAT MANAGEMENT SYSTEM
        </h2>
      </header>

      {/* Main Content - grows to fill space */}
      <main className="relative z-10 flex-grow flex items-center justify-center py-8">
        <div className="animate-scale-in w-full" style={{ animationDelay: '0.2s' }}>
          <LibraryLayout />
        </div>
      </main>

      {/* Footer - stays at bottom */}
      <footer className="relative z-10 py-6 animate-fade-in-up" style={{ animationDelay: '0.4s' }}>
        <div className="flex flex-col items-center gap-4">
          {/* Info Box */}
          <div className="glass rounded-2xl px-6 py-4 max-w-md text-center">
            <div className="flex items-center justify-center gap-3 text-white/70">
              <svg className="w-5 h-5 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clipRule="evenodd" />
              </svg>
              <span className="text-sm font-medium">
                Dữ liệu realtime từ cảm biến ESP32 & Firebase
              </span>
            </div>
          </div>

          {/* Credits */}
          <p className="text-xs text-white/30 tracking-wider">
            © 2025 VNU-IS Library • Powered by IoT
          </p>
        </div>
      </footer>
    </div>
  );
};

export default App;