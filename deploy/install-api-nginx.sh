#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/masterdoc-backend}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-admin@masterdoc.pro}"
SITE="/etc/nginx/sites-available/api.masterdoc.pro"

mkdir -p /var/www/certbot

if [[ -f /etc/letsencrypt/live/api.masterdoc.pro/fullchain.pem ]]; then
  cp "${DEPLOY_PATH}/api.masterdoc.pro.nginx.conf" "${SITE}"
else
  cp "${DEPLOY_PATH}/api.masterdoc.pro.nginx.http.conf" "${SITE}"
fi

ln -sf "${SITE}" /etc/nginx/sites-enabled/api.masterdoc.pro
nginx -t
systemctl reload nginx

if [[ ! -f /etc/letsencrypt/live/api.masterdoc.pro/fullchain.pem ]]; then
  certbot certonly --webroot -w /var/www/certbot \
    -d api.masterdoc.pro \
    --non-interactive --agree-tos --email "${CERTBOT_EMAIL}"
  cp "${DEPLOY_PATH}/api.masterdoc.pro.nginx.conf" "${SITE}"
  nginx -t
  systemctl reload nginx
fi
