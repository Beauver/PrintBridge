# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
LABEL org.opencontainers.image.title="PrintBridge frontend" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/Beauver/PrintBridge"
# Where nginx forwards /api to; override at runtime, e.g. -e BACKEND_URL=http://printbridge-backend:8080
ENV BACKEND_URL=http://backend:8080
# Only substitute our own variable, so nginx's $host, $uri etc. stay intact.
ENV NGINX_ENVSUBST_FILTER=BACKEND_URL
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/PrintBridge-Frontend/browser /usr/share/nginx/html
# Licences of the bundled libraries, linked from the footer (#16).
COPY --from=build /app/dist/PrintBridge-Frontend/3rdpartylicenses.txt /usr/share/nginx/html/3rdpartylicenses.txt
EXPOSE 80
