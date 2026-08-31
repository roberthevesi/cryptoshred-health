# ── Stage 1: Build Production Static Assets with Vite ─────────────────
FROM node:20-alpine AS builder
WORKDIR /app

# Copy package manifests & install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy source tree and compile production bundle
COPY . .
RUN npm run build

# ── Stage 2: Serve Bundle via Minimal Nginx Web Server ────────────────
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Remove default Nginx welcome page
RUN rm -rf ./*

# Copy compiled static assets from builder stage
COPY --from=builder /app/dist .

# Copy custom Nginx SPA & reverse proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -q -O - http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
