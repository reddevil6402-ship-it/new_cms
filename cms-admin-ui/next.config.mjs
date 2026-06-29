/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "http",
        hostname: "localhost",
        port: "8080",
        pathname: "/api/v1/media/**",
      },
      {
        protocol: "http",
        hostname: "localhost",
        port: "8085",
        pathname: "/api/v1/media/**",
      },
    ],
  },
};

export default nextConfig;
