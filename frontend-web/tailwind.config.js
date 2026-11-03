/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                occupied: {
                    DEFAULT: '#DC3545',
                    light: '#FF6B7A',
                    dark: '#C82333',
                },
                available: {
                    DEFAULT: '#28A745',
                    light: '#5FCF80',
                    dark: '#1E7E34',
                },
            },
            animation: {
                'fade-in': 'fadeIn 0.5s ease-in',
                'scale-in': 'scaleIn 0.3s ease-out',
                'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
            },
            keyframes: {
                fadeIn: {
                    '0%': { opacity: '0' },
                    '100%': { opacity: '1' },
                },
                scaleIn: {
                    '0%': { transform: 'scale(0.9)', opacity: '0' },
                    '100%': { transform: 'scale(1)', opacity: '1' },
                },
            },
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
            },
        },
    },
    plugins: [],
}
