/** @type {import('next').NextConfig} */
const nextConfig = {
    async rewrites() {
        return [
            {
                source: '/api/chat-stream',
                destination: 'http://localhost:8080/run_sse',
            },
            {
                source: '/api/chat/stream',
                destination: 'http://localhost:8080/api/chat/stream',
            },
        ];
    },
};

export default nextConfig;
